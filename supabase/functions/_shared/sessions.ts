import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamableHttp.js";
import { HungiiError, type Json } from "./errors.ts";
import { digest } from "./secrets.ts";

export interface Connection {
  tools: Json[];
  call(name: string, args: Json): Promise<Json>;
  close(): Promise<void>;
  sessionId?: string;
}
export type Factory = (url: string, token: string, guardedFetch: typeof fetch) => Promise<Connection>;
export const sdkFactory: Factory = async (url, token, guardedFetch) => {
  const client = new Client({ name: "hungii", version: "0.4.0" });
  const transport = new StreamableHTTPClientTransport(new URL(url), {
    requestInit: { headers: { Authorization: `Bearer ${token}` } }, fetch: guardedFetch,
    reconnectionOptions: { maxRetries: 0, initialReconnectionDelay: 1000, maxReconnectionDelay: 1000, reconnectionDelayGrowFactor: 1 },
  });
  try {
    await client.connect(transport, { timeout: 8000 });
    const tools: Json[] = []; let cursor: string | undefined;
    do {
      const page = await client.listTools(cursor ? { cursor } : {}, { timeout: 8000 });
      tools.push(...page.tools); cursor = page.nextCursor;
    } while (cursor && tools.length < 100);
    if (cursor) throw new HungiiError("HUNGII_SCHEMA_CHANGED", "The provider's catalogue exceeds this connection's limit.", 409);
    return { tools, get sessionId() { return transport.sessionId; },
      call: (name, args) => client.callTool({ name, arguments: args }, undefined, { timeout: 8000, maxTotalTimeout: 8000 }),
      close: async () => { await transport.terminateSession().catch(() => {}); await client.close().catch(() => {}); },
    };
  } catch (error) { await client.close().catch(() => {}); throw error; }
};

type Entry = { fingerprint: string; pending: Promise<Connection>; tail: Promise<void>; queued: number; touched: number; budget: number[]; cooldown: number; denial?: HungiiError; retryDelay: number };
export function retrySeconds(value: string | null, now: number): number {
  if (value === null) return 30;
  const n = Number(value); const seconds = Number.isFinite(n) && value.trim() !== "" ? n : (Date.parse(value) - now) / 1000;
  return Number.isFinite(seconds) ? Math.min(86400, Math.max(1, Math.ceil(seconds))) : 30;
}

