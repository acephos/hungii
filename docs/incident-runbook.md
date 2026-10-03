# Hungii operating runbook

Prepared 2 October 2026 for a limited, approval-gated Food beta. Support/on-call ownership and active alert routing need founder confirmation before production; this document does not claim continuous support coverage.

## Simulator phone cannot connect

1. Check phone Tailscale is connected to the computer's tailnet. Wi-Fi/mobile internet alone does not reach the private gateway.
2. Run `systemctl --user status hungii-simulator hungii-agent` on the computer. Restart installed services with `systemctl --user restart hungii-simulator hungii-agent`; install them using [Simulator setup](../demo/README.md) on a new machine. Do not run manual copies on occupied ports.
3. Read the gateway address from the app error or local build settings. On the founder host, `curl --fail --max-time 5 http://100.103.202.33:8788/health` should return `status: ready`; substitute your own configured address elsewhere. Health proves gateway availability; address selection/discovery verifies MCP too.
4. In Hungii, use Accounts → Refresh connection, then Home → Find my next meal. Synthetic sessions need no Swiggy login/retention consent.
5. For Assistant-only failures, check `curl --fail --max-time 5 http://127.0.0.1:8789/health`. `configured: false` means the ignored Groq key file is missing; do not paste it into logs/chat. Busy/quota responses require waiting or manual filters. No paid fallback is configured.

Reboots previously stopped manually started processes. Enabled user services now restart on failure and start with the user manager; the founder host already has lingering enabled for boot startup. The computer must stay awake. Server restart clears synthetic carts/orders; receipt/payment UI recovery remains unfinished. A full synthetic session table requires a controlled restart and loses in-memory state. These are demo limits, not provider incidents.

## Connection, rate or provider incident

1. Stop Food calls when a connection is blocked or rejected. Keep the live gate false during investigation. Do not repeatedly initialize, reuse a rejected token or rotate accounts to bypass limits.
2. Respect durable Retry-After. Display retry-later; do not sleep a user action beyond its deadline. Disconnect/deletion still removes Hungii's local credentials even when remote revocation cannot run.
3. Inspect only minimal hashed user/session identifiers, tool name, duration/status and deprecation warnings in Supabase function logs. Do not copy token values, addresses, phone numbers, whole menus, requests/results or tracker plaintext into a support ticket or AI tool.
4. If provider help is needed, prepare a minimal report for `builders@swiggy.in`. Sending any email remains a separate founder-authorized action; current instruction is draft only. Use Swiggy diagnostic/report tools only within verified docs and user consent.
5. Failed/uncertain initialization stops automatic recovery. Check staging/service health, remove the old connection through explicit disconnect, then allow one new user-authorized connection. Investigate auth-event accounting before reopening the gate.

## Privacy incident or request

Use the authenticated self-service cloud deletion/account deletion/device erasure controls. The backend atomically erases provider connections, OAuth states, session metadata, consent epochs and tracker rows before deleting the WorkOS user. A WorkOS deletion failure leaves a retryable deleting account; do not restore erased data or report success. Successful deletion retains a hashed identity fence for 24 hours, then purge removes it. Native saved-meal withdrawal/device erasure removes rows and rotates the per-owner data key. A remote logout failure is reported separately; never represent it as confirmed revocation. Direct requests affecting the user's Swiggy account records to Swiggy and coordinate deletion of Hungii's copies.

For suspected exposure, disable live calls, preserve minimal necessary evidence under restricted access, revoke affected connections and rotate only the affected secrets under a reviewed recovery plan. The single application encryption key cannot be replaced blindly while retained ciphertext exists. Assess required notifications with the responsible owner/provider; no legal deadlines are fabricated in this runbook.

## Monitoring and release

Check Cron `hungii-privacy-purge` for successful execution, project Free usage and function region/error logs. A paused Free project cannot run purge or serve requests; access cleanup runs after resumption. Do not add warmup traffic to evade pausing. No paid upgrade or backup add-on is authorized. Establish an approved encrypted backup/restore drill and alert delivery before relying on this account for production availability.

Read deprecation warnings daily during staging; set real alert routing before launch. Start with founder/seed accounts, then an explicitly capped private cohort, and increase only after documented green checks. Keep cart writes/order placement disabled until separately approved and tested. Record at least 48 hours green staging and written production confirmation. Reverify schema, consent/retention and region assumptions after provider/dependency changes.

## WorkOS account-service incident

Live session checks fail closed during a provider outage. Android sign-out always clears its local credentials; it reports remote sign-out failure separately. Never log JWTs, refresh tokens, account emails or API keys. Investigate pending deletions using restricted metadata, verify the request's identity, complete provider deletion and mark the account deleted with the 24-hour fence; never manually reactivate a deleting account. Confirm platform/API-key scopes and the environment-default issuer against the current WorkOS docs before credential rotation.

## Profile-sync incident

Keep local edits when offline. A changed server revision returns HUNGII_SYNC_CONFLICT; show the cloud/local choice and never retry it as a blind overwrite. Stop sync persists per device and cancels queued uploads; signing in is not permission to upload. A missing cloud row after expiry/deletion does not automatically recreate a prior copy. An explicit user choice can restore a cloud profile or authorize a new save. Inspect only minimized error/revision metadata, not tracker or preference plaintext.
