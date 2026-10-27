import { BRAND_WORDMARK } from "../_shared/brand.ts";
import {
  attemptView,
  FoodOrdering,
  orderingConfig,
} from "../_shared/ordering.ts";
import { CheckoutStore } from "../_shared/checkout-store.ts";
import { createClient, type SupabaseClient } from "@supabase/supabase-js";
import {
  cartView,
  discover,
  discovery,
  HungiiError,
  type Json,
  menuView,
  type ToolCall,
  withFood,
} from "../_shared/food.ts";
import {
  digest,
  type Keyring,
  keyringFromEnv,
  randomSecret,
  seal,
  unseal,
} from "../_shared/secrets.ts";
import { PRIVACY_VERSION, trackerState } from "../_shared/tracker.ts";
import { DurableFoodSessions } from "../_shared/durable-sessions.ts";
import { workosAuth, type WorkOSIdentity } from "../_shared/workos.ts";
import { readJsonObject } from "../_shared/http.ts";

const SWIGGY_HOSTS = ["mcp.swiggy.com", "mcp-staging.swiggy.com"];
const ADDRESS_PAGE_SIZE = 10;

/** The account service operations the handler needs. */
export type Auth = {
  verify(token: string): Promise<WorkOSIdentity>;
  refresh(refreshToken: string): Promise<Json>;
  signOut(identity: WorkOSIdentity): Promise<void>;
  deleteUser(identity: WorkOSIdentity): Promise<void>;
};

/** Where and as whom Food tools run. */
export type FoodSession = {
  url: string;
  token: string;
  user: string;
  db: SupabaseClient;
  secret: Keyring;
  generation: number;
  approvedWrites: boolean;
};

/** Runs [task] against the user's Food MCP session. */
export type FoodRunner = <T>(
  session: FoodSession,
  task: (call: ToolCall) => Promise<T>,
) => Promise<T>;

/** Everything outside the handler, injectable for tests. */
export type Deps = {
  env: (name: string) => string | undefined;
  database: (url: string, serviceKey: string) => SupabaseClient;
  auth: () => Auth;
  network: typeof fetch;
  food: FoodRunner;
  log: (entry: Json) => void;
  requestId: () => string;
};

/** Chosen when Swiggy's selected address was saved; speeds up finding its label. */
export type AddressSelection = { id: string; page?: number };

export function parseAddressSelection(value: string): AddressSelection {
  // Earlier rows hold the bare address ID.
  if (!value.startsWith("{")) return { id: value };
  const parsed = JSON.parse(value);
  if (typeof parsed.id !== "string" || !parsed.id) {
    throw new Error("Invalid address selection");
  }
  return {
    id: parsed.id,
    page: Number.isInteger(parsed.page) && parsed.page > 0
      ? parsed.page
      : undefined,
  };
}

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json",
      "Cache-Control": "no-store",
    },
  });
// Shared Supabase hosts rewrite HTML as plain text; never expose SVG markup there.
const brokerMessage = (message: string) =>
  new Response(message, {
    headers: {
      "Content-Type": "text/plain; charset=utf-8",
      "Cache-Control": "no-store",
      "Referrer-Policy": "no-referrer",
      "Content-Security-Policy": "default-src 'none'; frame-ancestors 'none'",
    },
  });
const page = (body: string) =>
  new Response(body, {
    headers: {
      "Content-Type": "text/html",
      "Cache-Control": "no-store",
      "Referrer-Policy": "no-referrer",
      "Content-Security-Policy":
        "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'",
    },
  });
const text = (value: unknown, limit = 150): string => {
  if (typeof value !== "string" || !value.trim() || value.length > limit) {
    throw new HungiiError("HUNGII_BAD_INPUT", "Please check this input.");
  }
  return value.trim();
};
const pageNumber = (value: unknown) => {
  if (value === undefined) return 1;
  if (!Number.isInteger(value) || Number(value) < 1 || Number(value) > 100) {
    throw new HungiiError("HUNGII_BAD_INPUT", "Invalid address page.");
  }
  return Number(value);
};
const check = (error: unknown) => {
  if (error) {
    throw new HungiiError(
      "HUNGII_STORAGE",
      "Hungii could not save this update. Try again.",
      503,
    );
  }
};

