import type { SupabaseClient } from "@supabase/supabase-js";
import { HungiiError, type Json } from "./errors.ts";
import { seal, unseal, digest } from "./secrets.ts";
import { retrySeconds } from "./sessions.ts";
import type { Connection } from "./sessions.ts";

type Metadata = { sessionId: string; protocol: string; tools: Json[] };
const VERSIONS = new Set(["2025-11-25", "2025-06-18", "2025-03-26"]);

// JSON and SSE are both permitted for a Streamable HTTP POST response.
// Stop after the matching response; never retain unrelated provider messages.
export async function rpcResult(response: Response, id: string): Promise<Json> {
  const reader = response.body?.getReader();
  if (!reader) throw new HungiiError("HUNGII_PROTOCOL", "The provider returned an empty response.", 502);
  const decoder = new TextDecoder(); let buffer = "", total = 0;
  const sse = response.headers.get("Content-Type")?.toLowerCase().includes("text/event-stream");
  if (!sse && !response.headers.get("Content-Type")?.toLowerCase().includes("application/json")) {
    await reader.cancel(); throw new HungiiError("HUNGII_PROTOCOL", "Unsupported provider response format.", 502);
  }
  const matching = (value: Json) => {
    if (value.jsonrpc !== "2.0" || value.id !== id) return null;
    if (value.error) {
      if (value.error.code === -32001) throw new HungiiError("HUNGII_RECONNECT", "Reconnect your Swiggy account.", 401);
      throw new HungiiError("HUNGII_UPSTREAM_RESPONSE", "The provider could not complete this request.", 502);
    }
    if (!value.result || typeof value.result !== "object") throw new HungiiError("HUNGII_PROTOCOL", "Invalid provider response.", 502);
    return value.result;
  };
  try {
    for (;;) {
      const chunk = await reader.read();
      total += chunk.value?.byteLength ?? 0;
      if (total > 2_000_000) throw new HungiiError("HUNGII_PROTOCOL", "The provider response exceeded the safe size limit.", 502);
      buffer += decoder.decode(chunk.value, { stream: !chunk.done });
      if (sse) {
        // Normalize CRLF only after complete lines; CR can arrive in a separate chunk.
        let delimiter: RegExpExecArray | null;
        while ((delimiter = /\r?\n\r?\n/.exec(buffer))) {
          const block = buffer.slice(0, delimiter.index); buffer = buffer.slice(delimiter.index + delimiter[0].length);
          const data = block.split(/\r?\n/).filter(line => line.startsWith("data:")).map(line => line.slice(5).replace(/^ /, "")).join("\n");
          if (!data) continue;
          let value: Json;
          try { value = JSON.parse(data); } catch { throw new HungiiError("HUNGII_PROTOCOL", "Invalid provider event.", 502); }
          const result = matching(value); if (result) return result;
        }
      }
      if (chunk.done) break;
    }
    if (!sse) {
      let value: Json;
      try { value = JSON.parse(buffer); } catch { throw new HungiiError("HUNGII_PROTOCOL", "Invalid provider JSON.", 502); }
      const result = matching(value); if (result) return result;
    }
    throw new HungiiError("HUNGII_PROTOCOL", "The provider did not return a matching response.", 502);
  } finally { await reader.cancel().catch(() => {}); reader.releaseLock(); }
}

