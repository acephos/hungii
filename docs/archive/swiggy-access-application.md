# Hungii: Food staging access request

> Historical snapshot. This preserves the original findings or correspondence; use [current status](../approval-readiness.md) and the [documentation index](../README.md) for today’s implementation and setup.

Status update: [0.4 readiness evidence](../approval-readiness.md) records the free Mumbai deployment, hardening and completed checks. The remaining text preserves the original early proposal; an updated draft is in [the review email](swiggy-review-email-draft.md), not sent.

Prepared 2 October 2026 for the founder-authorized request to `builders@swiggy.in`. This is an early-stage onboarding request, not a production-access certification or a completed developer form. The [email text and send confirmation](swiggy-staging-email.md) record the Food seeded-staging request sent through the founder's connected Gmail account. Access remains pending Swiggy's response.

## Product and use case

Hungii is a native Android next-meal planner for people with irregular eating schedules who rely on food delivery while managing a daily food allowance and calorie/macronutrient targets. The intended audience includes students, early-career professionals and gym-goers who prefer a short, low-friction decision flow. It does not require an ADHD diagnosis and makes no clinical claims.

A user checks or edits today's recorded intake, remaining money and expected eating opportunities, then describes a craving. Hungii proposes a bounded set of meals with clear price, delivery and nutrition uncertainty. The person swipes to choose three acceptable finalists, sees them turn face down and shuffle, and selects one to reveal a winner. The draw is a voluntary decision aid: the user can change their choice, and it never changes a cart or places an order. Checkout remains a separate explicit action; food is recorded as consumed only when the user reports eating it.

The proposed full product compares next-meal trade-offs against the whole remaining day. Legitimate eligible offers may inform affordability, but no fake transactions, coupon abuse, manufactured incentives, secret sponsored weighting, or background cart overwriting are proposed. Written approval is requested for the permitted scope of personalized candidate ranking and offer comparisons.

## Differentiation and intended moat

The intended moat combines Hungii-owned planning/ranking software, a tested finite decision flow, and continuity from the user's own entered budgets, preferences and portion notes. Independently sourced and calibrated portion-level nutrition is a future layer. Over time, execution quality and preference continuity should help the user make a confident choice with fewer repeated decisions. These are product hypotheses to validate, not demonstrated retention or conversion results.