/** Request-scoped state shared by the routes. */
type Context = {
  deps: Deps;
  db: SupabaseClient;
  secret: Keyring;
  url: URL;
  required: (name: string) => string;
};
type Account = Context & {
  identity: WorkOSIdentity;
  user: string;
  action: string;
  body: Json;
};
type Route<C> = (ctx: C) => Response | Promise<Response>;
type Provider = Account & {
  connection: Json | null;
  connected: boolean;
  address: AddressSelection | null;
  checkoutStore: CheckoutStore;
};

function authBase(ctx: Context): string {
  const base = new URL(ctx.required("SWIGGY_AUTH_BASE_URL"));
  if (
    base.protocol !== "https:" || !SWIGGY_HOSTS.includes(base.hostname) ||
    base.pathname !== "/"
  ) {
    throw new HungiiError(
      "HUNGII_CONFIG",
      "Invalid Swiggy authorization configuration.",
      503,
    );
  }
  return base.origin;
}

function redirect(ctx: Context): string {
  const url = new URL(ctx.required("SWIGGY_REDIRECT_URI"));
  const project = new URL(ctx.required("SUPABASE_URL"));
  if (
    url.protocol !== "https:" || url.hostname !== project.hostname ||
    url.pathname !== "/functions/v1/hungii-api/callback" ||
    url.search !== "?forceFunctionRegion=ap-south-1"
  ) {
    throw new HungiiError(
      "HUNGII_CONFIG",
      "An approved Mumbai HTTPS callback is required.",
      503,
    );
  }
  return url.href;
}

async function upstreamJson(
  ctx: Context,
  url: string,
  body: Json,
  bearer?: string,
): Promise<Json> {
  const response = await ctx.deps.network(url, {
    method: "POST",
    redirect: "error",
    headers: {
      "Content-Type": "application/json",
      ...(bearer ? { Authorization: `Bearer ${bearer}` } : {}),
    },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(10_000),
  });
  if (!response.ok) {
    await response.body?.cancel().catch(() => {});
    throw new HungiiError(
      "HUNGII_AUTH_FAILED",
      "Swiggy authorization did not complete. Please try connecting again.",
      502,
    );
  }
  return await response.json();
}

// Browser routes: the WorkOS sign-out return page and the Swiggy OAuth callback.

function welcome(ctx: Context): Response {
  if (ctx.url.hostname.endsWith(".supabase.co")) {
    return brokerMessage(
      "Hungii\n\nYour next meal, made easy.\nYou can close this tab and return to the Hungii app.",
    );
  }
  return page(
    '<!doctype html><meta name="viewport" content="width=device-width"><title>Hungii</title><style>body{background:#09090c;color:#fff2f5;font:20px system-ui;padding:40px;max-width:600px}h1{color:#ff526f}</style><h1>' +
      BRAND_WORDMARK +
      "</h1><p>Budget-aware meal planning for your day.</p><p>You can close this browser tab and return to the Hungii Android app.</p>",
  );
}

