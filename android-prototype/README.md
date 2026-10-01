# Hungii Android prototype

Native Kotlin / Jetpack Compose Android review artifact on `prototype/android-lime`.

**Design question:** does a focused mobile journey help someone choose their next meal without getting stuck comparing an endless list?

The selected visual direction is charcoal with electric lime: condensed display typography, large food photos, a voice-reactive orb, nutrition stat panels, and one primary action per stage. Home handles check-in; Discover handles the shortlist; the lucky draw and reveal fill the screen. The user requested this direction after reviewing the earlier web exploration.

## Run

Open this directory in Android Studio, allow it to install the SDK packages, and run the `app` configuration on an emulator or Android device. Minimum Android version: 8.0 (API 26). Build toolchain: JDK 17, Gradle 8.11.1, Android SDK 35. The wrapper verifies the Gradle distribution checksum.

For a command-line build, set `JAVA_HOME` and `ANDROID_HOME` to your installed toolchain and create a gitignored `local.properties` containing `sdk.dir=/absolute/path/to/your/sdk`:

```sh
./gradlew assembleDebug lintDebug
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`. Install with Android Studio, or `adb -s YOUR_DEVICE_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk`. The review copy is at `../artifacts/hungii-android-prototype.apk` and is not committed.

## Try it

1. Start with the sample day. Type `₹350 left`, `2 meals left`, `something cheesy`, or `I had 2 eggs`. The sliders icon edits targets directly. Each check-in can be undone.
2. Open Discover. Swipe right to keep a meal or left to pass; buttons provide the same actions. Save a meal with the heart on its photo. Filters change the sample ranking and pool.
3. Keep three finalists. Tap **Turn over & shuffle**. Cards turn face down, move, and become selectable when the shuffle finishes. Tap one for the reveal. You can revisit the finalists.
4. Continue to a simulated bill. The cheesy flatbread demonstrates a threshold offer: adding a ₹40 curd side saves ₹20 overall and updates the nutrition ranges.
5. Confirm a demo meal. This records sample spending and reserves food without increasing consumed nutrition. Log the whole meal or half separately. My day shows intake and remaining reservations, with an action to log saved portions later. Confirming clears the completed shortlist for the next decision.
6. Saved provides shortcuts to go-to meals for this session.

## Prototype boundaries

All meal names, restaurants, nutrition ranges, prices, delivery times and coupons are fictional fixtures. Food photos are illustrative stock images. The nutrition ranges demonstrate estimate uncertainty and do not describe the photographed dishes. The ranking is a local heuristic for interaction review. Hard vegetarian and price filters are respected; taste, protein and speed preferences influence ranking. There are no Swiggy requests, real orders, payments or user accounts.

The check-in parser only understands the sample phrases and a few variations. The orb animates continuously and reacts to the platform speech recognizer's sound level when listening. Microphone permission is requested only when voice is tapped. Recognition depends on an installed Android speech service; typing remains available. There is no AI conversation or spoken assistant response in this build.

State is in memory and resets when the activity is recreated or the app process restarts. This prototype targets portrait phones. System animation settings disable continuous orb/shuffle motion. The implementation is disposable; accepting the visual direction does not select a production stack.

## Review status

Visual direction selected by the user; usability verdict pending user review. Verification details and screenshots are recorded in `../artifacts/android-verification.md`.

The T3 Device panel returned an unavailable Android host and repeated discovery errors after SDK installation. Native verification used a dedicated Android 15 Pixel 7 emulator through ADB. No connected physical phone was changed.

## Assets

Barlow Condensed Bold is from [Google Fonts](https://github.com/google/fonts/tree/main/ofl/barlowcondensed), licensed under the included [SIL Open Font License](BARLOW-OFL.txt). Stock-photo source URLs are listed in [the earlier prototype notes](../prototype/README.md#photo-sources). Material icons are supplied by AndroidX Compose.