Provider catalogue extraction, cross-user order-history profiling and Swiggy-derived training/advertising/competitive-intelligence data are not the proposed moat. API access is not exclusive. Any future use of Swiggy-originated data for analytics, advertising or model training would require explicit separate consent, a DPA and provider approval under applicable terms. [Data requirements](https://mcp.swiggy.com/builders/docs/operate/data-and-compliance.md), [ground rules](https://mcp.swiggy.com/builders/access/)

## Initial integration and readiness

- Requested scope: Food only, seeded staging. Instamart is a possible later extension requiring separate discussion; Dineout and Scenes are outside this pilot.
- Surface: native Android, Kotlin/Jetpack Compose, Room for local user-entered tracker data, Supabase Auth/Postgres and a TypeScript MCP adapter planned in Mumbai.
- Prepared code: per-user browser OAuth/PKCE with one-use expiring state, encrypted provider tokens/verifiers, separately verified Hungii sessions, explicit address selection, runtime input-schema validation, menu/discovery/cart/offer reads. Android build/lint and eight normalization/encryption tests pass locally.
- The current backend is undeployed and the APK is disconnected. No authenticated Swiggy or Google login has been validated. Initial actual traffic and orders are zero; the small seeded pilot's request envelope will be agreed and benchmarked before expansion.
- Current cart adapter is read-only. Writes/order placement remain disabled until authenticated item schemas and explicit confirmation behavior are verified. No payment/card/OTP handling is implemented by Hungii.
- Reviewed menu schemas do not provide structured nutrition. Current cards keep it unknown. Estimates, when developed from independent evidence, must be labeled with sources and calibrated uncertainty rather than presented as Swiggy facts.
- Production callback, gateway/egress IP information, publicly viewable demo video and security review are pending infrastructure setup. No placeholder is submitted as a real callback or demo.

## Compliance position and release gates

Only bounded, user-initiated calls within the approved task scope are intended. No access resale, bulk catalogue export, scraping, competitive benchmarking or bypass of provider controls is proposed. Current code does not log full tool request/response bodies; all arguments/results are treated as sensitive. Provider data attribution must follow supplied brand guidelines, including prescribed "Powered by Swiggy" wording without implying an unapproved partnership. [Access](https://mcp.swiggy.com/builders/docs/operate/access.md), [branding/support](https://mcp.swiggy.com/builders/docs/operate/support.md)

The following are pending before real-user production, rather than claimed complete:

1. Replace request-scoped MCP initialization with provider-approved persistent per-user session handling; honor current limits, block/rejection, `Retry-After` and bounded retries. Do not blindly retry purchases. Dedicated rate-limit guidance takes precedence over older conflicting checklist language, subject to provider confirmation.
2. Publish a privacy notice and retention schedule; implement and test full deletion/withdrawal, expiry cleanup, and removal of derived provider data on the relevant deletion requests. Direct Swiggy account access/correction/erasure requests to Swiggy and coordinate deletion on Hungii's side.
3. Verify encryption for every persistent store, permitted retention of consented favorites, lawful identifier storage or hashing, processor/subprocessor handling and any required contractual documents. Tokens are encrypted now; this does not certify every store or backend deployment.
4. Deploy and verify Mumbai-region provider processing, TLS and exact callback allowlisting. No external LLM is integrated. Android's default speech service and identity-provider processing need review; this is not a blanket assertion that all processing is India-only. No cross-border provider-response inference is proposed without a signed DPA, valid transfer arrangements and minimization.
5. Complete two-user isolation, OAuth replay/expiry/cancellation, rate/error/stock/price-unit tests, minimal permitted support diagnostics, deprecation monitoring, incident runbook/contact and gradual rollout. Obtain explicit production approval after the documented staging validation period.

See the [compliance evidence and gaps](swiggy-application-compliance.md). These gates align the request with published requirements; they do not establish legal certification or substitute for Swiggy's review. [Compliance](https://mcp.swiggy.com/builders/docs/operate/data-and-compliance.md), [rate limits](https://mcp.swiggy.com/builders/docs/operate/rate-limits.md), [go-live checklist](https://mcp.swiggy.com/builders/docs/build/ship-to-production.md)

## Questions for Swiggy

1. Can this early-stage Food use case be onboarded to seeded staging before production infrastructure and demo requirements are complete? What developer-form/local-callback procedure should be used?
2. What are the approved staging OAuth endpoints, test-account procedure and any payment simulation restrictions?
3. Is bounded personal meal ranking and user-approved comparison of documented eligible offers within the permitted task scope? What extra limitations apply to nutrition overlays and saved go-to meal descriptors?
4. What persistent-session deployment pattern is supported for a Mumbai gateway, and is managed serverless egress acceptable or is fixed egress required?
5. Please provide applicable integration/data-processing terms, required privacy/retention arrangements, branding assets and the current rate-limit/onboarding guidance.

## Formal form status

The official [developer form](https://forms.gle/4vkeKyqm15Qb6fnJA), linked from Swiggy's [application page](https://mcp.swiggy.com/builders/access/), was inspected on 2 October 2026. Its first page requires Google sign-in, full name/contact, developer category, project name, GitHub/portfolio, LinkedIn, product/architecture descriptions, production redirect URI, a publicly viewable working demo video, and acknowledgement of MCP terms. Production URI instructions explicitly exclude local-development URIs. No form submission or terms acknowledgement is represented by an onboarding email. Later form sections may impose further requirements.
