# Simulator service recovery — 3 October 2026

The phone and computer were online on Tailscale, but the simulator gateway and cloud Assistant processes were absent after a host reboot. There were no enabled Hungii startup services.

Before recovery, `curl --fail --silent --show-error --max-time 5 http://100.103.202.33:8788/health` exited 7 with connection refused. The Assistant health request also exited 7. No listener existed on ports 8788, 8789 or 8890.

Installed and enabled local systemd user units at `/home/ace/.config/systemd/user/hungii-simulator.service` and `/home/ace/.config/systemd/user/hungii-agent.service`. Both use the existing repository and installed runtimes, restart on failure, and start with the user manager. User lingering was already enabled. Simulator startup retries allow Tailscale time to become ready. Gateway exposure remains on the computer's Tailscale IPv4; MCP and Assistant stay on loopback. The existing ignored Assistant credential file is used without copying its key into unit files.

Validation:

- `systemd-analyze --user verify` passed for both units.
- Both units are enabled, active and running.
- The original gateway health command now returns `status: ready`; Assistant health returns `configured: true`.
- A distinct synthetic session through the phone's configured gateway endpoint passed connection status, address listing/selection and meal discovery. Four meal cards were returned; the trace included MCP address and restaurant calls.

No APK change or new release is required. Phone UI recovery remains for the user to confirm by refreshing connection. The computer must remain awake; this does not make the simulator an offline phone service. Restarting the server cleared its previous in-memory demo carts and orders.
