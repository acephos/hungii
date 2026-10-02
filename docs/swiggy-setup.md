# Hungii service setup

Current project: **hungii-mumbai**, reference `qvqnzsqrxejdlvnbqwcs`, Supabase **Free**, specific Mumbai `ap-south-1`. GitHub dashboard sign-in is complete. [Dashboard](https://supabase.com/dashboard/project/qvqnzsqrxejdlvnbqwcs), [current validation evidence](approval-readiness.md).

## Already provisioned

- Four SQL migrations applied; backend-only RLS tables, encrypted tracker/token/verifier/address/session fields, versioned consent, callback generation fencing and durable session leases.
- `hungii-api` deployed with public callback support and Auth.getUser on every API action. Actual Mumbai execution header verified. API/root without an account returns login required.
- `HUNGII_TOKEN_KEY` generated and installed as a server secret. Provisioning credentials are in a private, gitignored `supabase/.env.provisioning`; never paste them into chat, Android, a public repo or the draft.
- Swiggy session-resumption gate false; price unit unverified. No Swiggy client or auth host set until Builders supplies approved configuration.
- Cron purge every ten minutes, with successful execution verified; access also purges expiry. Free inactivity pausing constrains scheduled cleanup and availability.
- Native project URL/publishable key configured in private `android-prototype/local.properties`. `hungii.supabaseAuthReady=false` keeps Google login visibly pending.

## Google user login still needs an OAuth client

Supabase dashboard login via GitHub is independent of Hungii users signing in with Google. In the founder's Google Cloud project, create an OAuth web client and configure the consent screen. Authorized provider redirect: `https://qvqnzsqrxejdlvnbqwcs.supabase.co/auth/v1/callback`. Install the Google client ID/secret in Supabase Auth's Google settings; do not place the client secret in Android or chat. App redirect allowlist is scoped to `hungii://auth-return?flow=*` so the app can validate its random login-flow nonce. Verify real phone/browser PKCE, cancellation and replay before setting `hungii.supabaseAuthReady=true` and rebuilding. Production verified app links, consent-screen verification and distribution need separate validation. [Google provider](https://supabase.com/docs/guides/auth/social-login/auth-google), [redirect URLs](https://supabase.com/docs/guides/auth/redirect-urls).

## Swiggy staging remains approval-gated

Request **Food-only seeded staging**. Swiggy supplies reviewed access and test-account instructions; our local synthetic MCP demo is not a Swiggy sandbox. Do not infer production credentials work on staging or fabricate test tokens. [Testing options](swiggy-testing-options.md), [access](https://mcp.swiggy.com/builders/docs/operate/access.md).

Proposed hosted exact callback:

```text
https://qvqnzsqrxejdlvnbqwcs.supabase.co/functions/v1/hungii-api/callback?forceFunctionRegion=ap-south-1
```

Obtain written confirmation/registration of the full URI, staging OAuth hosts, DCR client, seeded accounts, permissible personalized meal sorting/offer optimization/favorites retention, logical-session reuse across Edge workers and any egress requirements. Configure verified `SWIGGY_AUTH_BASE_URL`, `SWIGGY_FOOD_URL`, `SWIGGY_CLIENT_ID` and callback as server secrets. Swiggy phone/OTP entry happens only in its own browser UI. [Authentication](https://mcp.swiggy.com/builders/docs/start/authenticate.md).

Run staging checks for real tools/list, output shapes, price units, stock races, address pagination, tool/HTTP errors, cooldowns, rejected credentials, callback replay/expiry/cancellation and logout. Only set `SWIGGY_SESSION_RESUME_VERIFIED=true` after cross-worker session persistence and request accounting pass against Swiggy and are accepted by the provider. No paid runtime is enabled as a fallback. See [runtime decision](adr/0002-durable-mumbai-mcp-session.md) and [sources](approval-technical-sources.md).

Native API calls and callback force Mumbai; the handler rejects other execution regions. Do not set `HUNGII_LOCAL_DEVELOPMENT` on hosted functions. Mumbai primary data/runtime checks do not certify every provider/subprocessor/egress path. [Regional invocation](https://supabase.com/docs/guides/functions/regional-invocation).

Cart writes, coupon application, order placement and payment are disabled. The existing-cart read does not quote a selected winner. Enable no write before authenticated customization/cartItems/price/coupon/payment contracts, explicit user confirmation, final payable and non-idempotent retry safeguards are verified. Nutrition estimates require independently sourced/calibrated portion data; no invented macros. [Integration contract](swiggy-integration-contract.md).

## Local checks

```sh
npx --yes deno check --config supabase/functions/deno.json supabase/functions/hungii-api/index.ts
npx --yes deno test --config supabase/functions/deno.json --allow-env supabase/functions/_shared/
```

The SQL test in `supabase/tests/privacy-and-leases.sql` uses rolled-back synthetic accounts on Hungii's own project. Never run it against a different production database. The `demo` Android flavor reaches a local read-only MCP server; the `real` flavor never substitutes fixtures. [Android build/demo guide](../android-prototype/README.md).

Before production: complete [external gates](approval-readiness.md#external-gates-before-production), including approved agreements/privacy contact, at least 48 hours green staging, explicit production access, support/alerts/recovery and staged rollout. A staging request can describe prepared controls; it cannot assert production approval.
