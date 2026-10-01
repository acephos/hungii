# Hungii Android

Native Kotlin / Compose app using charcoal and electric lime, with check-in, swipe to three finalists, face-down shuffle/reveal, an offline tracker and consented local favorites.

Version 0.3 removes fictional meal/price/coupon/nutrition fixtures and the stock-photo catalogue. It starts disconnected; meals arrive only from the prepared Swiggy MCP adapter after service setup. Targets are editable defaults; consumed food and spending start at zero. Room retains user-entered totals and favorites across restarts, separated by Hungii account.

## Build

Open this directory in Android Studio, or use JDK 17, Gradle 8.11.1 and SDK 35:

```sh
./gradlew assembleDebug lintDebug
```

Minimum Android is 8.0/API 26. APK: `app/build/outputs/apk/debug/app-debug.apk`. Review copy: `../artifacts/hungii-android-prototype.apk`, excluded from git. Follow [service setup](../docs/swiggy-setup.md) before rebuilding a connected APK. Only the Supabase URL and publishable key go into Android's gitignored `local.properties`.

## Behaviour and boundaries

Google login uses Supabase Auth; Swiggy connects through its own browser authorization. Provider credentials stay encrypted on the backend. Select an address before searching. Cards preserve returned identifiers and display missing nutrition honestly. Saving a meal asks for retention consent; searching again refreshes availability. Food totals contain only what you enter; an order is not consumption.

Review reads the existing Swiggy cart and offer descriptions. It does not add the winner, mutate a cart, simulate discounts or order. Checkout opens Swiggy. Cart writes require verified authenticated item schemas; nutrition requires a sourced, calibrated layer. AI conversation, macro-based matching, history imports and payment orchestration are not implemented.

Check-in uses a local phrase parser and explicit search input. Voice uses Android speech recognition with typing fallback; there is no spoken AI response. ViewModel holds the UI, Room stores the tracker/favorites, and Keystore encrypts account sessions and pending login data. Portrait layouts and reduced-motion support retain the design exploration.

See [stack decision](../docs/adr/0001-native-android-and-mumbai-backend.md), [MCP contract](../docs/swiggy-integration-contract.md) and [verification](../artifacts/swiggy-verification.md). Earlier screenshots document version 0.2, not a live connection.

## Assets

Barlow Condensed Bold is from [Google Fonts](https://github.com/google/fonts/tree/main/ofl/barlowcondensed), under the included [SIL Open Font License](BARLOW-OFL.txt). Food photos load from returned HTTPS URLs; missing photos show a neutral icon. Material icons come from AndroidX Compose.
