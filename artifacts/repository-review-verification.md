# Repository review fixes — 27 October 2026

Scope: v0.9.0 Android source, backend handler and shared modules, Simulator gateway, SQL tests, CI/release workflows and documentation. This pass does not certify production, enable live ordering or obtain provider approval. No screenshots are committed for it; see the size budget in [contributing](../CONTRIBUTING.md).

## Defects fixed

- Totals stayed on yesterday while the app process lived past midnight; entries logged then were saved under the old date and dropped on the next start. The tracker now moves to today on resume, at midnight and before every change.
- The real build hard-coded privacy notice 2026-10-02.3 while the backend and notice used 2026-10-04.1. Gradle now reads the backend constant; consents given under an older notice follow the existing "notice changed" path once.
- A recreated activity replayed the used WorkOS sign-in link and showed "Start signing in again."
- An unresolved checkout reserved its total from every later day's allowance. The reservation now applies only on the day it started, and `scripts/operator.ts` records support-verified outcomes for blocked checkouts.
- One global loading flag dropped user actions during background sync and labelled sync as "Finding your meals…". Requests now queue; each operation reports its own progress.
- Cancelled payment polling marked a checkout unresolved.
- Found during the rewrite: erasing device data overwrote the cloud profile with blank defaults; deleting the account replaced the separate local-only profile; saving goals collapsed logged intake ranges to their low end; notices such as "Log what I ate" guidance and Assistant confirmations were never shown; a background sync showed "Profile, preferences and tracker synced." in error colour on Home; rejected Assistant changes were reported as confirmed; `checkout-recovery.sql` committed its synthetic rows.

## Changes

- Android: focused state holders over pure, tested rules; typed checkout models; string resources; ktlint via Spotless; AGP 9.4, Kotlin 2.4, Gradle 9.8, compile SDK 37, Compose BOM 2026.09, Coil 3 with memory-only image caching, OkHttp 5, Room on KSP with exported schema; R8-shrunk non-debuggable release build on the stable preview certificate; `versionCode` from the version; Swiggy return link only in the real flavor; screen restored after process death; unused screens, coupon request, reserved-food tile and font removed.
- Backend: injectable handler with route tables; request IDs on every response with privacy-safe failure logs; versioned encryption keys with rotation and re-encryption tooling; the selected address page as an encrypted lookup hint; `deno fmt` and `deno lint` clean; lease completion no longer throws from `finally`.
- CI: Deno format/lint, handler, key and operator tests, SQL tests against a disposable local Supabase database, Kotlin formatting and the release build; Dependabot for Gradle, Actions and pip; the release workflow publishes the release build and refuses a debuggable APK.

## Validation completed

- Android: 73 unit tests per flavor passed (previously 12), including the original ranking formula compared term-for-term over 2,000 random cases. Debug and release builds assembled; lint passed for each with no new warnings; Spotless passed.
- Deno 2.9.6: 63 backend, handler, key, operator and simulator tests passed; format, lint and type checks passed.
- All seven migrations applied and all four SQL tests passed in an embedded Postgres (PGlite) with stubs for Supabase's `auth` schema, platform roles and `pg_cron`. CI repeats them on the Supabase local database.
- Seven no-inference Assistant tests passed.
- Release APK: 2.0 MB (debug 32 MB), `versionCode` 900, not debuggable, same signing certificate as the debug build.
- API 35 emulator against a separate local copy of the Simulator gateway (the founder's running Simulator service was not touched):
  - Data saved by the previous version loaded in both flavors.
  - Search, an exhausted batch, revisiting passed meals, shortlist, lucky draw, mock cart, quantity change, bill, an expired UPI payment, retry and confirmation all worked.
  - Home showed the in-flight ₹247 as reserved; after confirmation it counted once as spent.
  - "Log as eaten" added the estimate once and showed its notice.
  - The app survived theme and font-size changes and restored My day with saved totals after process death.
  - Moving the clock past midnight with the same process reset the day's totals on return.
- Real release build on the same emulator:
  - Privacy shows notice 2026-10-04.1.
  - The check-in and its undo worked.
  - The day editor saved a goal.
  - No crashes were logged.

## Deployment and release

- In GitHub Actions, all seven migrations applied to the Supabase local database and the four SQL tests passed before the merge ([PR #8](https://github.com/acephos/hungii/pull/8)).
- `hungii-api` version 14 was deployed to hungii-mumbai on 27 October 2026 with server-side bundling and JWT verification still off; version 13 was the previous one. Live checks: `/welcome` returned 200; an unauthenticated request and a forged bearer token returned `HUNGII_LOGIN_REQUIRED` with a request ID in the header and body; an unknown Swiggy callback state returned `HUNGII_CALLBACK_EXPIRED`, which exercises the key configuration and database. No migration or new secret was needed.
- The [v0.9.0 prerelease](https://github.com/acephos/hungii/releases/tag/v0.9.0) was published by the release workflow. The downloaded `hungii.apk` (2.0 MB) matched `SHA256SUMS.txt`, reports version 0.9.0 (code 900), is not debuggable and carries the stable preview certificate, so it installs over 0.8.1.

## Remaining limits

- The sign-in link fix, notice-version handling and the delete/erase account paths were checked by review and unit tests of their parts, not with live WorkOS on a device.
- Key rotation, re-encryption and checkout resolution were tested against fakes, not the deployed database; follow the [runbook](../docs/incident-runbook.md) and dry-run first.
- Address selections saved by version 14 are not readable by earlier backends, so a rollback asks those users to choose their address again.
