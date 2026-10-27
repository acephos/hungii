/**
 * Restricted operator tasks against the Hungii database, using the service role.
 *
 *   list-checkouts                                  Checkouts that block new orders
 *   resolve-checkout <user> <request> confirmed --order-id=<id>
 *   resolve-checkout <user> <request> failed       Record what Swiggy support confirmed
 *   reseal [--dry-run]                              Re-encrypt rows still on an older key
 *
 * Run from the repository root:
 *   deno run --config supabase/functions/deno.json --allow-env --allow-net scripts/operator.ts <task>
 *
 * Needs SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY and HUNGII_TOKEN_KEY (plus
 * HUNGII_TOKEN_KEY_ID / HUNGII_TOKEN_KEYS_PREVIOUS during rotation). Output omits
 * addresses, tokens and tracker contents.
 */
import { createClient, type SupabaseClient } from "@supabase/supabase-js";
import type { Json } from "../supabase/functions/_shared/errors.ts";
import {
  type Keyring,
  keyringFromEnv,
  seal,
  sealedKeyId,
  unseal,
} from "../supabase/functions/_shared/secrets.ts";

const ACTIVE = ["placing", "pending", "unresolved"];

/** Applies a support-verified outcome to a blocked attempt. */
export function resolveAttempt(
  attempt: Json,
  outcome: "confirmed" | "failed",
  orderId: string | undefined,
  now: number,
): Json {
  if (!ACTIVE.includes(attempt.phase)) {
    throw new Error(`Attempt is already ${attempt.phase}; nothing to resolve.`);
  }
  if (outcome === "confirmed" && !orderId) {
    throw new Error("A confirmed order needs --order-id from Swiggy.");
  }
  const provider = { ...(attempt.provider ?? {}) };
  if (orderId) provider.orderId = orderId;
  return {
    ...attempt,
    phase: outcome,
    provider,
    resolution: {
      outcome,
      by: "operator",
      at: now,
      previousPhase: attempt.phase,
    },
  };
}

/** What an operator needs to match an attempt with Swiggy support, without personal data. */
export function summarizeAttempt(row: Json, attempt: Json): Json {
  const cart = attempt.cart ?? {};
  return {
    user: row.user_id,
    request: row.request_id,
    phase: attempt.phase,
    created: new Date(attempt.createdAt ?? row.created_at).toISOString(),
    restaurant: cart.restaurant ?? null,
    payable: cart.payable ?? null,
    items: (cart.items ?? []).reduce(
      (n: number, i: Json) => n + (i.quantity ?? 0),
      0,
    ),
    orderId: attempt.provider?.orderId ?? null,
    paymentReference: attempt.provider?.paasId ? "present" : null,
  };
}

/** Every application-encrypted value, with the additional data that binds it to its row. */
export type EncryptedField = {
  table: string;
  select: string;
  keys: string[];
  read: (row: Json) => string | null;
  aad: (row: Json) => string;
  write: (row: Json, ciphertext: string) => Json;
  /** Column filter that proves the row still holds the value that was read. */
  match: string;
};

export const ENCRYPTED_FIELDS: EncryptedField[] = [
  {
    table: "swiggy_oauth_states",
    select: "state_hash,user_id,encrypted_verifier",
    keys: ["state_hash"],
    read: (r) => r.encrypted_verifier,
    aad: (r) => `${r.user_id}:pkce`,
    write: (_r, c) => ({ encrypted_verifier: c }),
    match: "encrypted_verifier",
  },
  {
    table: "swiggy_connections",
    select: "user_id,encrypted_token",
    keys: ["user_id"],
    read: (r) => r.encrypted_token,
    aad: (r) => `${r.user_id}:swiggy`,
    write: (_r, c) => ({ encrypted_token: c }),
    match: "encrypted_token",
  },
  {
    table: "swiggy_connections",
    select: "user_id,encrypted_address_id",
    keys: ["user_id"],
    read: (r) => r.encrypted_address_id,
    aad: (r) => `${r.user_id}:address`,
    write: (_r, c) => ({ encrypted_address_id: c }),
    match: "encrypted_address_id",
  },
  {
    table: "swiggy_mcp_sessions",
    select: "user_id,generation,encrypted_metadata",
    keys: ["user_id"],
    read: (r) => r.encrypted_metadata,
    aad: (r) => `${r.user_id}:mcp:${r.generation}`,
    write: (_r, c) => ({ encrypted_metadata: c }),
    match: "encrypted_metadata",
  },
  {
    table: "hungii_state",
    select: "user_id,state",
    keys: ["user_id"],
    read: (r) => r.state?.ciphertext ?? null,
    aad: (r) => `${r.user_id}:tracker`,
    write: (r, c) => ({ state: { ...r.state, ciphertext: c } }),
    match: "state->>ciphertext",
  },
  {
    table: "hungii_checkouts",
    select: "user_id,request_id,ciphertext",
    keys: ["user_id", "request_id"],
    read: (r) => r.ciphertext,
    aad: (r) => `${r.user_id}:checkout:${r.request_id}`,
    write: (_r, c) => ({ ciphertext: c }),
    match: "ciphertext",
  },
];