// A logical MCP session survives Free Edge worker retirement. Database leases
// serialize access; their generation/nonce fence stale callbacks and completions.
// Cross-worker support by Swiggy remains an explicit staging approval gate.
export class DurableFoodSessions {
  constructor(private db: SupabaseClient, private secret: string, private generation: number, private network: typeof fetch = fetch) {}
  async run<T>(user: string, token: string, url: string, task: (connection: Connection) => Promise<T>): Promise<T> {
    const lease = crypto.randomUUID(), deadline = Date.now() + 28_000;
    const args = { owner_id: user, expected_generation: this.generation, lease_id: lease };
    const claim = await this.db.rpc("claim_swiggy_session", args);
    if (claim.error) throw new HungiiError("HUNGII_STORAGE", "Connection ownership could not be checked.", 503);
    const entry = claim.data?.[0];
    if (!entry) throw new HungiiError("HUNGII_BUSY", "A request is already running, or the connection changed. Please wait.", 429, 5);
    if (entry.phase === "blocked") throw new HungiiError(entry.blocked_code ?? "HUNGII_RECONNECT", "This connection needs attention. Reconnect after checking the service.", 409);
    if (entry.lease_owner !== lease) throw new HungiiError("HUNGII_BUSY", "Please wait for the previous request.", 429, 5);
    let metadata: Metadata | undefined, ciphertext: string | null = entry.encrypted_metadata;
    let failure: string | null = null, cooldown = 0;
    const post = async (method: string, params?: Json, notification = false): Promise<{result: Json; sessionId: string | null}> => {
      const budget = await this.db.rpc("reserve_swiggy_request", args);
      if (budget.error || !Number.isInteger(budget.data) || budget.data < 0) throw new HungiiError("HUNGII_CONNECTION_CHANGED", "The connection changed. Start again.", 409);
      if (budget.data > 0) throw new HungiiError("HUNGII_BUSY", "Please wait before the next request.", 429, budget.data);
      const remaining = deadline - Date.now();
      if (remaining <= 0) throw new HungiiError("HUNGII_TIMEOUT", "This search took too long. Try again later.", 504);
      const id = crypto.randomUUID();
      const response = await this.network(url, { method: "POST", redirect: "error", signal: AbortSignal.timeout(Math.min(8000, remaining)),
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json", Accept: "application/json, text/event-stream",
          ...(metadata ? { "Mcp-Session-Id": metadata.sessionId, "MCP-Protocol-Version": metadata.protocol } : {}) },
        body: JSON.stringify({ jsonrpc: "2.0", ...(notification ? {} : {id}), method, ...(params ? {params} : {}) }),
      });
      if (!response.ok) {
        await response.body?.cancel().catch(() => {});
        if (response.status === 429) throw new HungiiError("HUNGII_BUSY", "Swiggy asked us to wait. Please try later.", 429, retrySeconds(response.headers.get("Retry-After"), Date.now()));
        if ([401,419].includes(response.status)) throw new HungiiError("HUNGII_RECONNECT", "Reconnect your Swiggy account.", 401);
        if (response.status === 403) throw new HungiiError("HUNGII_PROVIDER_BLOCKED", "Swiggy rejected the connection. Contact support before retrying.", 403);
        if (response.status === 404 && metadata) throw new HungiiError("HUNGII_RECONNECT", "The provider session ended. Reconnect your account.", 401);
        throw new HungiiError("HUNGII_UPSTREAM_UNAVAILABLE", "Swiggy is unavailable. Please wait before retrying.", 502, 5);
      }
      if (notification) { await response.body?.cancel().catch(() => {}); return {result:{},sessionId:response.headers.get("Mcp-Session-Id")}; }
      return { result: await rpcResult(response, id), sessionId: response.headers.get("Mcp-Session-Id") };
    };
    try {
      if (ciphertext) metadata = JSON.parse(await unseal(ciphertext, `${user}:mcp:${this.generation}`, this.secret));
      if (!metadata) {
        const initialized = await post("initialize", { protocolVersion: "2025-11-25", capabilities: {}, clientInfo: {name:"hungii",version:"0.4.0"} });
        if (!initialized.sessionId || !VERSIONS.has(initialized.result.protocolVersion) || !initialized.result.capabilities?.tools) {
          throw new HungiiError("HUNGII_PROTOCOL", "A reusable Food session was not negotiated. Contact integration support.", 502);
        }
        metadata = {sessionId:initialized.sessionId, protocol:initialized.result.protocolVersion, tools:[]};
        await post("notifications/initialized", undefined, true);
        let cursor: string | undefined;
        do {
          const page = (await post("tools/list", cursor ? {cursor} : {})).result;
          if (!Array.isArray(page.tools)) throw new HungiiError("HUNGII_PROTOCOL", "Invalid tool catalogue.", 502);
          metadata.tools.push(...page.tools); cursor = page.nextCursor;
          if (metadata.tools.length > 100) throw new HungiiError("HUNGII_PROTOCOL", "The provider catalogue exceeded its limit.", 502);
        } while (cursor);
        ciphertext = await seal(JSON.stringify(metadata), `${user}:mcp:${this.generation}`, this.secret);
      }
      const userHash=await digest(user),sessionHash=await digest(metadata.sessionId);
      return await task({tools:metadata.tools, sessionId:metadata.sessionId,
        call: async (name, toolArgs) => {
          const started=Date.now();let status="ok";
          try {
            const result=(await post("tools/call", {name,arguments:toolArgs})).result;
            if(result._meta?.swiggy?.deprecation) console.warn(JSON.stringify({event:"swiggy_deprecation",tool:name}));
            return result;
          }catch(error){status="failed";throw error;}
          finally{console.info(JSON.stringify({event:"mcp_tool_call",user_id_hash:userHash,session_id_hash:sessionHash,tool:name,duration_ms:Date.now()-started,status}));}
        },
        close: async () => {},
      });
    } catch (error) {
      const safe = error instanceof HungiiError ? error : new HungiiError("HUNGII_UPSTREAM_UNAVAILABLE", "The provider request failed. Try later.", 502, 5);
      cooldown = safe.retryAfter ?? 5;
      if (!ciphertext) failure = "HUNGII_INITIALIZATION_UNCERTAIN";
      else if (["HUNGII_RECONNECT","HUNGII_PROVIDER_BLOCKED","HUNGII_PROTOCOL"].includes(safe.code)) failure = safe.code;
      throw safe;
    } finally {
      const finished = await this.db.rpc("finish_swiggy_session", {...args,metadata_ciphertext:ciphertext,failure_code:failure,retry_seconds:cooldown});
      if (finished.error || !finished.data) throw new HungiiError("HUNGII_CONNECTION_CHANGED", "The connection changed before this result could be saved. Start again.", 409);
    }
  }
}
