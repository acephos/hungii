<img src="assets/icon.svg" width="64" height="64" alt="">

# hungii

Created in [T3 Code](https://t3.codes).

## Android prototype

The current prototype is a native Kotlin / Jetpack Compose Android app with charcoal surfaces, electric lime, an animated voice orb, swipe-to-shortlist meal cards, and a face-down lucky draw.

Build and run instructions: [android-prototype/README.md](android-prototype/README.md). Requires Android 8.0 or newer. The downloadable build is generated at `artifacts/hungii-android-prototype.apk`.

Version 0.6 has separate real-service and synthetic local-demo builds. Supabase Free is provisioned in Mumbai, with an authenticated API, backend-only encrypted tracker/credential/session storage, durable MCP session leases and scheduled expiry cleanup. WorkOS staging email sign-in is configured; Swiggy access remains pending; the real build never substitutes sample meals for provider results.

The first screen offers email sign-in or local-only use without an account. Profile/goals/preferences sync is optional after sign-in, restores across devices and prevents silent conflicting overwrites. Saved Swiggy meal shortcuts remain local.

The Android tracker and consented meal shortcuts use AES-256-GCM with per-account Keystore keys. Voice uses an available on-device recognizer or typed input. The demo uses our fictional MCP fixtures, is labeled throughout and cannot order.

See [readiness evidence](docs/approval-readiness.md), [service setup](docs/swiggy-setup.md), [stack decision](docs/adr/0003-workos-with-supabase.md) and [MCP contract](docs/swiggy-integration-contract.md). Local tests and deployed Supabase checks do not establish Swiggy approval or production certification.

## Earlier web exploration

```sh
python3 prototype/serve.py
```

Open [Hungii](http://localhost:5173/prototype/?variant=a) to review the earlier Companion, Pocket, and Daybook layouts. The Android prototype supersedes these visual directions.

Swipe to shortlist three meals, shuffle them face down, and pick a card. The orb supports scripted text updates and optional browser voice. Meals, nutrition, prices, coupons, and checkout are sample data; nothing is ordered.

See [prototype notes](prototype/README.md) and the [product specification](docs/meal-planner-product.md).

## Checkpoints and preview releases

Public repository: [acephos/hungii](https://github.com/acephos/hungii). Main/PR checks type-check and test the backend, then build and lint both Android variants. Standard GitHub-hosted Linux runners are free for public repositories. No paid runner or store publication is enabled.

Push an annotated `vX.Y.Z` tag from a tested main checkpoint to build and publish a GitHub prerelease with the phone preview, emulator demo and SHA-256 checksums. The release uses the stable preview signing key held in GitHub Secrets and verifies its public certificate fingerprint before publication. [Release setup](docs/releases.md).
