# Connect Hungii to real services

Selected stack: Kotlin/Compose, Room, Supabase Auth/Postgres in Mumbai, and a TypeScript Food MCP adapter in Mumbai Edge Functions. WorkOS and Convex are not dependencies. The code and disconnected APK are prepared; neither external service has been provisioned or deployed.

## 1. Supabase project

Create a project in the specific **South Asia (Mumbai)** region. Record its project reference, HTTPS URL and publishable key. Android only needs the URL and public key; service-role keys and Swiggy tokens stay on the server. [Regions](https://supabase.com/docs/guides/platform/regions), [secrets](https://supabase.com/docs/guides/functions/secrets)

Connect the Supabase integration to this workspace once the project exists, or use the CLI. Check the project reference before applying the migration:

```sh
npx supabase login
npx supabase link --project-ref YOUR_PROJECT_REFERENCE
npx supabase db push
```

The migration creates an RLS-protected tracker table and server-only connection/OAuth-state tables. Tokens/verifiers use a separate encryption secret, and callback state is consumed once. Whole tool responses are not stored. Database deployment is not yet tested against a real project. [Deployment](https://supabase.com/docs/guides/functions/deploy)

## 2. Hungii login

Enable Google in Supabase Auth using a Google OAuth client and the provider's setup guide. Google redirects to Supabase; Supabase returns to Android. Allow `hungii://auth-return` with its random `flow` query parameter in Supabase's redirect allowlist, using documented wildcard support for the changing query. The native app validates its nonce, expiration and PKCE verifier. [Google setup](https://supabase.com/docs/guides/auth/social-login/auth-google), [redirect allowlist](https://supabase.com/docs/guides/auth/redirect-urls), [PKCE](https://supabase.com/docs/guides/auth/sessions/pkce-flow), [REST contract](https://github.com/supabase/auth/blob/master/openapi.yaml)

## 3. Swiggy Builders access

Swiggy documents a staging environment at `mcp-staging.swiggy.com/{server}` with the same interface as production, seeded data and no real orders. The access page says staging credentials are issued during application review. Before receiving access, it recommends building against a local development stub. The reviewed docs do not link an official downloadable stub. The quickstart's broader wording about starting without approval does not establish anonymous access to hosted staging. [Testing options](swiggy-testing-options.md), [access](https://mcp.swiggy.com/builders/docs/operate/access.md), [quickstart](https://mcp.swiggy.com/builders/docs/start/developer/index.md)

Apply with Hungii's use case, Food scope, expected traffic, technical contact and demo. Request staging access and exact allowlisting of this complete callback:

```text
https://YOUR_PROJECT.supabase.co/functions/v1/hungii-api/callback?forceFunctionRegion=ap-south-1
```

Obtain/register the OAuth client through Swiggy's documented Dynamic Client Registration and approved callback process. The app's custom scheme is only used after the successful server callback; it is not Swiggy's redirect URI. There is no static Swiggy API key, third-party phone/OTP endpoint or shared account token. [Access](https://mcp.swiggy.com/builders/docs/operate/access.md), [authentication](https://mcp.swiggy.com/builders/docs/start/authenticate.md)

Ask Swiggy to confirm the staging authorization base and Food endpoint. The documented staging Food convention is `https://mcp-staging.swiggy.com/food`; do not guess staging OAuth endpoints. Verify menu/cart money units against approved responses. Obtain authenticated `tools/list` schemas defining `cartItems` members before enabling cart writes. Do not assume coupon IDs are coupon codes. [Verified integration contract](swiggy-integration-contract.md)

## 4. Backend configuration

Copy [supabase/.env.example](../supabase/.env.example) to gitignored `supabase/.env`. Fill approved endpoints, client ID and exact callback. Generate a random 32-byte base64 key locally for `HUNGII_TOKEN_KEY`; never put it into Android configuration or chat. Leave `SWIGGY_PRICE_UNIT=unverified` until staging confirms all relevant units; verified `rupees` or `paise` controls explicit conversion, with no heuristic division by 100.

```sh
chmod 600 supabase/.env
npx supabase secrets set --env-file supabase/.env
npx supabase functions deploy hungii-api
```

Gateway JWT verification is disabled for the public OAuth callback. Every API action separately verifies its bearer session using Supabase Auth. The handler refuses non-Mumbai execution; requests and callback specify `forceFunctionRegion=ap-south-1`. Do not set `HUNGII_LOCAL_DEVELOPMENT` on a deployed function. A Mumbai database alone does not pin functions or establish compliance. [Function authentication](https://supabase.com/docs/guides/functions/auth), [regional invocation](https://supabase.com/docs/guides/functions/regional-invocation)

## 5. Android configuration

Add these public values to existing gitignored `android-prototype/local.properties`, preserving `sdk.dir`:

```properties
hungii.supabaseUrl=https://YOUR_PROJECT.supabase.co
hungii.supabasePublishableKey=YOUR_PUBLIC_PUBLISHABLE_KEY
```

Build with JDK 17 and SDK 35:

```sh
cd android-prototype
./gradlew assembleDebug lintDebug
```

Install `app/build/outputs/apk/debug/app-debug.apk`. In Accounts: Google login → consent to secure connection retention → Swiggy browser authorization → return and refresh → explicit address selection. Search meals, shortlist three, shuffle and pick. Review reads the existing live cart and offers without changing them; finish the basket/payment in Swiggy.

## Implemented and pending

Implemented: official MCP streamable HTTP client, per-user encrypted credentials, runtime input-schema checks, address pagination/selection, open-restaurant dish discovery, selected-restaurant menu search, returned menu photos/diet flags/available prices/ETA/distance, current-cart totals and coupon descriptions. A suggested coupon with zero discount is not called applied. Room retains user-entered totals and consented favorites; tracker sync is explicit. Hungii sign-out and Swiggy disconnect are separate.

The adapter is read-only. Cart writes/order placement are disabled until authenticated item schemas are verified. The winner is not added to a basket; checkout hands off to Swiggy. Exact hypothetical basket quotes and automatic coupon threshold optimization are not available in this integration.

Reviewed schemas do not publish calories/macros. These stay unknown, not invented ranges; manual intake entry works. A sourced/calibrated estimate layer, conversational AI, history-derived favorites, payment polling and cross-device tracker conflict resolution remain separate work.

Before live use, verify two-user isolation/RLS, callback replay/expiry/cancellation, expired/revoked tokens, address pagination, optional fields, staging price units, stock changes and 429 handling. Local tests cover normalization/encryption and disconnected Android behaviour; authenticated requests and Google login require the accounts above. Permitted retention and production approval still apply. [Provider requirements](https://mcp.swiggy.com/builders/docs/operate/data-and-compliance.md)