async function swiggyCallback(ctx: Context): Promise<Response> {
  const { db, url, secret } = ctx;
  const state = text(url.searchParams.get("state"), 200);
  const { data, error } = await db.rpc("consume_swiggy_oauth_state", {
    state_digest: await digest(state),
  });
  check(error);
  const pending = data?.[0];
  if (!pending) {
    throw new HungiiError(
      "HUNGII_CALLBACK_EXPIRED",
      "This connection link expired or was already used. Connect again.",
    );
  }
  if (url.searchParams.has("error")) {
    throw new HungiiError(
      "HUNGII_AUTH_CANCELLED",
      "Swiggy authorization was cancelled.",
    );
  }
  const verifier = await unseal(
    pending.encrypted_verifier,
    `${pending.user_id}:pkce`,
    secret,
  );
  const token = await upstreamJson(ctx, `${authBase(ctx)}/auth/token`, {
    grant_type: "authorization_code",
    client_id: ctx.required("SWIGGY_CLIENT_ID"),
    code: text(url.searchParams.get("code"), 2048),
    code_verifier: verifier,
    redirect_uri: redirect(ctx),
  });
  if (
    typeof token.access_token !== "string" ||
    !Number.isFinite(token.expires_in) || token.expires_in <= 0
  ) {
    throw new HungiiError(
      "HUNGII_AUTH_FAILED",
      "Swiggy did not return a valid session.",
      502,
    );
  }
  if (pending.consent_version !== PRIVACY_VERSION) {
    throw new HungiiError(
      "HUNGII_CONSENT",
      "Read the current privacy notice and connect again.",
    );
  }
  const stored = await db.rpc("store_swiggy_connection", {
    owner_id: pending.user_id,
    expected_generation: pending.generation,
    token_ciphertext: await seal(
      token.access_token,
      `${pending.user_id}:swiggy`,
      secret,
    ),
    expiry: new Date(
      Date.now() + Math.min(token.expires_in, 5 * 86400) * 1000,
    ).toISOString(),
    privacy_version: pending.consent_version,
    consent_time: pending.consented_at,
  });
  check(stored.error);
  if (!stored.data) {
    await upstreamJson(
      ctx,
      `${authBase(ctx)}/auth/logout`,
      {},
      token.access_token,
    )
      .catch(() => {});
    throw new HungiiError(
      "HUNGII_CONSENT_WITHDRAWN",
      "This connection request was withdrawn. Connect again if needed.",
      409,
    );
  }
  if (url.hostname.endsWith(".supabase.co")) {
    return brokerMessage(
      "Hungii\n\nSwiggy connected.\nReturn to the app to choose your delivery address.",
    );
  }
  return page(
    '<!doctype html><meta name="viewport" content="width=device-width"><title>Hungii connected</title><style>body{background:#09090c;color:#fff2f5;font:20px system-ui;padding:40px}a{color:#ff526f}</style>' +
      BRAND_WORDMARK +
      '<h1>Swiggy connected.</h1><p>Return to Hungii to choose your delivery address.</p><a href="hungii://swiggy-return">Open Hungii</a>',
  );
}

// Account routes: no Swiggy connection needed.

const accountRoutes: Record<string, Route<Account>> = {
  async sign_out(ctx) {
    await ctx.deps.auth().signOut(ctx.identity);
    const back = `${
      ctx.required("SUPABASE_URL")
    }/functions/v1/hungii-api/welcome?forceFunctionRegion=ap-south-1`;
    return json({
      signedOut: true,
      logoutUrl:
        `https://api.workos.com/user_management/sessions/logout?session_id=${
          encodeURIComponent(ctx.identity.sessionId)
        }&return_to=${encodeURIComponent(back)}`,
    });
  },

  async connect(ctx) {
    const { db, user, body, secret } = ctx;
    if (body.consent !== true || body.privacyVersion !== PRIVACY_VERSION) {
      throw new HungiiError(
        "HUNGII_CONSENT",
        "Read the privacy notice before allowing connection retention.",
      );
    }
    const existing = await db.from("swiggy_connections").select("user_id").eq(
      "user_id",
      user,
    ).maybeSingle();
    check(existing.error);
    if (existing.data) {
      throw new HungiiError(
        "HUNGII_ALREADY_CONNECTED",
        "Disconnect the previous Swiggy connection before authorizing a new one.",
        409,
      );
    }
    const state = randomSecret(), verifier = randomSecret();
    const authorization = new URL(`${authBase(ctx)}/auth/authorize`);
    const callback = redirect(ctx), clientId = ctx.required("SWIGGY_CLIENT_ID");
    const stored = await db.rpc("begin_swiggy_consent", {
      owner_id: user,
      state_digest: await digest(state),
      verifier_ciphertext: await seal(verifier, `${user}:pkce`, secret),
      privacy_version: PRIVACY_VERSION,
    });
    check(stored.error);
    authorization.search = new URLSearchParams({
      response_type: "code",
      client_id: clientId,
      redirect_uri: callback,
      code_challenge: await digest(verifier),
      code_challenge_method: "S256",
      state,
      scope: "mcp:tools",
    }).toString();
    return json({ authorizationUrl: authorization.href });
  },

  // Only user-authored tracker state; no whole Swiggy payloads or tokens in this table.
  async state_get({ db, user, secret }) {
    const stored = await db.from("hungii_state").select("state,updated_at").eq(
      "user_id",
      user,
    ).maybeSingle();
    check(stored.error);
    const encrypted = stored.data?.state?.ciphertext;
    return json({
      state: encrypted
        ? trackerState(
          JSON.parse(await unseal(encrypted, `${user}:tracker`, secret)),
        )
        : null,
      updatedAt: stored.data?.updated_at ?? null,
      consentVersion: stored.data?.state?.consentVersion ?? null,
    });
  },

  async state_save({ db, user, body, secret }) {
    if (body.privacyVersion !== PRIVACY_VERSION) {
      throw new HungiiError(
        "HUNGII_CONSENT",
        "Read the privacy notice before syncing.",
      );
    }
    const normalized = trackerState(body.state);
    const expected = body.expectedUpdatedAt ?? null;
    if (
      expected !== null &&
      (typeof expected !== "string" || !Number.isFinite(Date.parse(expected)))
    ) {
      throw new HungiiError("HUNGII_BAD_INPUT", "Invalid sync revision.");
    }
    const stored = await db.rpc("save_hungii_state", {
      owner_id: user,
      encrypted_state: {
        ciphertext: await seal(
          JSON.stringify(normalized),
          `${user}:tracker`,
          secret,
        ),
        consentVersion: PRIVACY_VERSION,
      },
      expected_update: expected,
    });
    check(stored.error);
    if (!stored.data?.[0]?.saved) {
      throw new HungiiError(
        "HUNGII_SYNC_CONFLICT",
        "Your cloud profile changed on another device. Choose which copy to keep.",
        409,
      );
    }
    return json({ saved: true, updatedAt: stored.data[0].updated_at });
  },
};

