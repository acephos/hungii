import type { SupabaseClient } from "@supabase/supabase-js";
import {
  type Auth,
  createHandler,
  type Deps,
  type FoodSession,
  parseAddressSelection,
} from "./handler.ts";
import { HungiiError, type Json } from "../_shared/errors.ts";
import { base64, seal, unseal } from "../_shared/secrets.ts";
import { PRIVACY_VERSION } from "../_shared/tracker.ts";

function assert(value: unknown, message = "Assertion failed") {
  if (!value) throw new Error(message);
}
function equal(actual: unknown, expected: unknown, message = "") {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error(
      `${message} expected ${JSON.stringify(expected)}, got ${
        JSON.stringify(actual)
      }`,
    );
  }
}

type Result = { data?: unknown; error?: unknown };

/** An in-memory stand-in for the Supabase client calls the handler makes. */
class FakeDb {
  tables: Record<string, Json[]> = {};
  rpcs: Record<string, (args: Json) => Result> = {};
  log: string[] = [];

  rpc(name: string, args: Json = {}) {
    this.log.push(`rpc:${name}`);
    const handler = this.rpcs[name];
    if (!handler) {
      return Promise.resolve({ data: null, error: { message: "missing" } });
    }
    return Promise.resolve({ data: null, error: null, ...handler(args) });
  }

  from(table: string) {
    return new Query(this, table);
  }
}

class Query {
  private filters: [string, unknown][] = [];
  private op: "select" | "update" | "delete" = "select";
  private values: Json = {};
  constructor(private db: FakeDb, private table: string) {}
  select(_columns?: string) {
    return this;
  }
  eq(column: string, value: unknown) {
    this.filters.push([column, value]);
    return this;
  }
  order() {
    return this;
  }
  limit() {
    return this;
  }
  update(values: Json) {
    this.op = "update";
    this.values = values;
    return this;
  }
  delete() {
    this.op = "delete";
    return this;
  }
  private run(): Json[] {
    this.db.log.push(`${this.op}:${this.table}`);
    const all = this.db.tables[this.table] ??= [];
    const rows = all.filter((row) =>
      this.filters.every(([column, value]) => row[column] === value)
    );
    if (this.op === "update") {
      rows.forEach((row) => Object.assign(row, this.values));
    }
    if (this.op === "delete") {
      this.db.tables[this.table] = all.filter((row) => !rows.includes(row));
    }
    return rows;
  }
  maybeSingle() {
    return Promise.resolve({ data: this.run().at(-1) ?? null, error: null });
  }
  single() {
    const rows = this.run();
    return Promise.resolve(
      rows.length
        ? { data: rows[0], error: null }
        : { data: null, error: { message: "no rows" } },
    );
  }
  then<T>(resolve: (value: Result) => T, reject?: (reason: unknown) => T) {
    return Promise.resolve({ data: this.run(), error: null }).then(
      resolve,
      reject,
    );
  }
}

const USER = "6b3f1c7e-0000-4000-8000-000000000001";
const key = base64(crypto.getRandomValues(new Uint8Array(32)));
const identity = {
  subject: "user_01ABCDEFGHJK",
  sessionId: "session_01ABCDEFGHJK",
};

function setup(env: Record<string, string> = {}) {
  const db = new FakeDb();
  db.rpcs.resolve_hungii_account = () => ({
    data: [{ id: USER, status: "active" }],
  });
  db.rpcs.purge_hungii_expired = () => ({ data: null });
  const logs: Json[] = [];
  const network: { url: string; method: string; headers: Headers }[] = [];
  const foodSessions: FoodSession[] = [];
  const tools: Record<string, (args: Json) => Json> = {};
  const auth: Auth = {
    verify(token) {
      if (token !== "good") {
        return Promise.reject(
          new HungiiError("HUNGII_LOGIN_REQUIRED", "Please sign in.", 401),
        );
      }
      return Promise.resolve(identity);
    },
    refresh: () => Promise.resolve({ access_token: "a", refresh_token: "r" }),
    signOut: () => Promise.resolve(),
    deleteUser: () => Promise.resolve(),
  };
  const values: Record<string, string> = {
    SB_REGION: "ap-south-1",
    SUPABASE_URL: "https://project.supabase.co",
    SUPABASE_SERVICE_ROLE_KEY: "service-role",
    HUNGII_TOKEN_KEY: key,
    WORKOS_CLIENT_ID: "client_01ABCDEFGHJK",
    SWIGGY_AUTH_BASE_URL: "https://mcp-staging.swiggy.com/",
    SWIGGY_FOOD_URL: "https://mcp-staging.swiggy.com/food",
    SWIGGY_CLIENT_ID: "swiggy-client",
    ...env,
  };
  const deps: Deps = {
    env: (name) => values[name],
    database: () => db as unknown as SupabaseClient,
    auth: () => auth,
    network: (input, init) => {
      network.push({
        url: String(input),
        method: init?.method ?? "GET",
        headers: new Headers(init?.headers),
      });
      return Promise.resolve(Response.json({}));
    },
    food: (session, task) => {
      foodSessions.push(session);
      return task((name, args) => Promise.resolve(tools[name](args)));
    },
    log: (entry) => logs.push(entry),
    requestId: () => "request-1",
  };
  const handler = createHandler(deps);
  const post = (body: Json, token = "good") =>
    handler(
      new Request("https://project.supabase.co/functions/v1/hungii-api", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify(body),
      }),
    );
  return { db, logs, network, foodSessions, tools, handler, post, values };
}

