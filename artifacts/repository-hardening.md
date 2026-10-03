# Repository hardening — 3 October 2026

Scope: current Android, backend and synthetic MCP/Assistant source; repository hygiene, documentation and GitHub configuration. Earlier WorkOS/database checks remain dated evidence; this pass does not certify production or obtain provider approval.

Before fixes, gateway regressions failed for malformed JSON and session-capacity health checks. Assistant boundary regressions reproduced invalid input exceptions, queued-turn timeouts and uncaught model-setup failures. Synthetic connection reset could not discard a rejected handshake.

Changes enforce actual streamed UTF-8 request-byte limits, validate object/chat shapes, keep health available without allocating sessions, recover failed synthetic handshakes and reject overlapping cloud turns immediately. Model setup and cleanup use sanitized errors; MCP resources close in the task that opened them. Restartable services can be installed from this checkout without embedding secrets or a founder-specific path.

Research and historical correspondence were separated from current setup/status documents; local links, data-path/privacy statements and test instructions were updated. Published artifacts and dated results remain historical evidence.

Validation completed:

- 34 Deno backend/protocol/simulator regressions passed, including live-write fencing, chunked UTF-8 size limits, synthetic connection reset and MCP transport cleanup.
- Seven no-inference Python Assistant/adapter tests passed.
- Four Android transport tests passed in each flavor; both APKs assembled and both lint tasks passed. Deprecated directional icons now use automatically mirrored variants.
- The repository path/secret-format/local-link guard and actionlint 1.7.12 passed. A known-private-value scan found no credential matches across publishable files and 224 remote/tag historical blobs; values were never printed.
- The generated systemd units passed staged validation, installed successfully and are enabled/running. A public bind address was rejected by the installer.
- Through the configured phone gateway address, address selection and real synthetic MCP discovery returned four meal cards. One opt-in Groq turn returned a reviewable allowance proposal without any tracker mutation.
- GitHub automatic deletion of merged branches, private vulnerability reporting, secret scanning/push protection and Dependabot alerts are enabled. `main` requires up-to-date `check`/`assistant` jobs from the GitHub Actions app, PRs and resolved conversations, including administrators; force pushes/deletion are disabled. Three obsolete remote branches were verified as ancestors of main and deleted atomically. Published releases/tags remain intact. [PR #2](https://github.com/acephos/hungii/pull/2) records publication and CI status.
- PyPI reported no active advisories for the six explicitly pinned Assistant/runtime dependencies; the enabled GitHub alert list was empty at this check. This is not a complete audit of transitive dependencies, Android packages or unpublished vulnerabilities.

The shared real-backend request boundary is tested in source; the existing hosted WorkOS/Supabase deployment was not redeployed or comprehensively revalidated in this pass. Known limits remain: synthetic in-memory carts/orders, no app receipt/payment recovery after restart, free inference quotas, awake host/Tailscale requirement and all existing production gates.

## Android backup follow-up

Final lint review found that `allowBackup=false` alone is not sufficient to opt out of every Android 12+ manufacturer's device-transfer behavior. [Android's primary guidance](https://developer.android.com/identity/data/autobackup) documents this distinction. Preview 0.7.4 adds legacy backup rules and separate cloud-backup/device-transfer exclusions for every supported storage domain; optional Hungii cloud sync remains independent. No actual leaked backup or transfer was observed. This is configuration hardening, not verification of every OEM transfer implementation.

Both 0.7.4 flavors passed all eight Android transport tests, assembly and lint. Inspection of the packaged manifest/resources confirmed version code 12, both rule references and all nine legacy/eighteen modern exclusions. The backup warning is resolved; 28 existing lint advisories remain per flavor, with no lint errors.
