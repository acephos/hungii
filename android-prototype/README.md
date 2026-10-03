# Hungii Android

Native Kotlin / Compose app using charcoal and electric lime, with check-in, swipe to three finalists, face-down shuffle/reveal, an offline tracker and consented local favorites.

The current preview has a `real` build with WorkOS staging email sign-in and approval-gated Swiggy setup, and a separately installed `demo` build with fictional meals served through a local MCP server. The Simulator is labeled, performs MCP cart/coupon/mock checkout calls and offers manual Swiggy checkout. In the real build, meals arrive only from Swiggy after approved setup. Targets are editable defaults; consumed food and spending start at zero. Room retains user-entered totals and favorites across restarts, separated by Hungii account.

## Build

Open this directory in Android Studio, or use JDK 17, Gradle 8.11.1 and SDK 35:

```sh
./gradlew :app:assembleRealDebug :app:assembleDemoDebug :app:lintRealDebug :app:lintDemoDebug
```

Minimum Android is 8.0/API 26. APK: `app/build/outputs/apk/real/debug/app-real-debug.apk`. Review copy: `../artifacts/hungii-android-prototype.apk`, excluded from git. Follow [service setup](../docs/swiggy-setup.md) before rebuilding a connected APK. Only the Supabase URL/publishable key, public WorkOS client ID and readiness flag go into Android's gitignored `local.properties`.

## Behaviour and boundaries

Email login uses hosted WorkOS AuthKit with PKCE; Swiggy connects through its own browser authorization. Provider credentials stay encrypted on the backend. Select an address before searching. Cards preserve returned identifiers and display missing nutrition honestly. Saving a meal asks for retention consent; searching again refreshes availability. The opening screen offers email sign-in or local-only use. Cloud profile/preferences sync is an explicit choice after sign-in, with automatic restore, revision conflict handling and a stop-sync control. Saved provider meal shortcuts remain local. Food totals contain only what you enter; an order is not consumption.

The real build reads the existing live cart/offers and opens Swiggy for manual checkout; live cart writes stay approval-gated. The Simulator creates and edits a synthetic cart through MCP, checks final coupon totals, explicitly reviews UPI/QR/COD and confirms only successful mock payments. Order receipts use MCP details/status/tracking. Estimates enter consumption totals only on an explicit log action.

The Simulator’s dedicated Assistant tab uses Google ADK + Groq Free cloud inference with opt-in and reviewable app actions; the real build explains that cloud inference is available in Simulator. The key stays on the server; no model is installed locally. Speech recognition is on-device where available; typing always works. The orb has listening/thinking/idle animations, with reduced-motion support. Room/Keystore tracker and optional account sync behavior remain available.

See [stack decision](../docs/adr/0003-workos-with-supabase.md), [MCP contract](../docs/swiggy-integration-contract.md) and [verification](../artifacts/swiggy-verification.md). Earlier screenshots document version 0.2, not a live connection.

## Current design

Preview 0.7.5 applies the [mobile UI](../docs/research/mobile-ui-video-research.md), [UX psychology](../docs/research/ux-psychology-design-research.md) and [product-page](../docs/research/product-page-design-research.md) transcript guidance. Four primary destinations keep Saved under Meals. Home prioritizes discovery; filters and day edits use contextual sheets. Controls use 48 dp targets and readable supporting text. Discovery distinguishes connection, first-use, loading, failed search, no matches and exhausted batches; Pass/Keep/Undo remain available alongside swiping.

The real build offers useful local planning before optional sign-in. Starting targets stay editable; profile personalization and saving retain their existing permissions. Shortlist progress counts actual choices. Meal prices distinguish menu-only amounts from basket estimates and show their impact on the remaining allowance; unknown costs stay unknown. Basket quantity shortcuts and the returned total sit close to checkout actions. Provider names/images remain intact; no ratings or urgency are fabricated.

## Assets

Barlow Condensed Bold is from [Google Fonts](https://github.com/google/fonts/tree/main/ofl/barlowcondensed), under the included [SIL Open Font License](BARLOW-OFL.txt). Food photos load from returned HTTPS URLs with disk/memory caching disabled; missing photos show a neutral icon. Material icons come from AndroidX Compose.

## Local MCP simulator

See [demo/README.md](../demo/README.md) for gateway, Tailscale, Groq setup and contract limits. Install `app/build/outputs/apk/demo/debug/app-demo-debug.apk`. Set `hungii.demoApiUrl` in local.properties for your phone/Tailscale address; the default uses emulator host 10.0.2.2. The gateway must be running for meal discovery/checkout; the agent process is needed only for the cloud Assistant. Install restartable services using the Simulator guide. No Swiggy approval or paid cloud plan is required for synthetic discovery and mock checkout.
