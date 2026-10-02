# Hungii free-stack research

Checked 2 October 2026 against provider documentation. These research notes informed the [accepted stack decision](adr/0001-native-android-and-mumbai-backend.md); Supabase Free was subsequently provisioned; see [current readiness evidence](approval-readiness.md). Free quotas are service limits, not a promise that an entire production app stays free.

## Current selection

The founder selected WorkOS AuthKit with Supabase. Staging email authentication is configured and tested against the real Mumbai backend. Use the free hosted auth domain and email codes, avoiding paid SSO, custom domains and SMS. The native public-client protocol avoids upgrading Kotlin solely for the current Android SDK. Database access remains server-only; [ADR](adr/0003-workos-with-supabase.md). Earlier recommendations below are research context, superseded where they proposed Supabase Auth or Convex.

## Supabase: credible alternative to Convex

The Free plan includes two active projects, 500 MB database per project, 50,000 monthly active users, 1 GB file storage, 5 GB egress, 500,000 Edge Function invocations, two million realtime messages and 200 peak realtime connections. [Billing documentation](https://supabase.com/docs/guides/platform/billing-on-supabase)

Free projects pause after a week of inactivity; automatic backups are not included. Pro starts at US$25/month with the first project included. [Pricing](https://supabase.com/pricing)

Supabase supports a specific Mumbai (`ap-south-1`) region. Choosing the broad APAC region instead does not guarantee Mumbai: the documented general APAC option is Singapore. Region controls primary data location and is not itself a compliance guarantee. [Regions](https://supabase.com/docs/guides/platform/regions)

The Kotlin client supports database access, auth, storage, realtime and Edge Functions, but Supabase explicitly identifies it as community-maintained rather than an official library. Its documented minimum Android SDK is 26 unless desugaring is enabled. [Kotlin introduction](https://supabase.com/docs/reference/kotlin/introduction), [installation](https://supabase.com/docs/reference/kotlin/installing)

Recommendation: Supabase is attractive if we want Postgres for consumption entries, spend entries, favorites and historical reporting, and Mumbai hosting. Supabase Auth could cover Hungii login without a second auth vendor. Swiggy authorization would still be a separate user connection. We would need server-side access checks and a separate decision about where the Swiggy/AI orchestration runs; a generous database free tier does not solve provider access or AI inference costs.

## Firebase: useful even with another backend

FCM push notifications, Crashlytics, App Distribution and Performance Monitoring are listed as no-cost products. Spark needs no payment method. Remote Config now has a 100,000-fetch/day/project no-cost allowance; its new pricing took effect on 1 September 2026. Firestore Standard offers 1 GiB storage, 50,000 document reads/day and 20,000 writes/day. Phone authentication is billed per SMS and is unavailable on Spark. [Pricing](https://firebase.google.com/pricing)

Deploying Cloud Functions requires Blaze, even though functions can be tested locally on any plan. Thus “Firebase has a free tier” does not mean we can deploy our Swiggy gateway without enabling billing. [Functions getting started](https://firebase.google.com/docs/functions/get-started)

Recommendation: use Crashlytics and App Distribution early; add FCM when reminders genuinely help users. Firebase can supply these without replacing the primary database. Avoid assuming Cloud Functions, Cloud Storage or SMS login are available on a card-free plan: check the individual product before selecting it.

## Voice: native Android first, with explicit fallbacks

Android's ordinary `SpeechRecognizer` can stream audio to remote servers and is not designed for continuous recognition. Android 12/API 31 introduced checks and factory methods for an on-device recognizer, but availability must be checked on each device. Language support/model downloads can still fail. [SpeechRecognizer reference](https://developer.android.com/reference/android/speech/SpeechRecognizer)

Android provides `TextToSpeech`; each available voice exposes whether it requires a network connection through `isNetworkConnectionRequired()`. Selecting an installed offline voice is therefore an explicit implementation decision, not an automatic consequence of using Android TTS. [TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech), [Voice](https://developer.android.com/reference/android/speech/tts/Voice)

Recommendation: start with tap-to-talk, native recognition and native TTS, keeping typed input available. This avoids introducing a paid cloud speech service at the outset, but does not guarantee offline recognition, identical voices or identical language support on all phones. The orb animation can be native Compose drawing driven by audio levels; the animation itself does not require an AI service. Real conversational interpretation remains a separate model/runtime choice.

## Nutrition: useful free reference data, not Swiggy meal truth

USDA FoodData Central allows public API access with a data.gov key. Its default limit is 1,000 requests/hour/IP; exceeding the limit temporarily blocks the key for an hour. Data are public domain under CC0; attribution is requested. The key must not be published. The catalogue includes Foundation, SR Legacy, survey foods and branded products. [API guide](https://fdc.nal.usda.gov/api-guide/)

Recommendation/inference: use it as ingredient/reference nutrition, cache results on the backend, and keep the API key there. A reference paneer or cooked-rice record cannot establish the portion size, oil, sauce or recipe of a specific Indian restaurant's delivered meal. Published restaurant nutrition should retain its provenance; inferred recipe nutrition should retain portion assumptions, calorie/macro ranges and an estimate label. Database availability does not validate those uncertainty ranges—we would need a calibration dataset or human review.

## Cost-sensitive design implications

Recommendations: keep budget arithmetic, eligibility checks, scoring, shortlist selection and random draw deterministic. Use a model to convert conversation into structured preferences and explain existing trade-offs, not as the authority for totals or coupon eligibility. Cache reusable nutrition and user preferences; refresh time-sensitive availability and quotes only when needed and only within Swiggy's documented caching/persistence rules. The live integration remains gated by the Swiggy access and data constraints recorded in [the feasibility notes](meal-planner-api-feasibility.md).

## Convex and WorkOS

Convex Free currently permits 1 million function calls/month, 0.5 GB database storage, 1 GB file storage, 1 GB/month database I/O, 1 GB/month file egress and 20 GB-hours/month action compute. Subscription updates count as calls. Free has hard caps; Starter is a separate pay-as-you-go option beyond included usage. [Limits](https://docs.convex.dev/production/state/limits), [pricing](https://www.convex.dev/pricing)

Its official Android Kotlin client supports queries, mutations, actions and realtime subscriptions exposed as Kotlin Flow. Authentication has an interface for custom providers; the documented Android integrations are Auth0 and Clerk. Convex's AuthKit integration guide configures WorkOS JWT verification, but its client walkthrough uses React, so we should validate the Kotlin token-provider integration in a small spike before committing to it. [Kotlin client](https://docs.convex.dev/client/android/overview), [AuthKit integration](https://docs.convex.dev/auth/authkit)

WorkOS AuthKit is free for the first 1 million monthly active users. Enterprise SSO and other products have separate pricing. WorkOS has an official native Android SDK and public-client PKCE flow; current docs require Android API 26+, Kotlin 2.4+ and JDK 17. The prototype uses Kotlin 2.1.20, so SDK compatibility needs an upgrade check. WorkOS secrets must stay off the APK. [Pricing](https://workos.com/pricing), [Android SDK](https://workos.com/docs/sdks/android)

Recommendation: Convex plus WorkOS is a credible combination for fast development of reactive preferences and ledgers. The million-user authentication allowance does not mean the backend can support a million active users on its free quota.

## Hosting constraint specific to Swiggy

Convex currently lists Virginia, Ireland, Sydney and Canada as hosting regions, with no India option. Swiggy's provider documentation requires a signed DPA plus appropriate transfer mechanisms before production when an integration processes MCP responses outside India. This is a provider-contract requirement, not a claim that every app must host in India. [Convex regions](https://docs.convex.dev/production/regions), [Swiggy data and compliance](https://mcp.swiggy.com/builders/docs/operate/data-and-compliance.md)

Supabase's Mumbai project region applies to primary project data. Edge Functions default to the region nearest the caller; their docs support explicit Mumbai invocation. A live Swiggy adapter should enforce and verify its execution region rather than assume a Mumbai database makes all processing local. Region choice alone does not establish compliance. [Supabase regions](https://supabase.com/docs/guides/platform/regions), [function regional invocation](https://supabase.com/docs/guides/functions/regional-invocation)

Hungii account login and connecting a Swiggy account remain separate. Swiggy requires its own OAuth 2.1 / PKCE browser consent, exact approved HTTPS redirects and reviewed production access. WorkOS's MCP-auth product does not grant Swiggy access. The reviewed access page does not establish a guaranteed zero-price commercial API arrangement. [Swiggy authentication](https://mcp.swiggy.com/builders/docs/start/authenticate.md), [access](https://mcp.swiggy.com/builders/docs/operate/access.md)

## AI and proposed starting stack

Gemini's developer API has limited free access to selected models; its free tier uses submitted content to improve Google's products. Use fictional data to develop conversation handling. Production inference needs a separate decision on data terms, region, reliability and cost, especially before sending Swiggy-originated responses. [Gemini pricing](https://ai.google.dev/gemini-api/docs/pricing), [terms](https://ai.google.dev/gemini-api/terms)

Proposed starting stack: Kotlin / Compose; Room for an offline food log and pending sync; Supabase Postgres and Auth in Mumbai; a TypeScript Swiggy adapter evaluated in Mumbai Edge Functions; Android speech/TTS with text fallback; Firebase Crashlytics and App Distribution. Add an interchangeable intent model while keeping calculations and ranking deterministic. Validate the MCP transport, auth callbacks, region enforcement and function runtime limits in a short integration spike before selecting Edge Functions permanently. [Compose](https://developer.android.com/compose), [Room](https://developer.android.com/training/data-storage/room)

Alternative: Convex / WorkOS if we prefer their development model and clear Swiggy's cross-border processing arrangements. These are discussion recommendations, not an adopted architecture or a change to the APK.
