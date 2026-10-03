# Hungii simulator

A Hungii-authored synthetic Swiggy Food MCP server, deterministic cart/payment gateway, and opt-in cloud Assistant. This is not a Swiggy sandbox, approval, live integration, or a nutrition dataset. No real money or orders are created.

## Run without paying

From the repository root, start the gateway:

```sh
npx --yes deno@2.9.6 run --config supabase/functions/deno.json --allow-net=127.0.0.1 --allow-env demo/gateway.ts
```

The gateway uses localhost:8788; its MCP server uses localhost:8890/food. It accepts real MCP initialization, list-tools and tool-call requests through the official TypeScript SDK. Device sessions have independent carts and orders; restarting clears them. Address listing/selection precedes discovery. Android automatically selects the first synthetic address; Accounts lets you change it.

For a phone on the same Tailscale network:

```sh
HUNGII_DEMO_BIND=YOUR_TAILSCALE_IPV4 npx --yes deno@2.9.6 run --config supabase/functions/deno.json --allow-net=127.0.0.1,YOUR_TAILSCALE_IPV4 --allow-env demo/gateway.ts
```

Set `hungii.demoApiUrl=http://YOUR_TAILSCALE_IPV4:8788/api` in gitignored `android-prototype/local.properties` before building `assembleDemoDebug`. The default is `http://10.0.2.2:8788/api` for an emulator. The gateway permits loopback/Tailscale binding; do not expose its unauthenticated demo API to the public internet. The founder preview uses the configured repository variable `HUNGII_DEMO_API_URL`. The computer must stay on and run both services.

### Phone cannot connect

1. Open Tailscale on the phone and switch it to **Connected**, using the same tailnet as the computer. Ordinary Wi-Fi/mobile internet alone does not reach this private simulator.
2. Keep the computer awake with the gateway running. Open `http://YOUR_TAILSCALE_IPV4:8788/health` on the phone; it should return `status: ready`.
3. Return to Hungii → Accounts → **Refresh connection** (or **Reconnect simulator**). The synthetic address is selected automatically. Close Accounts and tap **Find my next meal** on Home.

Version 0.7.2 reports unreachable simulator connections with the destination and these recovery instructions. Synthetic sessions do not require a Swiggy login or token-retention consent. The cloud Assistant still has a separate opt-in.

### Restart after reboot or a crash

On Linux with a systemd user manager and Deno 2.9.6 on PATH, install gateway startup from this checkout:

```sh
python3 scripts/demo-services.py --bind YOUR_TAILSCALE_IPV4
```

After configuring the Assistant virtual environment/key below, add `--assistant` to enable it too. The installer writes `hungii-simulator.service` and optionally `hungii-agent.service` under `~/.config/systemd/user/`, backs up changed units, verifies them, enables startup and restarts them. It uses this checkout's absolute path and installed runtimes. Only loopback or a Tailscale IPv4 is accepted. Keys remain in the ignored environment file; none are copied into service units.

Services restart on failure; the gateway retries if Tailscale is not ready at startup. They start with your user manager; the founder computer already has user lingering enabled to start that manager at boot. Other machines may start the manager only after login. No APK installs host services. Check with `systemctl --user status hungii-simulator hungii-agent`; restart with `systemctl --user restart hungii-simulator hungii-agent`; stop with `systemctl --user stop hungii-simulator hungii-agent`. To disable startup, use `systemctl --user disable --now hungii-simulator hungii-agent`. Do not also launch manual copies on the same ports. The computer must stay awake and the phone must be on Tailscale.

## Cloud Assistant

No model runs locally. Create a Groq Free account/key, remain on Free, then:

```sh
python3 -m venv .venv-agent
.venv-agent/bin/pip install -r demo/requirements-agent.txt
cp demo/.env.agent.example demo/.env.agent
chmod 600 demo/.env.agent
# Enter the key in demo/.env.agent using your editor, never git or an APK.
.venv-agent/bin/python demo/agent.py
```

