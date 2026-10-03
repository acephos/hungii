# Android preview releases

GitHub Actions checks `main` and pull requests: backend/protocol tests, repository hygiene/local documentation links, no-inference Assistant regressions, and both Android variants' unit tests/build/lint. The release workflow runs for `vX.Y.Z` tags or an explicit existing-tag dispatch. It validates tag version and ancestry on main, repeats the relevant checks and publishes a GitHub prerelease with:

- `hungii-preview.apk`: the phone build with WorkOS staging login and optional cloud sync/local-only use.
- `hungii-simulator.apk`: separate synthetic MCP checkout + cloud Assistant; connect through Tailscale to the running local services.
- `SHA256SUMS.txt`: file checksums.

These are development-signed previews, with the same certificate as the Tailscale APK. They are not Play Store production releases. Production signing, verified Android App Links and provider approvals remain separate release gates. No Play Store fee, paid runner or paid service is activated.

## Repository configuration

Public variables: `HUNGII_SUPABASE_URL`, `HUNGII_SUPABASE_PUBLISHABLE_KEY`, `HUNGII_WORKOS_CLIENT_ID`, `HUNGII_DEMO_API_URL`, and `HUNGII_PREVIEW_CERT_SHA256`. Publishable keys and client IDs are public identifiers; the backend still requires a verified WorkOS identity/session for account access.

Private secret: `HUNGII_PREVIEW_KEYSTORE_B64`, containing the stable preview signing key. Never commit/export it in logs or artifacts. Do not generate a new key for each CI run: Android would reject updates to existing installs. The workflow sets `HUNGII_PREVIEW_KEYSTORE_PATH` explicitly for Gradle and deletes that runner-local copy after the job. The current local Android key is in the path reported by `./gradlew :app:signingReport`, which can differ from `~/.android` under XDG configuration.

Supabase service-role, database password/encryption key and WorkOS/Swiggy API secrets are not needed by build/release CI and must remain off GitHub/Android artifacts. CI tests do not call real provider services or migrate the deployed database.

## Publish a checkpoint

1. Merge a green change into main.
2. Increment `versionCode` and the default app version in `android-prototype/app/build.gradle.kts`. The hardening checkpoint is `0.7.3`, code 11; always use the version in the final merged source.
3. Create and push a new matching annotated tag, for example `git tag -a v0.7.3 -m 'Hungii simulator and repository hardening'` then `git push origin v0.7.3`.
4. Check the Publish Android preview workflow. Only a successful build with the expected signing certificate may publish assets. Download the phone APK from the prerelease.

Standard hosted Linux runners for this public repository are free. No artifact archive is uploaded by the check workflow; release APKs attach directly to GitHub Releases. Workflows have timeouts, pinned action commits and limited token permissions. [GitHub billing](https://docs.github.com/en/billing/concepts/product-billing/github-actions), [secrets](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets).

Published tags and APK/checksum assets are historical checkpoints. Uploads do not use `--clobber`; rerunning publication cannot silently replace an existing APK. Use a new version for a fix. An existing-tag dispatch is for an unpublished or incomplete release, and duplicate asset names fail rather than overwrite. Preserve published previews when cleaning obsolete merged branches. See [the initial release history](archive/push-review.md) for failed early tags.

The CI signing configuration uses an explicit debug `SigningConfig.storeFile`, following the [Android command-line signing documentation](https://developer.android.com/build/building-cmdline).
