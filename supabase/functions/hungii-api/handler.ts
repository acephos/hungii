import { createClient } from "@supabase/supabase-js";
import { cartView, discover, discovery, menuView, HungiiError, withFood, type Json, type ToolCall } from "../_shared/food.ts";
import { digest, randomSecret, seal, unseal } from "../_shared/secrets.ts";
import { trackerState, PRIVACY_VERSION } from "../_shared/tracker.ts";
import { DurableFoodSessions } from "../_shared/durable-sessions.ts";

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
    throw new HungiiError("HUNGII_REGION", "This service must run in Mumbai.", 503);
  }
}
function authBase(): string {
  const base = new URL(required("SWIGGY_AUTH_BASE_URL"));
  if (base.protocol !== "https:" || !["mcp.swiggy.com", "mcp-staging.swiggy.com"].includes(base.hostname) || base.pathname !== "/") throw new HungiiError("HUNGII_CONFIG", "Invalid Swiggy authorization configuration.", 503);
  return base.origin;
}
function redirect(): string {
  const url = new URL(required("SWIGGY_REDIRECT_URI"));
  const project = new URL(required("SUPABASE_URL"));
  if (url.protocol !== "https:" || url.hostname !== project.hostname || url.pathname !== "/functions/v1/hungii-api/callback" || url.search !== "?forceFunctionRegion=ap-south-1") throw new HungiiError("HUNGII_CONFIG", "An approved Mumbai HTTPS callback is required.", 503);
  return url.href;
}
async function upstreamJson(url: string, body: Json, bearer?: string): Promise<Json> {
  const response = await fetch(url, { method: "POST", redirect: "error", headers: { "Content-Type": "application/json", ...(bearer ? { Authorization: `Bearer ${bearer}` } : {}) }, body: JSON.stringify(body), signal: AbortSignal.timeout(10_000) });
  if (!response.ok) throw new HungiiError("HUNGII_AUTH_FAILED", "Swiggy authorization did not complete. Please try connecting again.", 502);
  return await response.json();
}