Google ADK + LiteLLM uses `groq/openai/gpt-oss-20b`, with no paid fallback. The FastAPI agent listens only on localhost:8789. The Android Assistant asks for opt-in before sending text/transcribed speech and selected tracker context to cloud inference. Groq processing is outside India. Provider login, real addresses, real provider payloads and payment credentials are not automatically supplied by this demo. Free text can contain whatever you type; avoid entering secrets or real provider records. Voice recognition is on-device where Android supports it; typed input works otherwise. There is no spoken AI reply yet. See [privacy data paths](../docs/privacy-notice-draft.md).

The agent can read selected Food MCP tools and propose searches, tracker changes and app navigation. Native confirmation applies changes; the LLM cannot place orders, settle payments or confirm orders. Only one turn is admitted at a time; overlapping requests return retry-later immediately. Turns are bounded to five model calls, four executed tools and four turns/minute, with a 55-second work deadline and up to five seconds for cleanup. The app passes up to four recent messages and explicit daily goals/allowance so follow-ups have context. Chat is not persisted by Hungii across app restarts or server turns; this is not a guarantee about Groq retention. Free provider limits still apply; errors offer manual filters, never a fabricated AI answer. Tool responses are compacted; internal reasoning is excluded from Groq history.

## Checkout demonstration

Swipe to three finalists → shuffle face down → choose → add to mock cart. Cart updates preserve menu IDs, portions, addons and quantities. Offers are evaluated by final payable amount. One fixture deliberately makes chicken + mint raita cheaper than chicken alone by meeting a coupon threshold. The suggestion discloses extra calories and requires a tap.

Review the complete bill/address and choose returned UPI, mock QR or COD. Explicit confirmation calls `place_food_order`. UPI remains pending; use **Simulate success/failure/cancel**. Those controls are Hungii-owned endpoints, not invented Swiggy tools. The gateway calls `check_payment_status`, then Food `confirm_order` with orderId/addressId/lat/lng and optional cartId only after successful terminal status. Failure/expiry keeps the cart. Cart changes invalidate pending payments. Repeated checkout request IDs and confirmation do not duplicate orders. COD confirms immediately. Receipt calls order details, delivery status and tracking tools. Nutrition is logged only on an explicit “eaten” action.

**Open Swiggy** launches its installed Android app, with website fallback. The user creates and pays for the actual basket manually. No undocumented cart deep link is used; invented restaurants cannot be found there. Package verified from [Swiggy's Play listing](https://play.google.com/store/apps/details?id=in.swiggy.android).

## Contract limits

Current official Food documentation lists **20 tools**. `tool-schemas.json` snapshots documented top-level parameters; the live provider still advertises schemas dynamically. `cartItems` nested fields, mock currency rupees, quantity limits and deterministic payment outcomes are Hungii simulator assumptions because those member schemas are not published. They do not unlock live writes. Responses are representative documented shapes, not promised byte-for-byte provider behavior. See [contract research](../docs/research/mock-mcp-contract-research.md).

Nutrition intervals are illustrative synthetic estimates, not lab-verified or calibrated uncertainty. They live in Hungii metadata outside MCP provider payloads. Photos are bundled representative prototype assets. No scraped live menus are stored. Fixture order history is session-local; receipt/log/payment state is not recovered after app/server termination. Live Swiggy writes stay blocked and require approvals plus verified authenticated item contracts.

## Checks

```sh
npx --yes deno@2.9.6 test --config supabase/functions/deno.json --allow-env supabase/functions/_shared/ demo/simulator_test.ts
```

CI uses synthetic data and no Groq key. The suite covers successful/failed/expired/cancelled payments, cart revisions, coupon savings, invalid items, MCP protocol sessions, device isolation and the live-write gate.

After installing the Assistant dependencies, run `.venv-agent/bin/python -m unittest demo.agent_test -v`. CI installs the same pinned dependencies and exercises input limits, busy-turn rejection, setup failures and the SDK adapter without inference. Both HTTP services cap actual request bytes, including requests with no Content-Length. Gateway health remains available at session capacity, and an explicit reset can discard a failed synthetic handshake.

Android CI also runs `:app:testRealDebugUnitTest :app:testDemoDebugUnitTest`. The HTTP-boundary regression closes a real test server before a request and verifies a recoverable connection error; companion cases check successful responses and preserved gateway errors.
