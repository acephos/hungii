<img src="assets/brand/wordmark.svg" width="185" height="82" alt="Hungii">

# Hungii

Hungii helps you choose your next meal within your remaining food allowance, entered nutrition goals and taste preferences. Shortlist three meals, shuffle them face down and reveal a winner. Choosing a meal, paying for it and logging it as eaten are separate actions.

The current product is a native Kotlin / Jetpack Compose Android preview for Android 8+. Only the Food integration is implemented; Instamart, Dineout and Scenes remain future scope.

Download **hungii.apk**, the single user-facing Android app, from [GitHub Releases](https://github.com/acephos/hungii/releases). It has an encrypted offline tracker, optional profile sync, simplified delivery settings and a prepared Swiggy basket/payment/tracking flow. **Live ordering remains gated on Swiggy access and staging verification.** These are development-signed previews, not Play Store production releases.

The Simulator (`demo` flavor) is a developer-only testing build with fictional meals and payments, not a second app users need to install. It is not published in new releases. Historical preview and Simulator APKs remain unchanged.

## Run and build

- [Android instructions](android-prototype/README.md): build both variants with JDK 17, SDK 35 and the Gradle wrapper.
- [Simulator setup](demo/README.md): start the gateway, configure your phone endpoint, enable optional cloud inference and install services that restart after reboot.
- [Real service setup](docs/swiggy-setup.md): public mobile settings, WorkOS/Supabase and remaining Swiggy access gates.
- [Contributor checks](CONTRIBUTING.md): backend, Assistant, Android and repository hygiene commands. Tests use authored fixtures and no live inference.

The Simulator needs neither a Swiggy account nor paid inference. Its cloud Assistant requires a separate opt-in and a server-side Groq Free key. No model runs on the phone or computer. Inference availability depends on the free provider's quotas. Assistant actions require review before changing the tracker.

## Status and documentation

Use the [documentation index](docs/README.md) for current guides, product requirements, architecture decisions, dated research and historical correspondence. [Readiness evidence](docs/approval-readiness.md) distinguishes implemented controls from external launch gates; [verification records](artifacts/README.md) record checks at specific versions.

The real build never substitutes sample meals for provider results. It has no enabled live cart mutation, ordering or payment. The prepared coordinator and remaining contract gaps are described in [checkout preparation](docs/swiggy-ordering-preparation.md). Missing provider nutrition stays unknown; Simulator estimates are fictional. Server restarts clear synthetic carts/orders. Real checkout recovery uses an encrypted backend ledger and device request ID; its provider behavior still needs staging verification.

[Privacy draft](docs/privacy-notice-draft.md), [security reporting](SECURITY.md), [incident runbook](docs/incident-runbook.md) and [release process](docs/releases.md) describe data paths and maintenance. Provider access/agreements, production identity/signing, calibrated nutrition and operational readiness remain launch gates.

## Earlier web exploration

The [web prototype](prototype/README.md) preserves the original design exploration with scripted conversation and fictional meals:

```sh
python3 prototype/serve.py
```

Open [the local prototype](http://localhost:5173/prototype/?variant=a). The Android implementation supersedes it.

Built in [T3 Code](https://t3.codes).

The [fork-h identity](assets/brand/README.md) is shared across the app, adaptive/themed launcher icons and repository.