async function connect(db: FakeDb, generation = 3) {
  db.tables.swiggy_connections = [{
    user_id: USER,
    generation,
    consent_version: PRIVACY_VERSION,
    expires_at: new Date(Date.now() + 86_400_000).toISOString(),
    encrypted_token: await seal("swiggy-token", `${USER}:swiggy`, key),
    encrypted_address_id: null,
  }];
}

const tracker = {
  day: "2026-10-27",
  calorieGoal: 2200,
  proteinGoal: 140,
  carbGoal: 250,
  fatGoal: 70,
  allowance: 600,
  spent: 0,
  opportunities: 2,
  intake: { calories: [0, 0], protein: [0, 0], carbs: [0, 0], fat: [0, 0] },
};

Deno.test("requests outside Mumbai are refused and correlated", async () => {
  const { handler, logs } = setup({ SB_REGION: "us-east-1" });
  const response = await handler(
    new Request("https://x.supabase.co/functions/v1/hungii-api", {
      method: "POST",
      body: "{}",
    }),
  );
  equal(response.status, 503);
  equal(response.headers.get("X-Request-Id"), "request-1");
  const body = await response.json();
  equal(body.error.code, "HUNGII_REGION");
  equal(body.error.requestId, "request-1");
  equal(logs, [{
    event: "request_failed",
    request_id: "request-1",
    code: "HUNGII_REGION",
    status: 503,
  }]);
});

Deno.test("account actions need a verified, active account", async () => {
  const { post, db } = setup();
  equal(
    (await (await post({ action: "status" }, "")).json()).error.code,
    "HUNGII_LOGIN_REQUIRED",
  );
  equal(
    (await (await post({ action: "status" }, "forged")).json()).error.code,
    "HUNGII_LOGIN_REQUIRED",
  );
  db.rpcs.resolve_hungii_account = () => ({
    data: [{ id: USER, status: "deleting" }],
  });
  const deleting = await post({ action: "state_get" });
  equal(deleting.status, 409);
  equal((await deleting.json()).error.code, "HUNGII_ACCOUNT_DELETING");
});

Deno.test("other methods are rejected", async () => {
  const { handler } = setup();
  const response = await handler(
    new Request("https://project.supabase.co/functions/v1/hungii-api", {
      method: "PUT",
    }),
  );
  equal(response.status, 405);
});

Deno.test("cloud profile sync stores only encrypted, validated state", async () => {
  const { post, db } = setup();
  db.rpcs.save_hungii_state = (args) => {
    db.tables.hungii_state = [{
      user_id: args.owner_id,
      state: args.encrypted_state,
      updated_at: "2026-10-27T09:00:00Z",
    }];
    return { data: [{ saved: true, updated_at: "2026-10-27T09:00:00Z" }] };
  };
  const stale = await post({
    action: "state_save",
    state: tracker,
    privacyVersion: "2026-01-01.1",
  });
  equal((await stale.json()).error.code, "HUNGII_CONSENT");
  const saved = await post({
    action: "state_save",
    state: tracker,
    privacyVersion: PRIVACY_VERSION,
  });
  equal((await saved.json()).updatedAt, "2026-10-27T09:00:00Z");
  const stored = db.tables.hungii_state[0].state;
  assert(!JSON.stringify(stored).includes("calorieGoal"), "Plaintext stored");
  equal(
    JSON.parse(await unseal(stored.ciphertext, `${USER}:tracker`, key)),
    tracker,
  );
  const restored = await (await post({ action: "state_get" })).json();
  equal(restored.state, tracker);
  equal(restored.consentVersion, PRIVACY_VERSION);
  db.rpcs.save_hungii_state = () => ({ data: [{ saved: false }] });
  const conflict = await post({
    action: "state_save",
    state: tracker,
    privacyVersion: PRIVACY_VERSION,
    expectedUpdatedAt: "2026-10-27T08:00:00Z",
  });
  equal(conflict.status, 409);
  equal((await conflict.json()).error.code, "HUNGII_SYNC_CONFLICT");
});