// Routes that read the Swiggy connection but do not call Food tools.

/** Ends the provider session and token. [session] is read before withdrawal erases it. */
async function endRemoteSession(
  ctx: Provider,
  session: { data: Json | null },
): Promise<boolean> {
  const { user, secret, connection } = ctx;
  if (!connection) return true;
  const cooling = session.data?.cooldown_until &&
    Date.parse(session.data.cooldown_until) > Date.now();
  // Provider rejection or cooldown forbids a further remote attempt.
  if (session.data?.phase === "blocked" || cooling) return false;
  try {
    const token = await unseal(
      connection.encrypted_token,
      `${user}:swiggy`,
      secret,
    );
    if (session.data?.encrypted_metadata) {
      try {
        const metadata = JSON.parse(
          await unseal(
            session.data.encrypted_metadata,
            `${user}:mcp:${session.data.generation}`,
            secret,
          ),
        );
        const endpoint = new URL(ctx.required("SWIGGY_FOOD_URL"));
        if (
          endpoint.protocol !== "https:" ||
          !SWIGGY_HOSTS.includes(endpoint.hostname) ||
          endpoint.pathname !== "/food"
        ) throw new Error("Invalid endpoint");
        const ended = await ctx.deps.network(endpoint, {
          method: "DELETE",
          redirect: "error",
          signal: AbortSignal.timeout(8000),
          headers: {
            Authorization: `Bearer ${token}`,
            "Mcp-Session-Id": metadata.sessionId,
            "MCP-Protocol-Version": metadata.protocol,
          },
        });
        await ended.body?.cancel().catch(() => {});
        if ([401, 419, 403, 429].includes(ended.status)) {
          throw new HungiiError(
            "HUNGII_REVOCATION_UNCONFIRMED",
            "Remote revocation could not be confirmed.",
          );
        }
      } catch (error) {
        if (error instanceof HungiiError) throw error;
      }
    }
    await upstreamJson(ctx, `${authBase(ctx)}/auth/logout`, {}, token);
    return true;
  } catch {
    // Withdrawal still erases local provider data.
    return false;
  }
}

