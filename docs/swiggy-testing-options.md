# Swiggy testing options for Hungii

Verified on 2026-10-02. This is documentation research, not an authenticated staging smoke test. The current [official index](https://mcp.swiggy.com/builders/llms.txt) listed 105 Markdown pages; all were fetched successfully and checked for staging, local stubs, mocks and test credentials. The browser rejected some Markdown content types, so the exact indexed URLs were fetched directly over HTTPS.

**Swiggy documents a seeded staging environment with no real orders, but does not document an anonymously accessible dummy API or publish a runnable local mock in the reviewed material. Hungii can build and test against its own local stub before obtaining access.** [Access and onboarding](https://mcp.swiggy.com/builders/docs/operate/access.md), [Developer quickstart](https://mcp.swiggy.com/builders/docs/start/developer/index.md).

| Option | Before access is granted? | Evidence and limits |
| --- | --- | --- |
| Hungii-owned local stub | Yes | Swiggy recommends building on localhost against a local dev stub. No stub package, download, fixture bundle or launch command was found in the indexed docs. |
| Swiggy staging | After staging access is issued; potentially before production approval | `mcp-staging.swiggy.com/{server}` has production-shaped interfaces backed by seeded data and no real orders. Developer staging credentials are issued during application review. |
| Production MCP | Reviewed developer onboarding applies | Production follows a working staging integration and at least 48 hours of green staging. Production ordering is a real transaction surface. |

The access and production requirements above come from [Access and onboarding](https://mcp.swiggy.com/builders/docs/operate/access.md) and the [production checklist](https://mcp.swiggy.com/builders/docs/build/ship-to-production.md). “No published mock found” describes this research scope; it does not establish that Swiggy has no private or future mock offering.

## What approval-free development means

The quickstart says steps 1–5 can run on localhost without approval. However, it does not supply a localhost server implementation, and its linked framework examples use `https://mcp.swiggy.com/food`. The access page is more explicit: use a local dev stub, or staging once access is available. A localhost **OAuth callback** also does not make the remote server a local mock. Interpret the approval-free path as building a locally simulated integration, not proof that Swiggy's hosted staging can be called without credentials. [Quickstart](https://mcp.swiggy.com/builders/docs/start/developer/index.md), [Framework examples](https://mcp.swiggy.com/builders/docs/start/developer/build-an-agent.md), [Access](https://mcp.swiggy.com/builders/docs/operate/access.md).

## Credentials, samples and transaction effects

The reviewed docs publish no shared staging login, test phone/OTP, anonymous token or static API key. General authentication uses OAuth 2.1 with PKCE, browser phone/OTP consent and Dynamic Client Registration. The auth guide primarily documents the production host; ask Swiggy for the approved staging OAuth configuration and test-account procedure rather than inventing staging auth URLs. [Authenticate](https://mcp.swiggy.com/builders/docs/start/authenticate.md), [Framework examples](https://mcp.swiggy.com/builders/docs/start/developer/build-an-agent.md).

Public tool pages provide request examples and output schemas suitable for designing synthetic fixtures. Example IDs such as `addr_01HXYZ` are not credentials or usable account data; the dish-search contract requires an address returned by `get_addresses`. [Saved-address schema](https://mcp.swiggy.com/builders/docs/reference/food/get_addresses.md), [Dish-search schema](https://mcp.swiggy.com/builders/docs/reference/food/search_menu.md), [Cart schema](https://mcp.swiggy.com/builders/docs/reference/food/get_food_cart.md).

Swiggy's public [MCP manifest repository](https://github.com/Swiggy/swiggy-mcp-server-manifest) currently contains a README with hosted-client configuration, rather than a runnable mock server. Its feature/access statements differ from newer Builders documentation, so it should not override the current onboarding contract.

Staging explicitly promises no real orders. The reviewed docs do not separately spell out sandbox UPI/wallet settlement behavior, so confirm payment testing with Swiggy before testing those flows. On production, Cash/COD placement is immediate, Swiggy Money debits inline, and UPI starts a pending payment flow. These endpoints must not be treated as test doubles. [Access](https://mcp.swiggy.com/builders/docs/operate/access.md), [Food order placement](https://mcp.swiggy.com/builders/docs/reference/food/place_food_order.md).

## Practical path for Hungii

Build synthetic fixtures for the current read-only Food flow: address selection/pagination, restaurant and dish search, customization data, empty/error results, and cart totals/offers. Simulate token expiry and throttling locally. This verifies Hungii's behavior against chosen fixtures; it does not validate actual Swiggy OAuth, undocumented item schemas, live money units or provider availability.

Submit the localhost demo with the Builders application and request staging access during review. Ask for staging OAuth settings, permitted test accounts and payment simulation details. Replace synthetic fixtures with authenticated staging checks before enabling provider writes or representing the integration as live. The demo/application workflow and developer support contact are documented in [Access](https://mcp.swiggy.com/builders/docs/operate/access.md) and [Support](https://mcp.swiggy.com/builders/docs/operate/support.md).