Deno.test("status reports the shared notice version and no connection", async () => {
  const { post } = setup();
  const status = await (await post({ action: "status" })).json();
  equal(status.privacyVersion, PRIVACY_VERSION);
  equal(status.connected, false);
  equal(status.addressId, null);
  equal(status.environment, "staging");
  equal(status.orderingEnabled, false);
  const food = await post({ action: "discover", query: "dal" });
  equal(food.status, 401);
  equal((await food.json()).error.code, "HUNGII_RECONNECT");
});

Deno.test("disconnect reads the provider session before withdrawal erases it", async () => {
  const { post, db, network } = setup();
  await connect(db);
  db.tables.swiggy_mcp_sessions = [{
    user_id: USER,
    generation: 3,
    phase: "ready",
    cooldown_until: null,
    encrypted_metadata: await seal(
      JSON.stringify({ sessionId: "mcp-1", protocol: "2025-11-25", tools: [] }),
      `${USER}:mcp:3`,
      key,
    ),
  }];
  db.rpcs.withdraw_swiggy_consent = () => {
    db.tables.swiggy_connections = [];
    db.tables.swiggy_mcp_sessions = [];
    return { data: null };
  };
  equal(
    (await (await post({ action: "disconnect" })).json()).error.code,
    "HUNGII_CONFIRM_REQUIRED",
  );
  const result = await (await post({ action: "disconnect", confirm: true }))
    .json();
  equal(result, {
    disconnected: true,
    deleted: false,
    revocationConfirmed: true,
  });
  const read = db.log.lastIndexOf("select:swiggy_mcp_sessions");
  const withdrawn = db.log.lastIndexOf("rpc:withdraw_swiggy_consent");
  assert(read >= 0 && read < withdrawn, "Session read after withdrawal");
  equal(network.map((n) => `${n.method} ${n.url}`), [
    "DELETE https://mcp-staging.swiggy.com/food",
    "POST https://mcp-staging.swiggy.com/auth/logout",
  ]);
  equal(network[0].headers.get("Mcp-Session-Id"), "mcp-1");
});

Deno.test("a blocked provider session is not contacted on disconnect", async () => {
  const { post, db, network } = setup();
  await connect(db);
  db.tables.swiggy_mcp_sessions = [{
    user_id: USER,
    generation: 3,
    phase: "blocked",
  }];
  db.rpcs.withdraw_swiggy_consent = () => ({ data: null });
  const result = await (await post({ action: "disconnect", confirm: true }))
    .json();
  equal(result.revocationConfirmed, false);
  equal(network.length, 0);
});

Deno.test("the selected address keeps its page as an encrypted lookup hint", async () => {
  const { post, db, tools, foodSessions } = setup({
    SWIGGY_SESSION_RESUME_VERIFIED: "true",
  });
  await connect(db, 7);
  tools.get_addresses = (args) => ({
    addresses: [{
      id: `a${args.page}`,
      addressLine: "Line",
      addressTag: "Home",
    }],
    pagination: { hasMore: false },
  });
  const listed = await (await post({ action: "addresses", page: 2 })).json();
  equal(listed.addresses, [{ id: "a2", label: "Home", addressLine: "Line" }]);
  const selected = await (await post({
    action: "select_address",
    addressId: "a2",
    page: 2,
  })).json();
  equal(selected.addressId, "a2");
  const stored = db.tables.swiggy_connections[0].encrypted_address_id;
  equal(
    JSON.parse(await unseal(stored, `${USER}:address`, key)),
    { id: "a2", page: 2 },
  );
  equal((await (await post({ action: "status" })).json()).addressId, "a2");
  equal(foodSessions.map((s) => [s.generation, s.approvedWrites]), [
    [7, false],
    [7, false],
  ]);
  // Rows written before the hint existed hold a bare ID.
  db.tables.swiggy_connections[0].encrypted_address_id = await seal(
    "legacy-address",
    `${USER}:address`,
    key,
  );
  equal(
    (await (await post({ action: "status" })).json()).addressId,
    "legacy-address",
  );
});

Deno.test("unexpected failures are logged without their message", async () => {
  const { post, db, logs } = setup();
  db.rpcs.resolve_hungii_account = () => {
    throw new TypeError(`connection to ${USER} reset`);
  };
  const response = await post({ action: "status" });
  equal(response.status, 503);
  equal((await response.json()).error.code, "HUNGII_UNAVAILABLE");
  equal(logs.length, 1);
  equal(logs[0].event, "unhandled_error");
  equal(logs[0].error, "TypeError");
  equal(logs[0].request_id, "request-1");
  assert(!JSON.stringify(logs).includes(USER), "Logged request data");
});

Deno.test("address selections parse both formats", () => {
  equal(parseAddressSelection("abc"), { id: "abc" });
  equal(parseAddressSelection('{"id":"abc","page":3}'), { id: "abc", page: 3 });
  equal(parseAddressSelection('{"id":"abc","page":0}'), { id: "abc" });
  let rejected = false;
  try {
    parseAddressSelection('{"page":3}');
  } catch {
    rejected = true;
  }
  assert(rejected, "Selection without an ID accepted");
});
