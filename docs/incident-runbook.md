# Hungii operating runbook

Prepared 2 October 2026 for a limited, approval-gated Food beta. Support/on-call ownership and active alert routing need founder confirmation before production; this document does not claim continuous support coverage.

## Connection, rate or provider incident

1. Stop Food calls when a connection is blocked or rejected. Keep the live gate false during investigation. Do not repeatedly initialize, reuse a rejected token or rotate accounts to bypass limits.
2. Respect durable Retry-After. Display retry-later; do not sleep a user action beyond its deadline. Disconnect/deletion still removes Hungii's local credentials even when remote revocation cannot run.
3. Inspect only minimal hashed user/session identifiers, tool name, duration/status and deprecation warnings in Supabase function logs. Do not copy token values, addresses, phone numbers, whole menus, requests/results or tracker plaintext into a support ticket or AI tool.
4. If provider help is needed, prepare a minimal report for `builders@swiggy.in`. Sending any email remains a separate founder-authorized action; current instruction is draft only. Use Swiggy diagnostic/report tools only within verified docs and user consent.
5. Failed/uncertain initialization stops automatic recovery. Check staging/service health, remove the old connection through explicit disconnect, then allow one new user-authorized connection. Investigate auth-event accounting before reopening the gate.

## Privacy incident or request

Use the authenticated self-service cloud deletion/account deletion/device erasure controls. Backend Auth deletion cascades provider connections, OAuth states, session metadata, consent epochs and tracker rows. Native saved-meal withdrawal/device erasure removes rows and rotates the per-owner data key. A remote logout failure is reported separately; never represent it as confirmed revocation. Direct requests affecting the user's Swiggy account records to Swiggy and coordinate deletion of Hungii's copies.

For suspected exposure, disable live calls, preserve minimal necessary evidence under restricted access, revoke affected connections and rotate only the affected secrets under a reviewed recovery plan. The single application encryption key cannot be replaced blindly while retained ciphertext exists. Assess required notifications with the responsible owner/provider; no legal deadlines are fabricated in this runbook.

## Monitoring and release

Check Cron `hungii-privacy-purge` for successful execution, project Free usage and function region/error logs. A paused Free project cannot run purge or serve requests; access cleanup runs after resumption. Do not add warmup traffic to evade pausing. No paid upgrade or backup add-on is authorized. Establish an approved encrypted backup/restore drill and alert delivery before relying on this account for production availability.

Read deprecation warnings daily during staging; set real alert routing before launch. Start with founder/seed accounts, then an explicitly capped private cohort, and increase only after documented green checks. Keep cart writes/order placement disabled until separately approved and tested. Record at least 48 hours green staging and written production confirmation. Reverify schema, consent/retention and region assumptions after provider/dependency changes.
