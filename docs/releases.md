# Android preview releases

GitHub Actions checks `main` and pull requests. The separate release workflow runs for `vX.Y.Z` tags or an explicit existing-tag dispatch. It validates the tag's version and ancestry on main, tests the backend, builds/lints both Android variants and publishes a GitHub prerelease with:

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
2. Increment `versionCode` and the default app version in `android-prototype/app/build.gradle.kts` when making a new release. Tag `v0.6.2` corresponds to the `0.6.2-account-sync` checkpoint, code 7.
3. Create and push the matching annotated tag, for example `git tag -a v0.6.2 -m 'Hungii optional-sync Android preview'` then `git push origin v0.6.2`.
4. Check the Publish Android preview workflow. Only a successful build with the expected signing certificate may publish assets. Download the phone APK from the prerelease.

Standard hosted Linux runners for this public repository are free. No artifact archive is uploaded by the check workflow; release APKs attach directly to GitHub Releases. Workflows have timeouts, pinned action commits and limited token permissions. [GitHub billing](https://docs.github.com/en/billing/concepts/product-billing/github-actions), [secrets](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets).

The CI signing configuration uses an explicit debug `SigningConfig.storeFile`, following the [Android command-line signing documentation](https://developer.android.com/build/building-cmdline). The initial `v0.6.0` run was blocked by the certificate guard because the runner used a different default debug keystore; no assets were published for that tag.
