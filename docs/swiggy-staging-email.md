# Staging onboarding email

To: `builders@swiggy.in`

Subject: Hungii — request for Food MCP seeded staging access and onboarding guidance

+Hello Swiggy Builders team,

I'm Aniket Singh, building Hungii, a native Android meal-planning and decision-support product. I would like to request developer-track access to your seeded Food MCP staging environment and guidance on the required onboarding steps. This is a pre-production validation request; I am not requesting permission to launch real-user traffic yet.

Hungii is for people with irregular eating schedules who rely on delivery while managing a daily food allowance and calorie/macronutrient targets, including students, early-career professionals and gym-goers. The proposed experience asks about the next craving, considers the remaining day, and presents a bounded set of meal candidates with understandable cost, delivery and nutrition trade-offs. The user shortlists three acceptable meals, sees a face-down shuffle, and chooses a card to reveal a winner. They can change the result. A swipe or draw never authorizes a cart change or purchase, and ordering is separate from recording food as eaten. ADHD-friendly design is a usability goal, not a clinical claim.

Our intended moat is the combination of Hungii-owned planning/ranking software, a finite decision flow, and continuity from the user's own entered goals, preferences and portion notes. An independently sourced, calibrated portion-level nutrition layer is planned. These are early product hypotheses, not proven conversion or retention claims. We are not proposing a harvested Swiggy catalogue, cross-user order-history dataset, competitive intelligence, or training/advertising profiles as our advantage.

The prepared stack is Kotlin/Jetpack Compose with Room, Supabase Auth/Postgres and a TypeScript MCP adapter planned in Mumbai. The code includes per-user browser OAuth/PKCE, one-use expiring callback state, encrypted provider credentials, verified Hungii sessions, explicit address selection and runtime tool-input schema checks. Local Android build/lint and eight normalization/encryption tests pass. However, the backend is not deployed, the APK is disconnected, and authenticated provider/Google flows have not been validated. Current actual MCP traffic and orders are zero. Initial testing would be a small manual seeded-data pilot, with its request budget and scaling plan agreed and benchmarked before expansion.

The current adapter exposes reads for discovery/menu, addresses, existing cart totals and offer descriptions. Cart writes and ordering are disabled pending verified authenticated item schemas and confirmation tests; checkout currently hands off to Swiggy. Reviewed menu schemas do not provide structured nutrition, so current cards keep those values unknown. Any future estimates will use documented independent evidence, clear source/uncertainty labels and no claim that they are Swiggy-published facts. Legitimate eligible offers may help affordability, but no coupon abuse, artificial transactions or background cart overwriting is proposed.

I have reviewed your published access, data-handling, rate-limit, support/branding and production-checklist requirements. Our intended processing is scoped to user-initiated tasks, with no access resale, scraping, bulk export, competitive benchmarking or bypass of controls. The prepared application does not log full MCP request/response bodies. Connection retention and local saving have separate permission controls; selected saved meal descriptors can persist locally, so I am not claiming zero PII or zero provider-data retention.

Before real-user production, the remaining gates include a formal privacy/retention notice; tested deletion and withdrawal, including derived data and expiry cleanup; verified encryption coverage and identifier handling; approved persistent per-user MCP sessions; rate/block/Retry-After handling and bounded retries; deployed region/TLS/callback verification; minimal support diagnostics, deprecation monitoring and an incident/rollout runbook. The current request-scoped connection implementation needs replacement to meet your dedicated session-hygiene guidance. Persistent-session and rate controls will be in place before hosted staging testing. Required 'Powered by Swiggy' attribution and supplied branding will be applied without implying endorsement.

No external LLM is integrated. Provider-response processing is planned in Mumbai, but infrastructure/subprocessor and Android speech-service paths still need review, so this is not an assertion that all processing is India-only. No outside-India provider-data inference is proposed without the required signed DPA, transfer arrangements and minimization. Swiggy-originated analytics, advertising or model training would require separate explicit user consent, a DPA and permission under your terms. These are release gates, not claims of current certification or production compliance.

Your developer form asks for profile links, a deployed production HTTPS callback and a publicly viewable working demo video. The production callback, egress/gateway details and public end-to-end video are not ready. I have not submitted placeholders or acknowledged an unseen integration agreement. Could you advise whether seeded staging onboarding can begin at this stage and what local-development/application route is appropriate?

In particular, please confirm: (1) approved staging OAuth endpoints, registration and test-account procedure; (2) whether bounded personal meal ranking, user-approved eligible-offer comparisons, nutrition overlays and consented saved meal descriptors fit the permitted scope; (3) the supported persistent-session topology for a Mumbai gateway and whether managed serverless egress is acceptable; (4) applicable integration/data-processing terms, retention requirements, branding assets and current rate guidance; and (5) the demo and production-callback requirements for progressing from staging to production.

Thank you. I am happy to provide the prototype walkthrough, architecture details and staging validation evidence as onboarding progresses. Please use this email as the technical contact for this initial request.

Aniket Singh
Hungii

---

Sent on 2 October 2026 through the founder's connected Gmail account, following the explicit request to contact Swiggy on their behalf. The send tool returned message ID and thread ID `1a0fb464f1a60bd6` with label `SENT`; status checked at 06:21 UTC. [Sent conversation](https://mail.google.com/mail/u/0/#sent/1a0fb464f1a60bd6).

This confirms the message was sent, not that Swiggy received/read it, granted access or accepted the use case. The developer Google Form has not been submitted, and no integration agreement has been acknowledged or signed.