/** The value re-sealed under the current key, or null when it already uses it. */
export async function resealValue(
  value: string,
  aad: string,
  keys: Keyring,
): Promise<string | null> {
  if (sealedKeyId(value) === keys.current.id) return null;
  return await seal(await unseal(value, aad, keys), aad, keys);
}

async function rows(
  db: SupabaseClient,
  table: string,
  select: string,
): Promise<Json[]> {
  const all: Json[] = [];
  for (let from = 0;; from += 1000) {
    const { data, error } = await db.from(table).select(select).range(
      from,
      from + 999,
    );
    if (error) throw new Error(`${table}: ${error.message}`);
    all.push(...(data ?? []));
    if (!data || data.length < 1000) return all;
  }
}

async function listCheckouts(db: SupabaseClient, keys: Keyring) {
  const { data, error } = await db.from("hungii_checkouts").select(
    "user_id,request_id,phase,ciphertext,created_at",
  ).in("phase", ACTIVE).order("created_at");
  if (error) throw new Error(error.message);
  const summaries = [];
  for (const row of data ?? []) {
    const attempt = JSON.parse(
      await unseal(
        row.ciphertext,
        `${row.user_id}:checkout:${row.request_id}`,
        keys,
      ),
    );
    summaries.push(summarizeAttempt(row, attempt));
  }
  console.log(JSON.stringify(summaries, null, 2));
}

async function resolveCheckout(
  db: SupabaseClient,
  keys: Keyring,
  [user, request, outcome]: string[],
  orderId: string | undefined,
) {
  if (!user || !request || (outcome !== "confirmed" && outcome !== "failed")) {
    throw new Error(
      "Usage: resolve-checkout <user> <request> confirmed|failed [--order-id=<id>]",
    );
  }
  const { data: row, error } = await db.from("hungii_checkouts").select(
    "ciphertext",
  ).eq("user_id", user).eq("request_id", request).maybeSingle();
  if (error || !row) throw new Error("Checkout not found.");
  const aad = `${user}:checkout:${request}`;
  const resolved = resolveAttempt(
    JSON.parse(await unseal(row.ciphertext, aad, keys)),
    outcome,
    orderId,
    Date.now(),
  );
  // Only replace the attempt this operator inspected; a concurrent change aborts.
  const updated = await db.from("hungii_checkouts").update({
    phase: resolved.phase,
    ciphertext: await seal(JSON.stringify(resolved), aad, keys),
  }).eq("user_id", user).eq("request_id", request).eq(
    "ciphertext",
    row.ciphertext,
  ).select("request_id").maybeSingle();
  if (updated.error || !updated.data) {
    throw new Error(
      "The checkout changed while resolving it. Inspect it again.",
    );
  }
  console.log(`Checkout ${request} recorded as ${outcome}.`);
}

async function reseal(db: SupabaseClient, keys: Keyring, dryRun: boolean) {
  for (const field of ENCRYPTED_FIELDS) {
    let changed = 0, skipped = 0;
    for (const row of await rows(db, field.table, field.select)) {
      const value = field.read(row);
      if (!value) continue;
      const next = await resealValue(value, field.aad(row), keys);
      if (!next) continue;
      if (dryRun) {
        changed++;
        continue;
      }
      let query = db.from(field.table).update(field.write(row, next));
      for (const key of field.keys) query = query.eq(key, row[key]);
      const result = await query.eq(field.match, value).select(field.keys[0])
        .maybeSingle();
      if (result.error) {
        throw new Error(`${field.table}: ${result.error.message}`);
      }
      // A row rewritten meanwhile already uses the current key; run again to confirm.
      if (result.data) changed++;
      else skipped++;
    }
    console.log(
      `${field.table}.${field.match}: ${changed} ${
        dryRun ? "to re-encrypt" : "re-encrypted"
      }${skipped ? `, ${skipped} changed concurrently` : ""}`,
    );
  }
}

if (import.meta.main) {
  const [task, ...rest] = Deno.args.filter((a) => !a.startsWith("--"));
  const flag = (name: string) =>
    Deno.args.find((a) => a.startsWith(`--${name}`))?.split("=")[1] ?? "";
  const keys = keyringFromEnv((name) => Deno.env.get(name));
  const url = Deno.env.get("SUPABASE_URL"),
    service = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!keys || !url || !service) {
    console.error(
      "Set SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY and HUNGII_TOKEN_KEY.",
    );
    Deno.exit(2);
  }
  const db = createClient(url, service, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  try {
    if (task === "list-checkouts") await listCheckouts(db, keys);
    else if (task === "resolve-checkout") {
      await resolveCheckout(db, keys, rest, flag("order-id") || undefined);
    } else if (task === "reseal") {
      await reseal(db, keys, Deno.args.includes("--dry-run"));
    } else {
      console.error("Tasks: list-checkouts, resolve-checkout, reseal");
      Deno.exit(2);
    }
  } catch (error) {
    console.error(error instanceof Error ? error.message : String(error));
    Deno.exit(1);
  }
}
