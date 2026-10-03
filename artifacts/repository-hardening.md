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
- GitHub automatic deletion of merged branches and private vulnerability reporting are enabled. GitHub PR/release status remains visible in the repository; remote branch cleanup and required-check protection will be applied with publication.

The shared real-backend request boundary is tested in source; the existing hosted WorkOS/Supabase deployment was not redeployed or comprehensively revalidated in this pass. Known limits remain: synthetic in-memory carts/orders, no app receipt/payment recovery after restart, free inference quotas, awake host/Tailscale requirement and all existing production gates.
