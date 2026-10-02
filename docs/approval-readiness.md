# Hungii readiness evidence

Verified 2 October 2026. This is a technical self-assessment with reproducible evidence, not legal certification or Swiggy approval. It supersedes the initial [pre-hardening audit](swiggy-application-compliance.md). No further email or developer form has been sent after the founder's draft-only instruction.

## Provisioned at no charge

- Supabase project **hungii-mumbai**, reference `qvqnzsqrxejdlvnbqwcs`, in the existing Free organization. The primary database reports **ap-south-1 / South Asia (Mumbai)** and healthy Nano compute. No paid plan, add-on, card or external paid runtime was enabled.
- [Project dashboard](https://supabase.com/dashboard/project/qvqnzsqrxejdlvnbqwcs).
- Authenticated Edge API `hungii-api` deployed. Actual HTTP response `x-sb-edge-region: ap-south-1` verified. The handler rejects a different execution region; Android requests force Mumbai.
- Four migrations applied and recorded in Supabase CLI migration history: private tables, consent fields, credential generation/session leases, cleanup indexes and Cron. Five public-schema tables have RLS enabled; both anonymous and authenticated roles lack direct read privileges. Only the backend service authority accesses these tables after Auth.getUser establishes the caller.
- Scheduled purge `hungii-privacy-purge`, every ten minutes, has run with **succeeded** status. API access also purges expired data. Free project inactivity pausing can prevent scheduled cleanup while paused; access cleanup runs on resumption. Production availability/backup arrangements are unresolved on a zero-budget account.
- Google login for Hungii users is **disabled pending an owned Google Cloud OAuth client**. Dashboard GitHub sign-in does not configure app Google login. Anonymous sign-in is disabled.
- Swiggy credentials, staging authorization hosts and price units are not guessed. `SWIGGY_SESSION_RESUME_VERIFIED=false` prevents live Food calls until approved staging verifies session accounting. No provider connections or real orders exist.

## Implemented controls and evidence

| Requirement | Current implementation | Verification and remaining limits |
| --- | --- | --- |
| Per-user ownership | Auth.getUser on each action; body-supplied owner IDs are ignored; backend-only tables | Two actual temporary Supabase accounts: owner reads its own totals; second account cannot read them through a forged target; forged deletion target deletes authenticated self only. Deleted user's still-signed JWT is rejected. Test users and their data were removed. |
| Encryption/minimization | AES-256-GCM for tokens, PKCE verifiers, selected address ID, session metadata and cloud tracker; per-owner Keystore keys for local tracker/shortcuts; account sessions encrypted; Android backup disabled | Cross-owner/tamper encryption tests pass. Emulator database inspection found hashed owner IDs, ciphertext envelopes and no raw tracker JSON in DB/WAL. Native entered allowance survived reopening. Does not independently audit Supabase Auth/subprocessors. |
| Consent and retention | Version `2026-10-02.1`; separate notices for connection retention, cloud sync and local meal shortcuts | Five-day maximum connection/session retention, ten-minute OAuth state, 90-day inactive cloud tracker, 30-day local shortcuts. Saved data minimizes provider descriptors and drops photos/prices/offer text. Lawful-basis/provider acceptance still needs review. |
| Withdrawal and deletion | Confirmed disconnect, self-account deletion, cloud-only deletion, device erasure and saved-meal withdrawal; local erasure rotates the account data key | Actual API deletion/cascade tests and native confirmed erasure pass. Callback generations reject late connection recreation. Remote logout is separate and its failure is reported honestly; blocks/cooldown suppress remote retries. Auth-provider logout and MCP DELETE still need authenticated Swiggy fixtures. |
| Session hygiene | Encrypted logical session ID/version/tool schemas survive Edge worker changes; durable DB lease and generation fencing serialize calls | Local official-SDK mock with both JSON and SSE: two fresh workers initialize once for one user; another user receives a separate session. Real Postgres tests reject concurrent/stale owners and uncertain crash recovery. **Swiggy cross-worker acceptance and actual auth-event accounting remain pending.** |
| Rates and errors | Durable conservative 55 requests/minute and 12/10-second burst budgets, Retry-After cooldown, stop on rejected auth/block, 28-second RPC action deadline, no automatic purchase retries | HTTP 429 cooldown survives worker changes with zero subsequent traffic; 401 stops credential reuse. Symbolic rate error returns a sanitized retry-later status. Staging must validate actual headers/error envelopes and block-clearing process. |
| Availability and money honesty | Explicitly open restaurants; sold-out dishes excluded; identifiers preserved; price units unverified by default; authoritative existing-cart payable only | Normalization tests pass. Winner selection never mutates the cart or implies that an existing cart quotes the winner. Coupon descriptions remain unapplied. Live units, stock races, customization/cartItems and coupon/payment eligibility are pending. |
| Nutrition honesty | Missing nutrition remains unknown; only entered food totals count as consumed | No invented restaurant macros. Sourced/calibrated estimates, portion provenance and macro-aware matching remain product work before claims of validated health recommendations. |
| Attribution | Real cards label “Powered by Swiggy”; synthetic build labels itself throughout | Source/build inspection and demo screenshots. No claim of endorsement. Swiggy branded assets and layout approval remain subject to provider review. |
| Voice/AI | On-device recognition only when API/device supports it; otherwise typing. No cloud speech, LLM, advertising or training integration | Code/build/lint inspection. Voice model availability varies by phone. The orb is an animated interface and local phrase parser, not a deployed conversational AI. |
| Diagnostics | Hashed user/session IDs, tool, duration and status; deprecation warning logs; no provider arguments/results in application logs | Source inspection and local protocol tests. Actual alert routing, on-call coverage and staging incidents remain unproven. |

## Validation completed

- Both Android `real` and `demo` debug APKs assemble; both lint variants pass.
- Deno entrypoint type-checks; **13 backend/protocol tests pass**.
- Actual Mumbai Postgres transaction tests cover OAuth single use, two-user isolation, concurrent lease rejection, request budget, durable cooldown, stale completion fencing, withdrawal/callback race, uncertain initialization crash, 90-day expiry and Auth cascades. Synthetic SQL users are rolled back.
- **14 deployed API assertions pass** with two synthetic authenticated users, including encryption roundtrip, region, strict tracker schema, consent version, confirmation, ownership and post-deletion rejection. No emails were sent to test accounts.
- Cron job succeeded. Test accounts, cloud tracker rows and provider connections were zero after cleanup.
- Emulator exercised consent, address choice, MCP discovery, shortlist of three, face-down shuffle, winner and disabled demo checkout. Entered allowance survived reopen, then returned to defaults after confirmed erasure. Native DB/WAL payloads are encrypted.
- [Public review video](https://qvqnzsqrxejdlvnbqwcs.supabase.co/storage/v1/object/public/hungii-public-review/hungii-approval-demo.mp4) is a recording of fictional local MCP data, not a Swiggy staging run.

## External gates before production

1. Owned Google Cloud OAuth client and real app sign-in/PKCE verification; final public privacy notice with the founder's approved contact and processor disclosures.
2. Formal Swiggy review/agreements, exact registered HTTPS callback, approved seeded staging accounts/hosts and price/tool fixtures. Proposed live callback: `https://qvqnzsqrxejdlvnbqwcs.supabase.co/functions/v1/hungii-api/callback?forceFunctionRegion=ap-south-1`.
3. Written confirmation of personalized reranking, offer optimization, consented shortcuts/history retention and durable session reuse/egress policy. No scraping, catalog export or competitive ranking is implemented.
4. Actual India processing/egress/subprocessor assessment; any required DPA/transfer arrangements. A Mumbai database/response header alone cannot certify the whole path.
5. Authenticated provider replay/cancellation/expiry/logout, schema changes, rates, stock and price-unit validation; at least 48 hours green staging and explicit production access.
6. Approved item/customization, coupon and payment contracts before any cart write/order. Calibrated nutrition before validated macro scoring. Production release/signing/distribution, alert routing, support coverage, backup/recovery and staged rollout verification.

Provider sources: [authentication](https://mcp.swiggy.com/builders/docs/start/authenticate.md), [rates](https://mcp.swiggy.com/builders/docs/operate/rate-limits.md), [data handling](https://mcp.swiggy.com/builders/docs/operate/data-and-compliance.md), [production checklist](https://mcp.swiggy.com/builders/docs/build/ship-to-production.md). Infrastructure sources and protocol reasoning: [technical source review](approval-technical-sources.md), [Supabase pricing](https://supabase.com/pricing), [regional invocation](https://supabase.com/docs/guides/functions/regional-invocation). Passing the checks above demonstrates specific controls; it does not replace the remaining approvals or legal review.