// One process owns each user's connection. No Edge Function/request-scoped cache.
export class FoodSessions {
  private entries = new Map<string, Entry>();
  constructor(private factory: Factory = sdkFactory, private network: typeof fetch = fetch, private now: () => number = Date.now) {}
  async drop(user: string) {
    const key = await digest(user); const entry = this.entries.get(key); this.entries.delete(key);
    if (entry) { await entry.tail; await entry.pending.then(c => c.close(), () => {}); }
  }
  async sweep() {
    for (const [key, entry] of this.entries) {
      if (!entry.queued && this.now() - entry.touched > 30 * 60_000) {
        this.entries.delete(key); await entry.pending.then(c => c.close(), () => {});
      }
    }
  }
  async close() { for (const key of this.entries.keys()) { const e = this.entries.get(key)!; await e.tail; await e.pending.then(c => c.close(), () => {}); } this.entries.clear(); }
  async run<T>(user: string, token: string, url: string, task: (connection: Connection) => Promise<T>): Promise<T> {
    const key = await digest(user), fingerprint = await digest(`${url}\n${token}`);
    let entry = this.entries.get(key);
    if (entry && entry.fingerprint !== fingerprint) { await this.drop(user); entry = undefined; }
    if (!entry) {
      if (this.entries.size >= 200) throw new HungiiError("HUNGII_BUSY", "The connection service is at capacity. Try later.", 503);
      const created: Entry = { fingerprint, pending: Promise.resolve(null as unknown as Connection), tail: Promise.resolve(), queued: 0, touched: this.now(), budget: [], cooldown: 0, retryDelay: 1000 };
      this.entries.set(key, created); entry = created;
      const guarded: typeof fetch = async (input, init) => {
        if (created.denial) throw created.denial;
        const now = this.now();
        if (created.cooldown > now) throw new HungiiError("HUNGII_BUSY", "The provider asked us to wait. Please try later.", 429, Math.ceil((created.cooldown - now)/1000));
        created.budget = created.budget.filter(t => now - t < 60_000);
        if (created.budget.length >= 55 || created.budget.filter(t => now-t<10_000).length >= 12) {
          throw new HungiiError("HUNGII_BUSY", "Please pause before the next search.", 429, 10);
        }
        created.budget.push(now);
        const response = await this.network(input, init);
        if (response.status === 429) {
          const seconds = retrySeconds(response.headers.get("Retry-After"), now); created.cooldown = now + seconds*1000;
          throw new HungiiError("HUNGII_BUSY", "The provider asked us to wait. Please try later.", 429, seconds);
        }
        if ([401, 419, 403].includes(response.status)) {
          created.denial = response.status === 403 ? new HungiiError("HUNGII_PROVIDER_BLOCKED", "This connection was rejected. Contact support before retrying.", 403) : new HungiiError("HUNGII_RECONNECT", "Please reconnect your Swiggy account.", 401);
          throw created.denial;
        }
        return response;
      };
      created.pending = this.factory(url, token, guarded).catch(error => {
        // Failed handshakes are held, rather than restarted by every button press.
        created.denial = error instanceof HungiiError ? error : new HungiiError("HUNGII_CONNECTION_FAILED", "Connection failed. Reconnect once after checking the service.", 503);
        throw created.denial;
      });
      void created.pending.catch(() => {});
    }
    if (entry.queued >= 4) throw new HungiiError("HUNGII_BUSY", "A search is already queued. Please wait.", 429, 5);
    const current = entry; current.queued++;
    let release!: () => void; const prior = current.tail; current.tail = new Promise<void>(resolve => { release = resolve; });
    await prior;
    try {
      if (current.denial) throw current.denial;
      if (current.cooldown > this.now()) throw new HungiiError("HUNGII_BUSY", "Please wait before retrying.", 429, Math.ceil((current.cooldown-this.now())/1000));
      const connection = await current.pending;
      current.touched = this.now();
      return await task({ ...connection, sessionId: connection.sessionId, call: async (name, args) => {
        const began = this.now(); let status = "ok";
        try {
          for (let attempt=0;;attempt++) {
            try {
              const result = await connection.call(name, args);
              if (result._meta?.swiggy?.deprecation) console.warn(JSON.stringify({ event: "swiggy_deprecation", tool: name }));
              current.retryDelay = 1000; return result;
            } catch (error) {
              const code = (error as {code?:number}).code;
              if (error instanceof HungiiError) throw error;
              if ([401,419,-32001].includes(code ?? 0)) { current.denial = new HungiiError("HUNGII_RECONNECT", "Please reconnect your Swiggy account.", 401); throw current.denial; }
              if (code === 429) { current.cooldown = this.now()+30_000; throw new HungiiError("HUNGII_BUSY", "Please wait before retrying.", 429, 30); }
              if (code === 403) { current.denial = new HungiiError("HUNGII_PROVIDER_BLOCKED", "This connection was rejected. Contact support.", 403); throw current.denial; }
              if (!(code && code>=500 && code<600) || attempt>=2 || this.now()-began>20_000) {
                current.cooldown=this.now()+current.retryDelay; current.retryDelay=Math.min(30_000,current.retryDelay*2);
                throw new HungiiError("HUNGII_UPSTREAM_UNAVAILABLE", "The provider is unavailable. Please wait before retrying.", 502);
              }
              await new Promise(r => setTimeout(r, 500*2**attempt + Math.random()*100));
            }
          }
        } catch(error) { status="failed"; throw error; }
        finally { console.info(JSON.stringify({ event: "mcp_tool_call", user_id_hash: key, session_id: connection.sessionId ?? null, tool: name, duration_ms: this.now()-began, status })); }
      } });
    } finally { current.queued--; current.touched=this.now(); release(); }
  }
}
