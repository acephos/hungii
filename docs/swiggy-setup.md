# Hungii service setup

Real-service setup, separate from the [synthetic Simulator](../demo/README.md). Provisioned project: **hungii-mumbai**, reference `qvqnzsqrxejdlvnbqwcs`, Supabase **Free**, Mumbai `ap-south-1`. Deployment checks were recorded on 2 October 2026. [Dashboard](https://supabase.com/dashboard/project/qvqnzsqrxejdlvnbqwcs), [current status and evidence](approval-readiness.md).

## Already provisioned

- Six SQL migrations applied, including backend-only Hungii accounts, ownership/deletion fences, encrypted tracker/provider fields, durable MCP leases and scheduled expiry cleanup.
- `hungii-api` verifies signed WorkOS JWTs and live sessions for account actions. Android/callbacks force Mumbai; the handler rejects other regions. Swiggy callback and WorkOS refresh are separately scoped public endpoints.
- WorkOS staging application **Hungii Android**, client `client_01M3XWXNG4XE1J3C7NP76HBJKC`; Magic Auth email codes enabled. Android redirect `com.hungii.prototype://auth-return` registered. The free hosted AuthKit domain is used. Email/password also remains available in staging.
- WorkOS API key, client ID and exact JWT issuer installed as server secrets. The issuer references the environment's default application, `client_01M3XWQW4SPGCDAVZ551TTSY5S`, as documented by WorkOS. No secret enters Android.
- `HUNGII_TOKEN_KEY` installed. Provisioning files are gitignored and private; do not paste them into chat, APKs or review drafts.
- `SWIGGY_SESSION_RESUME_VERIFIED=false`, price unit unverified, no approved Swiggy client/endpoints installed.
- Cron purge every ten minutes plus access cleanup. Free inactivity pausing constrains cleanup/availability.

## Hungii user sign-in

The opening screen offers Hungii email sign-in or local-only use without an account. Cloud profile/goals/meal-filter sync is optional after sign-in and can be stopped per device; saved provider meal shortcuts remain local. Changes sync automatically only after permission, and conflicting revisions require a choice. The real preview uses WorkOS AuthKit with Supabase Postgres/Edge Functions. Supabase dashboard GitHub login and Hungii user login are independent. No Google OAuth configuration is needed for the selected email login. Android opens AuthKit with PKCE/state, exchanges the public authorization code, then uses the backend to refresh and authorize its session. Revoked sessions fail live checks. [Architecture](adr/0003-workos-with-supabase.md).

Only public values go into gitignored `android-prototype/local.properties`:

```properties
hungii.supabaseUrl=https://qvqnzsqrxejdlvnbqwcs.supabase.co
hungii.supabasePublishableKey=YOUR_PUBLISHABLE_KEY
hungii.workosClientId=client_01M3XWXNG4XE1J3C7NP76HBJKC
hungii.workosAuthReady=true
```

Use Accounts → Continue with email, enter your email, then select Email sign-in code if the password screen appears. WorkOS can send a six-digit email code; no phone OTP is needed for this app login. This is staging authentication, with a custom-scheme Android callback. The founder confirmed successful email-code sign-in on their phone. Configure production AuthKit/release signing/verified App Links before public distribution. The backend owns database authorization; direct Supabase Data API access is disabled, so no third-party Auth/JWT-role template is required by this implementation. [WorkOS](https://supabase.com/docs/guides/auth/third-party/workos).

## Swiggy staging remains approval-gated

Request **Food-only seeded staging**. Swiggy supplies reviewed access and test-account instructions; our local synthetic MCP demo is not a Swiggy sandbox. Do not infer production credentials work on staging or fabricate test tokens. [Testing options](research/swiggy-testing-options.md), [access](https://mcp.swiggy.com/builders/docs/operate/access.md).

Proposed hosted exact callback:

```text
https://qvqnzsqrxejdlvnbqwcs.supabase.co/functions/v1/hungii-api/callback?forceFunctionRegion=ap-south-1
```

Obtain written confirmation/registration of the full URI, staging OAuth hosts, DCR client, seeded accounts, permissible personalized meal sorting/offer optimization/favorites retention, logical-session reuse across Edge workers and any egress requirements. Configure verified `SWIGGY_AUTH_BASE_URL`, `SWIGGY_FOOD_URL`, `SWIGGY_CLIENT_ID` and callback as server secrets. Swiggy phone/OTP entry happens only in its own browser UI. [Authentication](https://mcp.swiggy.com/builders/docs/start/authenticate.md).

Run staging checks for real tools/list, output shapes, price units, stock races, address pagination, tool/HTTP errors, cooldowns, rejected credentials, callback replay/expiry/cancellation and logout. Only set `SWIGGY_SESSION_RESUME_VERIFIED=true` after cross-worker session persistence and request accounting pass against Swiggy and are accepted by the provider. No paid runtime is enabled as a fallback. See [runtime decision](adr/0002-durable-mumbai-mcp-session.md) and [sources](research/approval-technical-sources.md).

Native API calls and callback force Mumbai; the handler rejects other execution regions. Do not set `HUNGII_LOCAL_DEVELOPMENT` on hosted functions. Mumbai primary data/runtime checks do not certify every provider/subprocessor/egress path. [Regional invocation](https://supabase.com/docs/guides/functions/regional-invocation).

Cart writes, coupon application, order placement and payment are disabled. The existing-cart read does not quote a selected winner. Enable no write before authenticated customization/cartItems/price/coupon/payment contracts, explicit user confirmation, final payable and non-idempotent retry safeguards are verified. Nutrition estimates require independently sourced/calibrated portion data; no invented macros. [Integration contract](swiggy-integration-contract.md).

## Local checks

```sh
npx --yes deno@2.9.6 check --config supabase/functions/deno.json supabase/functions/hungii-api/index.ts demo/gateway.ts
npx --yes deno@2.9.6 test --config supabase/functions/deno.json --allow-env supabase/functions/_shared/ demo/simulator_test.ts
```

The SQL tests in `supabase/tests/` include privacy/leases, WorkOS ownership and cloud-profile sync. Their recorded deployed checks used rolled-back synthetic accounts. Configure the intended database explicitly before running them; CI does not mutate a deployed database. The `demo` Android flavor reaches the authored synthetic MCP server with mock cart/payment writes; the `real` flavor never substitutes fixtures. [Android build/demo guide](../android-prototype/README.md).

Before production: complete [external gates](approval-readiness.md#external-gates-before-production), including approved agreements/privacy contact, at least 48 hours green staging, explicit production access, support/alerts/recovery and staged rollout. A staging request can describe prepared controls; it cannot assert production approval.
