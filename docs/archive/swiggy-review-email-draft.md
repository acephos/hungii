# Unsent Swiggy review email

> Historical snapshot. This preserves the original findings or correspondence; use [current status](../approval-readiness.md) and the [documentation index](../README.md) for today’s implementation and setup.

Status: draft only. This replaces the proposed follow-up wording; it does not alter the earlier sent email's history. No developer form or new email is sent.

Saved to the connected Gmail account on 2 October 2026 as unsent draft `r-7378743094194602385`. Review it in [Gmail Drafts](https://mail.google.com/mail/u/0/#drafts). No send action was performed.

To: builders@swiggy.in

Subject: Hungii: Food seeded-staging review — Mumbai deployment, controls and demo

Hi Swiggy Builders team,

I'm building Hungii, an Android meal planner for students and early-career fitness users who care about their food budget, calories and macros but have irregular eating schedules and face decision overload. I would like Food-only seeded staging access to validate the integration before requesting production access.

The journey is deliberately finite: check in with your remaining food allowance and entered nutrition totals, browse a small pool of meal cards with explicit trade-offs, shortlist three, shuffle them face down, then choose a winner. Hungii's intended moat is its planning/ranking software, this finite decision experience and continuity from the user's own goals and preferences. It does not depend on scraping, exporting Swiggy's catalogue, selling provider data or manipulating marketplace incentives. Broader domains can be reviewed separately if a concrete product need emerges.

We have now provisioned and tested the following rather than treating architecture notes as deployment evidence:

- An account-first entry with email sign-in or a completely local tracker/profile option. Optional encrypted profile/goals/preferences cloud sync restores across devices and checks server revisions before edits; saved provider meal shortcuts remain local under separate consent.
- Native Kotlin/Compose Android builds with explicit real-service and synthetic-demo variants. Both assemble and pass lint. The demo uses Hungii-authored fictional MCP fixtures and cannot order; the real build never replaces provider results with samples.
- WorkOS AuthKit staging email login with Supabase Free Postgres and the deployed read-only adapter in Mumbai, ap-south-1. An actual function response confirms Mumbai execution, and the handler rejects other regions. No paid runtime or paid upgrade has been enabled.
- Signed WorkOS JWT verification and live-session ownership checks on every API action; backend-only RLS tables; AES-256-GCM protection of tracker, token, verifier, address identifier and session payloads. Android tracker/shortcuts use per-owner Keystore encryption, hashed database owner identifiers and disabled backup.
- Separate versioned notices for connection retention, optional cloud sync and local shortcuts. Implemented limits are ten-minute OAuth state, token/session expiry capped at five days, 90-day inactive cloud tracker and 30-day local shortcuts. Scheduled cleanup has succeeded; on-access cleanup covers resumed Free projects. We disclose Free inactivity/availability limits.
- Confirmed self-service disconnection, account deletion, cloud-only deletion and device erasure. Callback generations prevent an in-flight callback from recreating a withdrawn connection; account deletion erases linked rows before WorkOS identity deletion; a 24-hour hashed identity fence prevents late recreation. Remote revocation is reported separately if it cannot be confirmed, and rejected/cooling connections are not retried.
- Durable encrypted logical MCP session metadata, request leases, generation fencing, conservative quotas and Retry-After handling. Local protocol tests using the official SDK's server prove session reuse across fresh workers for JSON and SSE. Live Food calls remain disabled until you confirm and staging verifies Swiggy's cross-worker session/accounting requirements.
- Provider arguments/results, addresses, phones and tokens are excluded from application diagnostics. Real cards carry “Powered by Swiggy”; photos are not persisted. Voice uses only an available on-device recognizer or typed input. No external LLM, advertising or training pipeline is connected.

Validation includes 19 passing backend/protocol tests, actual Mumbai Postgres tests for replay/isolation/leases/rates/withdrawal/expiry/deletion fences, and real deployed WorkOS/Supabase checks with two temporary staging identities. Their trackers are isolated; server refresh works; deleted or revoked sessions cannot use outstanding JWTs. Temporary users and their cloud rows were removed. Native entered tracker data survives reopening and can be erased through confirmed controls. Hosted WorkOS sign-in returned to the emulator through its real PKCE callback; the founder also installed the APK and confirmed successful email-code sign-in on their phone.

Public review video:
https://qvqnzsqrxejdlvnbqwcs.supabase.co/storage/v1/object/public/hungii-public-review/hungii-approval-demo.mp4

This is a working Android recording with fictional local MCP data, not a claim of a Swiggy staging run.

Proposed hosted callback for your registration/review, including its region query:
https://qvqnzsqrxejdlvnbqwcs.supabase.co/functions/v1/hungii-api/callback?forceFunctionRegion=ap-south-1

Please confirm the approved staging OAuth endpoints/DCR process, seeded account procedure, exact callback registration and whether a persisted logical Food session can be resumed across Mumbai Edge workers without new initialization/auth events. We also need your guidance on managed egress and any required fixed IP arrangements.

Please review whether consented personalized meal sorting, local meal shortcuts, future order-history shortcuts and basket/offer optimization fit the intended-use and retention terms, and which agreements or lawful-basis evidence you require. Our retention permissions do not presume provider approval. We will not send Swiggy-originated data to external AI or use it for analytics, ads or training without the required separate consent and agreements.

The current integration reads discovery/menu/address/existing-cart/offer data. It does not modify carts, apply coupons, order or pay. An existing cart is not represented as a quote for the chosen winner. We have not guessed price units or undocumented cartItems fields. Missing nutrition stays unknown; independently sourced, calibrated estimates and validated macro-based scoring remain work before health recommendation claims.

We are not asserting complete legal compliance or production readiness. production WorkOS setup, the final approved privacy/support contact, processor/egress assessment, formal agreements, live provider fixtures, operational alert routing/recovery and at least 48 hours green staging remain launch gates. Production will wait for your explicit approval. Please also confirm staging pricing/quotas: this project currently has no budget to activate paid services.

I can provide the architecture, control matrix, test evidence and source review through your preferred review channel. Please let me know the next onboarding steps.

Thanks,
Aniket Singh
