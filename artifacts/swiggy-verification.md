# Hungii 0.4 readiness verification

Verified 2 October 2026. The free Mumbai project is deployed; see the [current control matrix and evidence](../docs/approval-readiness.md). Both `assembleRealDebug` / `assembleDemoDebug` and both corresponding lint tasks pass. The deployed function is version 4. Final unauthenticated API and invalid callback checks return 401 and 400 respectively, both with `x-sb-edge-region: ap-south-1`. Four applied migration versions are recorded in the standard Supabase CLI history table.

Current packages, version `0.4-readiness`, version code 3:

- `artifacts/hungii-android-prototype.apk`, SHA-256 `74346887178252bb582fc6b3ccd8636218ff7cbc65d0b126f040955a9bcca561`.
- `artifacts/hungii-android-demo.apk`, SHA-256 `a8653d87cbf1884c2882f7dfc618e92c294a4173027b1b90bd56dac77ce72f40`. Its synthetic backend uses the Android emulator's `10.0.2.2`; it is not configured for physical-phone discovery.

Native evidence: [encrypted tracker survived reopen](hungii-encrypted-tracker-restart.png), [confirmed device erasure](hungii-device-erasure.png), [synthetic discovery](hungii-live-path-demo.png), [read-only review](hungii-read-only-review.png). [Public synthetic review video](https://qvqnzsqrxejdlvnbqwcs.supabase.co/storage/v1/object/public/hungii-public-review/hungii-approval-demo.mp4).

Google app OAuth and real Swiggy staging remain pending. The production build does not substitute synthetic provider responses. The following is historical 0.3 evidence; its package checksum and unprovisioned status no longer describe the current build.

## Historical 0.3 integration preparation

Verified locally on 2 October 2026. This build starts disconnected: Supabase and approved Swiggy Builders access are not available in this workspace. No live meal discovery, OAuth or checkout success is claimed.

## Checks completed

- Android `assembleDebug lintDebug` passed with JDK 17 and SDK 35.
- Deno backend type check passed; all eight normalization/encryption tests passed. These cover closed/sold-out filtering, stable identifiers, explicit money units, authoritative cart totals, menu availability, one-time collection fallback, MCP envelopes, and encryption tampering/cross-user replay.
- APK signature verification passed with the existing Android debug certificate.
- Android 15 emulator: disconnected home starts at zero consumed/spent, account setup message is visible, Google login is disabled without configuration, and discovery contains no fictional meal cards.
- Changed remaining allowance to ₹350, force-stopped the app, installed the final build over it, and reopened. The ₹350 tracker value persisted through Room.
- Tailscale download returned HTTP 200 and the same bytes/checksum as the packaged APK. No physical phone was modified.
- `git diff --check` passed.

The emulator initially failed with its default graphics configuration; verification succeeded after restarting with host graphics. Screenshots: [home](swiggy-home.png), [accounts](swiggy-accounts.png), [discovery](swiggy-discover.png). Home shows editable default goals; its values are not personalized recommendations.

## Package

- File: `artifacts/hungii-android-prototype.apk` (ignored by git).
- Version: `0.3-swiggy`, version code 2.
- Size: 18,845,952 bytes.
- SHA-256: `8060653fbc731f2f7ee29039fc57b650b7032b23cbdbc6b289eb89738a5969be`.
- Private download while this development server is running: `http://100.103.202.33:8787/hungii.apk`.

## Checks requiring real services

The SQL migration has not been applied to a database. Two-user RLS/isolation, Google sign-in, Swiggy PKCE callback/replay/expiry/cancellation, authenticated runtime tool schemas, approved staging endpoints, price units, expired/revoked tokens, real addresses, cart/offer responses and rate-limit handling still require staging verification. Follow [setup](../docs/swiggy-setup.md).

This is a read-only adapter: it neither adds the drawn meal to a cart nor places an order. Coupon threshold optimization requires verified basket mutations and quotes. Missing nutrition stays unknown; a calibrated estimate source is still needed for macro-based ranking. Account-specific local tracker persistence is implemented but has only been exercised as an offline guest in this check. The cloud tracker has explicit push sync; pull/conflict resolution is pending.