async function erase(ctx: Provider): Promise<Response> {
  const { db, user, action, body } = ctx;
  if (body.confirm !== true) {
    throw new HungiiError(
      "HUNGII_CONFIRM_REQUIRED",
      "Confirm deletion first.",
    );
  }
  if (action === "delete_cloud_tracker") {
    const removed = await db.from("hungii_state").delete().eq("user_id", user);
    check(removed.error);
    return json({ deleted: true });
  }
  const session = await db.from("swiggy_mcp_sessions").select(
    "encrypted_metadata,generation,phase,cooldown_until",
  ).eq("user_id", user).maybeSingle();
  check(session.error);
  if (action === "delete_account") {
    const erased = await db.rpc("begin_hungii_account_deletion", {
      owner_id: user,
    });
    check(erased.error);
    if (!erased.data) {
      throw new HungiiError(
        "HUNGII_ACCOUNT_DELETING",
        "This account has already been erased.",
        409,
      );
    }
  } else {
    const withdrawn = await db.rpc("withdraw_swiggy_consent", {
      owner_id: user,
    });
    check(withdrawn.error);
  }
  const revoked = await endRemoteSession(ctx, session);
  if (action === "delete_account") {
    try {
      await ctx.deps.auth().deleteUser(ctx.identity);
    } catch {
      return json({
        disconnected: true,
        deleted: false,
        accountDeletionPending: true,
        revocationConfirmed: revoked,
      });
    }
    const finished = await db.from("hungii_accounts").update({
      status: "deleted",
      delete_after: new Date(Date.now() + 86400000).toISOString(),
    }).eq("id", user).eq("status", "deleting");
    check(finished.error);
  }
  return json({
    disconnected: true,
    deleted: action === "delete_account",
    revocationConfirmed: revoked,
  });
}

const providerRoutes: Record<string, Route<Provider>> = {
  async checkout_resume({ checkoutStore }) {
    const attempt = await checkoutStore.latest();
    return json(attempt ? attemptView(attempt) : { payment: null });
  },

  status({ deps, user, connected, connection, address }) {
    const food = deps.env("SWIGGY_FOOD_URL");
    const ordering = orderingConfig(deps.env);
    return json({
      accountId: user,
      privacyVersion: PRIVACY_VERSION,
      connected,
      addressId: address?.id ?? null,
      expiresAt: connected ? connection?.expires_at : null,
      environment: food?.includes("mcp-staging")
        ? "staging"
        : food
        ? "production"
        : "not-configured",
      cartWritesEnabled: ordering.enabled,
      orderingEnabled: ordering.enabled,
      connectionAvailable: Boolean(
        deps.env("SWIGGY_CLIENT_ID") && deps.env("SWIGGY_AUTH_BASE_URL") &&
          food,
      ),
      priceUnitVerified: ["rupees", "paise"].includes(
        deps.env("SWIGGY_PRICE_UNIT") ?? "",
      ),
    });
  },

  disconnect: erase,
  delete_account: erase,
  delete_cloud_tracker: erase,
};

// Food routes run inside the user's MCP session.

const ORDERING_ACTIONS = [
  "cart_create",
  "cart_quantity",
  "cart_coupon",
  "payment_options",
  "checkout",
  "payment_status",
  "order",
];

async function foodRoute(
  ctx: Provider,
  call: ToolCall,
): Promise<Response> {
  const { db, user, body, action, secret, connection, address, deps } = ctx;
  const unit = deps.env("SWIGGY_PRICE_UNIT") ?? "unverified";
  const ordering = orderingConfig(deps.env);
  if (action === "addresses" || action === "select_address") {
    const requested = pageNumber(body.page);
    const data = await call("get_addresses", {
      page: requested,
      pageSize: ADDRESS_PAGE_SIZE,
    });
    if (action === "select_address") {
      const addressId = text(body.addressId);
      if (!data.addresses?.some((a: Json) => a.id === addressId)) {
        throw new HungiiError(
          "HUNGII_ADDRESS",
          "Choose an address from the current list.",
        );
      }
      // The page is a lookup hint only; the address itself stays encrypted.
      const selection: AddressSelection = { id: addressId, page: requested };
      const selected = await db.from("swiggy_connections").update({
        encrypted_address_id: await seal(
          JSON.stringify(selection),
          `${user}:address`,
          secret,
        ),
      }).eq("user_id", user).eq("generation", connection!.generation).select(
        "user_id",
      ).maybeSingle();
      check(selected.error);
      if (!selected.data) {
        throw new HungiiError(
          "HUNGII_CONNECTION_CHANGED",
          "The connection changed. Select the address again.",
          409,
        );
      }
      return json({ addressId });
    }
    return json({
      addresses: (data.addresses ?? []).map((a: Json) => ({
        id: a.id,
        label: a.addressTag ?? a.addressCategory ?? "Delivery address",
        addressLine: a.addressLine,
      })),
      pagination: data.pagination,
    });
  }
  if (!address) {
    throw new HungiiError(
      "HUNGII_ADDRESS_REQUIRED",
      "Choose your delivery address first.",
      409,
    );
  }
  const addressId = address.id;
  if (action === "discover") {
    let offset: number | undefined;
    if (body.offset !== undefined) {
      if (!Number.isFinite(body.offset) || body.offset < 0) {
        throw new HungiiError("HUNGII_BAD_INPUT", "Invalid result page.");
      }
      offset = body.offset;
    }
    return json(
      discovery(
        await discover(
          call,
          addressId,
          text(body.query),
          body.collection,
          offset,
        ),
        unit,
      ),
    );
  }
  if (action === "menu") {
    return json(
      menuView(
        await call("search_menu", {
          addressId,
          query: text(body.query),
          restaurantIdOfAddedItem: text(body.restaurantId),
          ...(body.vegOnly === true ? { vegFilter: 1 } : {}),
        }),
        unit,
      ),
    );
  }
  if (action === "restaurant_menu") {
    return json(
      await call("get_restaurant_menu", {
        addressId,
        restaurantId: text(body.restaurantId),
      }),
    );
  }
  if (
    ORDERING_ACTIONS.includes(action) || action === "cart" && ordering.enabled
  ) {
    return json(
      await new FoodOrdering(
        call,
        ctx.checkoutStore,
        ordering,
        user,
        addressId,
        secret,
        Date.now,
        address.page,
      ).run(action, body),
    );
  }
  if (action === "cart") {
    return json(cartView(await call("get_food_cart", { addressId }), unit));
  }
  if (action === "coupons") {
    const data = await call("fetch_food_coupons", {
      addressId,
      restaurantId: text(body.restaurantId),
    });
    return json({
      sections: data.coupon_sections ?? [],
      summary: data.summary ?? null,
    });
  }
  throw new HungiiError(
    "HUNGII_ACTION",
    "This action is not enabled in this integration.",
    409,
  );
}

