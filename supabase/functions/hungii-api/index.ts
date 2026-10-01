import { createClient } from "@supabase/supabase-js";
import { cartView, discover, discovery, menuView, HungiiError, withFood, type Json, type ToolCall } from "../_shared/food.ts";
import { digest, randomSecret, seal, unseal } from "../_shared/secrets.ts";

const required = (name: string): string => {
  const value = Deno.env.get(name);
  if (!value) throw new HungiiError("HUNGII_SETUP_REQUIRED", "Hungii's connection is not set up yet.", 503);
  return value;
};
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
  status, headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
});
const text = (value: unknown, limit = 150): string => {
  if (typeof value !== "string" || !value.trim() || value.length > limit) throw new HungiiError("HUNGII_BAD_INPUT", "Please check this input.");
  return value.trim();
};
const pageNumber = (value: unknown) => {
  if (value === undefined) return 1;
  if (!Number.isInteger(value) || Number(value) < 1 || Number(value) > 100) throw new HungiiError("HUNGII_BAD_INPUT", "Invalid address page.");
  return Number(value);
};
function enforceRegion() {
  if (Deno.env.get("SB_REGION") !== "ap-south-1" && Deno.env.get("HUNGII_LOCAL_DEVELOPMENT") !== "true") {
    throw new HungiiError("HUNGII_REGION", "This connection must run in Hungii's configured region.", 503);
  }
}
function authBase(): string {
  const base = new URL(required("SWIGGY_AUTH_BASE_URL"));
  if (base.protocol !== "https:" || !["mcp.swiggy.com", "mcp-staging.swiggy.com"].includes(base.hostname) || base.pathname !== "/") throw new HungiiError("HUNGII_CONFIG", "Invalid Swiggy authorization configuration.", 503);
  return base.origin;
}
function redirect(): string {
  const url = new URL(required("SWIGGY_REDIRECT_URI"));
  if (url.protocol !== "https:" || !url.pathname.endsWith("/hungii-api/callback") || url.searchParams.get("forceFunctionRegion") !== "ap-south-1") throw new HungiiError("HUNGII_CONFIG", "An approved Mumbai HTTPS callback is required.", 503);
  return url.href;
}
async function upstreamJson(url: string, body: Json, bearer?: string): Promise<Json> {
  const response = await fetch(url, { method: "POST", headers: { "Content-Type": "application/json", ...(bearer ? { Authorization: `Bearer ${bearer}` } : {}) }, body: JSON.stringify(body), signal: AbortSignal.timeout(20_000) });
  if (!response.ok) throw new HungiiError("HUNGII_AUTH_FAILED", "Swiggy authorization did not complete. Please try connecting again.", 502);
  return await response.json();
}

