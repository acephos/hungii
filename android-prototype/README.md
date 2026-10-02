# Hungii Android

Native Kotlin / Compose app using charcoal and electric lime, with check-in, swipe to three finalists, face-down shuffle/reveal, an offline tracker and consented local favorites.

Version 0.4 has a `real` build that starts disconnected until Google/Swiggy setup, and a separately installed `demo` build with fictional meals served through a local MCP server. The demo is labeled and has no external checkout. In the real build, meals arrive only from Swiggy after approved setup. Targets are editable defaults; consumed food and spending start at zero. Room retains user-entered totals and favorites across restarts, separated by Hungii account.

## Build

Open this directory in Android Studio, or use JDK 17, Gradle 8.11.1 and SDK 35:

```sh
./gradlew :app:assembleRealDebug :app:assembleDemoDebug :app:lintRealDebug :app:lintDemoDebug
```

Minimum Android is 8.0/API 26. APK: `app/build/outputs/apk/real/debug/app-real-debug.apk`. Review copy: `../artifacts/hungii-android-prototype.apk`, excluded from git. Follow [service setup](../docs/swiggy-setup.md) before rebuilding a connected APK. Only the Supabase URL and publishable key go into Android's gitignored `local.properties`.

## Behaviour and boundaries

Google login uses Supabase Auth; Swiggy connects through its own browser authorization. Provider credentials stay encrypted on the backend. Select an address before searching. Cards preserve returned identifiers and display missing nutrition honestly. Saving a meal asks for retention consent; searching again refreshes availability. Food totals contain only what you enter; an order is not consumption.

Review reads the existing Swiggy cart and offer descriptions. It does not add the winner, mutate a cart, simulate discounts or order. Checkout opens Swiggy. Cart writes require verified authenticated item schemas; nutrition requires a sourced, calibrated layer. AI conversation, macro-based matching, history imports and payment orchestration are not implemented.

Check-in uses a local phrase parser and explicit search input. Voice uses Android on-device speech recognition when available, with typing fallback; there is no spoken AI response. ViewModel holds the UI, Room stores the tracker/favorites, and AES-256-GCM/Keystore encrypt account sessions, pending login data and per-owner tracker/favorites payloads. Portrait layouts and reduced-motion support retain the design exploration.

See [stack decision](../docs/adr/0001-native-android-and-mumbai-backend.md), [MCP contract](../docs/swiggy-integration-contract.md) and [verification](../artifacts/swiggy-verification.md). Earlier screenshots document version 0.2, not a live connection.

## Assets

Barlow Condensed Bold is from [Google Fonts](https://github.com/google/fonts/tree/main/ofl/barlowcondensed), under the included [SIL Open Font License](BARLOW-OFL.txt). Food photos load from returned HTTPS URLs with disk/memory caching disabled; missing photos show a neutral icon. Material icons come from AndroidX Compose.

## Local MCP demo

From the repository root run:

```sh
npx --yes deno run --config supabase/functions/deno.json --allow-net=127.0.0.1 --allow-env demo/gateway.ts
```

Install `app/build/outputs/apk/demo/debug/app-demo-debug.apk` on an Android emulator. In Accounts, read the notice, enable the synthetic connection and select the synthetic address. The build reaches the host through `10.0.2.2:8788`; it is an emulator demo, not a phone-ready live connection or a Swiggy-issued mock. Native device tracker deletion, optional cloud-sync consent and Swiggy disconnection have separate controls.