async function accountRequest(
  ctx: Context,
  request: Request,
): Promise<Response> {
  const { db, deps } = ctx;
  const body = await readJsonObject(request, 100_000);
  if (ctx.url.pathname.endsWith("/auth/refresh")) {
    return json(await deps.auth().refresh(text(body.refresh_token, 8192)));
  }
  const bearer = request.headers.get("Authorization")?.match(
    /^Bearer (.+)$/,
  )?.[1];
  if (!bearer) {
    throw new HungiiError(
      "HUNGII_LOGIN_REQUIRED",
      "Please sign in to Hungii.",
      401,
    );
  }
  const identity = await deps.auth().verify(bearer);
  const action = text(body.action);
  const account = await db.rpc("resolve_hungii_account", {
    subject_hash: await digest(
      `${ctx.required("WORKOS_CLIENT_ID")}:${identity.subject}`,
    ),
  });
  check(account.error);
  const owned = account.data?.[0];
  if (
    !owned || owned.status === "deleted" ||
    (owned.status === "deleting" && action !== "delete_account")
  ) {
    throw new HungiiError(
      "HUNGII_ACCOUNT_DELETING",
      "Account deletion is pending. Retry deletion from Accounts.",
      409,
    );
  }
  const purged = await db.rpc("purge_hungii_expired");
  check(purged.error);
  const accountCtx: Account = {
    ...ctx,
    identity,
    user: owned.id,
    action,
    body,
  };
  const accountRoute = accountRoutes[action];
  if (accountRoute) return await accountRoute(accountCtx);

  const user = owned.id;
  const { data: connection, error } = await db.from("swiggy_connections")
    .select("*").eq("user_id", user).maybeSingle();
  check(error);
  const connected = Boolean(
    connection && connection.consent_version === PRIVACY_VERSION &&
      Date.parse(connection.expires_at) > Date.now() + 60_000,
  );
  const address = connected && connection.encrypted_address_id
    ? parseAddressSelection(
      await unseal(
        connection.encrypted_address_id,
        `${user}:address`,
        ctx.secret,
      ),
    )
    : null;
  const providerCtx: Provider = {
    ...accountCtx,
    connection,
    connected,
    address,
    checkoutStore: new CheckoutStore(db, user, ctx.secret),
  };
  const providerRoute = providerRoutes[action];
  if (providerRoute) return await providerRoute(providerCtx);

  if (!connected) {
    throw new HungiiError(
      "HUNGII_RECONNECT",
      "Connect your Swiggy account to find meals.",
      401,
    );
  }
  if (deps.env("SWIGGY_SESSION_RESUME_VERIFIED") !== "true") {
    throw new HungiiError(
      "HUNGII_STAGING_REQUIRED",
      "Live Food access awaits staging verification of session reuse.",
      503,
    );
  }
  const token = await unseal(
    connection.encrypted_token,
    `${user}:swiggy`,
    ctx.secret,
  );
  return await deps.food({
    url: ctx.required("SWIGGY_FOOD_URL"),
    token,
    user,
    db,
    secret: ctx.secret,
    generation: connection.generation,
    approvedWrites: orderingConfig(deps.env).enabled,
  }, (call) => foodRoute(providerCtx, call));
}