Deno.serve(async (request: Request) => {
  try {
    enforceRegion();
    const db = createClient(required("SUPABASE_URL"), required("SUPABASE_SERVICE_ROLE_KEY"), { auth: { persistSession: false, autoRefreshToken: false } });
    const secret = required("HUNGII_TOKEN_KEY");
    const url = new URL(request.url);
    const check = (error: unknown) => { if (error) throw new HungiiError("HUNGII_STORAGE", "Hungii could not save this update. Try again.", 503); };

    if (request.method === "GET" && url.pathname.endsWith("/callback")) {
      const state = text(url.searchParams.get("state"), 200);
      const { data, error } = await db.rpc("consume_swiggy_oauth_state", { state_digest: await digest(state) });
      check(error);
      const pending = data?.[0];
      if (!pending) throw new HungiiError("HUNGII_CALLBACK_EXPIRED", "This connection link expired or was already used. Connect again.");
      if (url.searchParams.has("error")) throw new HungiiError("HUNGII_AUTH_CANCELLED", "Swiggy authorization was cancelled.");
      const verifier = await unseal(pending.encrypted_verifier, `${pending.user_id}:pkce`, secret);
      const token = await upstreamJson(`${authBase()}/auth/token`, { grant_type: "authorization_code", client_id: required("SWIGGY_CLIENT_ID"), code: text(url.searchParams.get("code"), 2048), code_verifier: verifier, redirect_uri: redirect() });
      if (typeof token.access_token !== "string" || !Number.isFinite(token.expires_in) || token.expires_in <= 0) throw new HungiiError("HUNGII_AUTH_FAILED", "Swiggy did not return a valid session.", 502);
      const stored = await db.from("swiggy_connections").upsert({ user_id: pending.user_id, encrypted_token: await seal(token.access_token, `${pending.user_id}:swiggy`, secret), expires_at: new Date(Date.now() + token.expires_in * 1000).toISOString(), address_id: null });
      check(stored.error);
      return new Response('<!doctype html><meta name="viewport" content="width=device-width"><title>Hungii connected</title><style>body{background:#101211;color:#f5f7ee;font:20px system-ui;padding:40px}a{color:#caff48}</style><h1>Swiggy connected.</h1><p>Return to Hungii to choose your delivery address.</p><a href="hungii://swiggy-return">Open Hungii</a>', { headers: { "Content-Type": "text/html", "Cache-Control": "no-store", "Referrer-Policy": "no-referrer", "Content-Security-Policy": "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'" } });
    }

    if (request.method !== "POST") return json({ error: { code: "HUNGII_METHOD", message: "Use the Hungii app to connect." } }, 405);
    const bearer = request.headers.get("Authorization")?.match(/^Bearer (.+)$/)?.[1];
    if (!bearer) throw new HungiiError("HUNGII_LOGIN_REQUIRED", "Please sign in to Hungii.", 401);
    const { data: identity, error: authError } = await db.auth.getUser(bearer);
    if (authError || !identity.user) throw new HungiiError("HUNGII_LOGIN_REQUIRED", "Please sign in to Hungii again.", 401);
    const user = identity.user.id;
    if (Number(request.headers.get("Content-Length") ?? 0) > 100_000) throw new HungiiError("HUNGII_BAD_INPUT", "This request is too large.");
    const raw = await request.text();
    if (raw.length > 100_000) throw new HungiiError("HUNGII_BAD_INPUT", "This request is too large.");
    let body: Json;
    try { body = JSON.parse(raw); } catch { throw new HungiiError("HUNGII_BAD_INPUT", "Invalid request."); }
    const action = text(body.action);

    if (action === "connect") {
      if (body.consent !== true) throw new HungiiError("HUNGII_CONSENT", "Permission is required to securely keep your Swiggy connection.");
      const state = randomSecret(), verifier = randomSecret();
      const clean = await db.from("swiggy_oauth_states").delete().eq("user_id", user); check(clean.error);
      const stored = await db.from("swiggy_oauth_states").insert({ user_id: user, state_hash: await digest(state), encrypted_verifier: await seal(verifier, `${user}:pkce`, secret), expires_at: new Date(Date.now() + 10 * 60_000).toISOString() }); check(stored.error);
      const authorization = new URL(`${authBase()}/auth/authorize`);
      authorization.search = new URLSearchParams({ response_type: "code", client_id: required("SWIGGY_CLIENT_ID"), redirect_uri: redirect(), code_challenge: await digest(verifier), code_challenge_method: "S256", state, scope: "mcp:tools" }).toString();
      return json({ authorizationUrl: authorization.href });
    }
    // Only user-authored tracker state; no whole Swiggy payloads or tokens in this table.
    if (action === "state_get") {
      const stored = await db.from("hungii_state").select("state").eq("user_id", user).maybeSingle(); check(stored.error);
      return json({ state: stored.data?.state ?? null });
    }
    if (action === "state_save") {
      if (!body.state || typeof body.state !== "object" || Array.isArray(body.state)) throw new HungiiError("HUNGII_BAD_INPUT", "Invalid tracker state.");
      const allowed = new Set(["day", "calorieGoal", "proteinGoal", "carbGoal", "fatGoal", "allowance", "spent", "opportunities", "intake"]);
      if (Object.keys(body.state).some(k => !allowed.has(k))) throw new HungiiError("HUNGII_BAD_INPUT", "Only your tracker can be synced.");
      const stored = await db.from("hungii_state").upsert({ user_id: user, state: body.state, updated_at: new Date().toISOString() }); check(stored.error);
      return json({ saved: true });
    }
    const { data: connection, error } = await db.from("swiggy_connections").select("*").eq("user_id", user).maybeSingle(); check(error);
    const connected = connection && Date.parse(connection.expires_at) > Date.now() + 60_000;
    if (action === "status") return json({ connected: Boolean(connected), addressId: connected ? connection.address_id : null, expiresAt: connected ? connection.expires_at : null, environment: required("SWIGGY_FOOD_URL").includes("mcp-staging") ? "staging" : "production", cartWritesEnabled: false, priceUnitVerified: ["rupees", "paise"].includes(Deno.env.get("SWIGGY_PRICE_UNIT") ?? "") });
    if (action === "disconnect") {
      if (connection) {
        const token = await unseal(connection.encrypted_token, `${user}:swiggy`, secret);
        // Keep the connection if revocation fails; user can retry rather than assume success.
        await upstreamJson(`${authBase()}/auth/logout`, {}, token);
      }
      const removed = await db.from("swiggy_connections").delete().eq("user_id", user); check(removed.error);
      const removedStates = await db.from("swiggy_oauth_states").delete().eq("user_id", user); check(removedStates.error);
      return json({ disconnected: true });
    }
    if (!connected) throw new HungiiError("HUNGII_RECONNECT", "Connect your Swiggy account to find meals.", 401);
    const token = await unseal(connection.encrypted_token, `${user}:swiggy`, secret);
    const unit = Deno.env.get("SWIGGY_PRICE_UNIT") ?? "unverified";
    return await withFood(required("SWIGGY_FOOD_URL"), token, async (call: ToolCall) => {
      if (action === "addresses" || action === "select_address") {
        const data = await call("get_addresses", { page: pageNumber(body.page), pageSize: 10 });
        if (action === "select_address") {
          const addressId = text(body.addressId);
          if (!data.addresses?.some((a: Json) => a.id === addressId)) throw new HungiiError("HUNGII_ADDRESS", "Choose an address from the current list.");
          const selected = await db.from("swiggy_connections").update({ address_id: addressId }).eq("user_id", user); check(selected.error);
          return json({ addressId });
        }
        return json({ addresses: (data.addresses ?? []).map((a: Json) => ({ id: a.id, label: a.addressTag ?? a.addressCategory ?? "Delivery address", addressLine: a.addressLine })), pagination: data.pagination });
      }
      const addressId = connection.address_id;
      if (!addressId) throw new HungiiError("HUNGII_ADDRESS_REQUIRED", "Choose your delivery address first.", 409);
      if (action === "discover") {
        let offset: number | undefined;
        if (body.offset !== undefined) {
          if (!Number.isFinite(body.offset) || body.offset < 0) throw new HungiiError("HUNGII_BAD_INPUT", "Invalid result page.");
          offset = body.offset;
        }
        return json(discovery(await discover(call, addressId, text(body.query), body.collection, offset), unit));
      }
      if (action === "menu") return json(menuView(await call("search_menu", { addressId, query: text(body.query), restaurantIdOfAddedItem: text(body.restaurantId), ...(body.vegOnly === true ? { vegFilter: 1 } : {}) }), unit));
      if (action === "restaurant_menu") return json(await call("get_restaurant_menu", { addressId, restaurantId: text(body.restaurantId) }));
      if (action === "cart") return json(cartView(await call("get_food_cart", { addressId }), unit));
      if (action === "coupons") {
        const data = await call("fetch_food_coupons", { addressId, restaurantId: text(body.restaurantId) });
        return json({ sections: data.coupon_sections ?? [], summary: data.summary ?? null });
      }
      throw new HungiiError("HUNGII_ACTION", "This action is not enabled in this integration.", 409);
    });
  } catch (error) {
    const safe = error instanceof HungiiError ? error : new HungiiError("HUNGII_UNAVAILABLE", "Hungii could not complete this request. Please retry.", 503);
    // No upstream messages, tokens, addresses or request bodies in logs.
    return json({ error: { code: safe.code, message: safe.message } }, safe.status);
  }
});