export async function handler(request: Request): Promise<Response> {
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
      if(pending.consent_version!==PRIVACY_VERSION) throw new HungiiError("HUNGII_CONSENT", "Read the current privacy notice and connect again.");
      const stored = await db.rpc("store_swiggy_connection", { owner_id: pending.user_id, expected_generation: pending.generation, token_ciphertext: await seal(token.access_token, `${pending.user_id}:swiggy`, secret), expiry: new Date(Date.now() + Math.min(token.expires_in,5*86400) * 1000).toISOString(), privacy_version: pending.consent_version, consent_time: pending.consented_at });
      check(stored.error);
      if(!stored.data) {
        await upstreamJson(`${authBase()}/auth/logout`, {}, token.access_token).catch(()=>{});
        throw new HungiiError("HUNGII_CONSENT_WITHDRAWN", "This connection request was withdrawn. Connect again if needed.",409);
      }
      return new Response('<!doctype html><meta name="viewport" content="width=device-width"><title>Hungii connected</title><style>body{background:#101211;color:#f5f7ee;font:20px system-ui;padding:40px}a{color:#caff48}</style><h1>Swiggy connected.</h1><p>Return to Hungii to choose your delivery address.</p><a href="hungii://swiggy-return">Open Hungii</a>', { headers: { "Content-Type": "text/html", "Cache-Control": "no-store", "Referrer-Policy": "no-referrer", "Content-Security-Policy": "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'" } });
    }

    if (request.method !== "POST") return json({ error: { code: "HUNGII_METHOD", message: "Use the Hungii app to connect." } }, 405);
    const bearer = request.headers.get("Authorization")?.match(/^Bearer (.+)$/)?.[1];
    if (!bearer) throw new HungiiError("HUNGII_LOGIN_REQUIRED", "Please sign in to Hungii.", 401);
    const { data: identity, error: authError } = await db.auth.getUser(bearer);
    if (authError || !identity.user) throw new HungiiError("HUNGII_LOGIN_REQUIRED", "Please sign in to Hungii again.", 401);
    const user = identity.user.id;
    const purged=await db.rpc("purge_hungii_expired");check(purged.error);
    if (Number(request.headers.get("Content-Length") ?? 0) > 100_000) throw new HungiiError("HUNGII_BAD_INPUT", "This request is too large.");
    const raw = await request.text();
    if (raw.length > 100_000) throw new HungiiError("HUNGII_BAD_INPUT", "This request is too large.");
    let body: Json;
    try { body = JSON.parse(raw); } catch { throw new HungiiError("HUNGII_BAD_INPUT", "Invalid request."); }
    const action = text(body.action);

    if (action === "connect") {
      if (body.consent !== true || body.privacyVersion!==PRIVACY_VERSION) throw new HungiiError("HUNGII_CONSENT", "Read the privacy notice before allowing connection retention.");
      const existing=await db.from("swiggy_connections").select("user_id").eq("user_id",user).maybeSingle();check(existing.error);
      if(existing.data) throw new HungiiError("HUNGII_ALREADY_CONNECTED", "Disconnect the previous Swiggy connection before authorizing a new one.",409);
      const state = randomSecret(), verifier = randomSecret();
      const authorization = new URL(`${authBase()}/auth/authorize`);
      const callback=redirect(),clientId=required("SWIGGY_CLIENT_ID");
      const stored = await db.rpc("begin_swiggy_consent", {owner_id:user,state_digest:await digest(state),verifier_ciphertext:await seal(verifier,`${user}:pkce`,secret),privacy_version:PRIVACY_VERSION}); check(stored.error);
      authorization.search = new URLSearchParams({ response_type: "code", client_id: clientId, redirect_uri: callback, code_challenge: await digest(verifier), code_challenge_method: "S256", state, scope: "mcp:tools" }).toString();
      return json({ authorizationUrl: authorization.href });
    }
    // Only user-authored tracker state; no whole Swiggy payloads or tokens in this table.
    if (action === "state_get") {
      const stored = await db.from("hungii_state").select("state").eq("user_id", user).maybeSingle(); check(stored.error);
      const encrypted=stored.data?.state?.ciphertext;
      return json({ state: encrypted ? trackerState(JSON.parse(await unseal(encrypted,`${user}:tracker`,secret))) : null });
    }
    if (action === "state_save") {
      if(body.privacyVersion!==PRIVACY_VERSION) throw new HungiiError("HUNGII_CONSENT", "Read the privacy notice before syncing.");
      const normalized=trackerState(body.state);
      const stored = await db.from("hungii_state").upsert({ user_id: user, state: { ciphertext: await seal(JSON.stringify(normalized),`${user}:tracker`,secret), consentVersion: PRIVACY_VERSION }, updated_at: new Date().toISOString() }); check(stored.error);
      return json({ saved: true });
    }
    const { data: connection, error } = await db.from("swiggy_connections").select("*").eq("user_id", user).maybeSingle(); check(error);
    const connected = connection && connection.consent_version===PRIVACY_VERSION && Date.parse(connection.expires_at) > Date.now() + 60_000;
    const addressId = connected && connection.encrypted_address_id ? await unseal(connection.encrypted_address_id,`${user}:address`,secret) : null;
    if (action === "status") return json({ connected: Boolean(connected), addressId, expiresAt: connected ? connection.expires_at : null, environment: Deno.env.get("SWIGGY_FOOD_URL")?.includes("mcp-staging") ? "staging" : Deno.env.get("SWIGGY_FOOD_URL") ? "production" : "not-configured", cartWritesEnabled: false, priceUnitVerified: ["rupees", "paise"].includes(Deno.env.get("SWIGGY_PRICE_UNIT") ?? "") });
    if (["disconnect","delete_account","delete_cloud_tracker"].includes(action)) {
      if(body.confirm!==true) throw new HungiiError("HUNGII_CONFIRM_REQUIRED", "Confirm deletion first.");
      if(action==="delete_cloud_tracker") { const removed=await db.from("hungii_state").delete().eq("user_id",user);check(removed.error);return json({deleted:true}); }
      const session=await db.from("swiggy_mcp_sessions").select("encrypted_metadata,generation,phase,cooldown_until").eq("user_id",user).maybeSingle();check(session.error);
      const remoteAllowed=session.data?.phase!=="blocked" && !(session.data?.cooldown_until && Date.parse(session.data.cooldown_until)>Date.now());
      let revoked = !connection;
      const withdrawn=await db.rpc("withdraw_swiggy_consent",{owner_id:user});check(withdrawn.error);
      if (connection && remoteAllowed) {
        try {
          const token = await unseal(connection.encrypted_token, `${user}:swiggy`, secret);
          if(session.data?.encrypted_metadata) {
            try {
              const metadata=JSON.parse(await unseal(session.data.encrypted_metadata,`${user}:mcp:${session.data.generation}`,secret));
              const endpoint=new URL(required("SWIGGY_FOOD_URL"));
              if(endpoint.protocol!=="https:" || !["mcp.swiggy.com","mcp-staging.swiggy.com"].includes(endpoint.hostname) || endpoint.pathname!=="/food") throw new Error("Invalid endpoint");
              const ended=await fetch(endpoint,{method:"DELETE",redirect:"error",signal:AbortSignal.timeout(8000),headers:{Authorization:`Bearer ${token}`,"Mcp-Session-Id":metadata.sessionId,"MCP-Protocol-Version":metadata.protocol}});
              await ended.body?.cancel().catch(()=>{});
              // Provider rejection/cooldown forbids a second remote attempt.
              if([401,419,403,429].includes(ended.status)) throw new HungiiError("HUNGII_REVOCATION_UNCONFIRMED","Remote revocation could not be confirmed.");
            }catch(error){if(error instanceof HungiiError)throw error;}
          }
          await upstreamJson(`${authBase()}/auth/logout`, {}, token);revoked=true;
        } catch { /* Withdrawal still erases local provider data. */ }
      }
      if(action==="delete_account") {const removedUser=await db.auth.admin.deleteUser(user,false);check(removedUser.error);}
      return json({ disconnected: true, deleted: action==="delete_account", revocationConfirmed: revoked });
    }
    if (!connected) throw new HungiiError("HUNGII_RECONNECT", "Connect your Swiggy account to find meals.", 401);
    if(Deno.env.get("SWIGGY_SESSION_RESUME_VERIFIED")!=="true") throw new HungiiError("HUNGII_STAGING_REQUIRED", "Live Food access awaits staging verification of session reuse.",503);
    const token = await unseal(connection.encrypted_token, `${user}:swiggy`, secret);
    const unit = Deno.env.get("SWIGGY_PRICE_UNIT") ?? "unverified";
    return await withFood(required("SWIGGY_FOOD_URL"), token, async (call: ToolCall) => {
      if (action === "addresses" || action === "select_address") {
        const data = await call("get_addresses", { page: pageNumber(body.page), pageSize: 10 });
        if (action === "select_address") {
          const addressId = text(body.addressId);
          if (!data.addresses?.some((a: Json) => a.id === addressId)) throw new HungiiError("HUNGII_ADDRESS", "Choose an address from the current list.");
          const selected = await db.from("swiggy_connections").update({ encrypted_address_id: await seal(addressId,`${user}:address`,secret) }).eq("user_id", user).eq("generation",connection.generation).select("user_id").maybeSingle(); check(selected.error);
          if(!selected.data) throw new HungiiError("HUNGII_CONNECTION_CHANGED", "The connection changed. Select the address again.",409);
          return json({ addressId });
        }
        return json({ addresses: (data.addresses ?? []).map((a: Json) => ({ id: a.id, label: a.addressTag ?? a.addressCategory ?? "Delivery address", addressLine: a.addressLine })), pagination: data.pagination });
      }
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
    }, user, false, new DurableFoodSessions(db,secret,connection.generation));
  } catch (error) {
    const safe = error instanceof HungiiError ? error : new HungiiError("HUNGII_UNAVAILABLE", "Hungii could not complete this request. Please retry.", 503);
    // No upstream messages, tokens, addresses or request bodies in logs.
    const response=json({ error: { code: safe.code, message: safe.message, retryAfter: safe.retryAfter ?? null } }, safe.status);
    if(safe.retryAfter) response.headers.set("Retry-After",String(safe.retryAfter));
    return response;
  }
}