/** The first frame of a stack trace locates a failure without logging any data. */
function origin(error: unknown): string | null {
  if (!(error instanceof Error)) return null;
  return error.stack?.split("\n").slice(1).find((line) =>
    line.includes("/functions/")
  )?.trim().replace(/^at /, "") ?? null;
}

export function createHandler(deps: Deps) {
  return async function handler(request: Request): Promise<Response> {
    const requestId = deps.requestId();
    let response: Response;
    try {
      const required = (name: string): string => {
        const value = deps.env(name);
        if (!value) {
          throw new HungiiError(
            "HUNGII_SETUP_REQUIRED",
            "Hungii's connection is not set up yet.",
            503,
          );
        }
        return value;
      };
      if (
        deps.env("SB_REGION") !== "ap-south-1" &&
        deps.env("HUNGII_LOCAL_DEVELOPMENT") !== "true"
      ) {
        throw new HungiiError(
          "HUNGII_REGION",
          "This service must run in Mumbai.",
          503,
        );
      }
      const db = deps.database(
        required("SUPABASE_URL"),
        required("SUPABASE_SERVICE_ROLE_KEY"),
      );
      const secret = keyringFromEnv(deps.env);
      if (!secret) required("HUNGII_TOKEN_KEY");
      const ctx: Context = {
        deps,
        db,
        secret: secret!,
        url: new URL(request.url),
        required,
      };
      if (request.method === "GET" && ctx.url.pathname.endsWith("/welcome")) {
        response = welcome(ctx);
      } else if (
        request.method === "GET" && ctx.url.pathname.endsWith("/callback")
      ) {
        response = await swiggyCallback(ctx);
      } else if (request.method !== "POST") {
        response = json({
          error: {
            code: "HUNGII_METHOD",
            message: "Use the Hungii app to connect.",
          },
        }, 405);
      } else {
        response = await accountRequest(ctx, request);
      }
    } catch (error) {
      const safe = error instanceof HungiiError ? error : new HungiiError(
        "HUNGII_UNAVAILABLE",
        "Hungii could not complete this request. Please retry.",
        503,
      );
      // Never log upstream messages, tokens, addresses or request bodies.
      if (!(error instanceof HungiiError)) {
        deps.log({
          event: "unhandled_error",
          request_id: requestId,
          error: error instanceof Error ? error.name : typeof error,
          origin: origin(error),
        });
      } else if (safe.status >= 500) {
        deps.log({
          event: "request_failed",
          request_id: requestId,
          code: safe.code,
          status: safe.status,
        });
      }
      response = json({
        error: {
          code: safe.code,
          message: safe.message,
          retryAfter: safe.retryAfter ?? null,
          requestId,
        },
      }, safe.status);
      if (safe.retryAfter) {
        response.headers.set("Retry-After", String(safe.retryAfter));
      }
    }
    response.headers.set("X-Request-Id", requestId);
    return response;
  };
}

export const defaultDeps: Deps = {
  env: (name) => Deno.env.get(name),
  database: (url, key) =>
    createClient(url, key, {
      auth: { persistSession: false, autoRefreshToken: false },
    }),
  auth: workosAuth,
  network: (input, init) => fetch(input, init),
  food: (session, task) =>
    withFood(
      session.url,
      session.token,
      task,
      session.user,
      false,
      new DurableFoodSessions(session.db, session.secret, session.generation),
      session.approvedWrites,
    ),
  log: (entry) => console.error(JSON.stringify(entry)),
  requestId: () => crypto.randomUUID(),
};

export const handler = createHandler(defaultDeps);
