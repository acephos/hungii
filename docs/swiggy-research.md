# Swiggy research for Hungii

Verified on 2026-10-01 against Swiggy Builders Club first-party documentation. This is documentation research, not a live authenticated MCP smoke test. The browser fetcher read `llms.txt` but rejected the Markdown content type for several page twins; those exact `.md` URLs were fetched directly over HTTPS instead.

## Smoke test and API scope

The supplied prompt expects 14 Food tools, but the current Food reference explicitly says **20 tools**, and the current `llms.txt` lists 20 Food tool pages. The historical reason for the 14 figure was not established; it must not override the current reference. These are four MCP servers, not a documented general REST developer API suite. [Documentation index](https://mcp.swiggy.com/builders/llms.txt), [Food reference](https://mcp.swiggy.com/builders/docs/reference/food/index.md).

| Server | Current tool count | Documented surface |
| --- | ---: | --- |
| Food | 20 | Delivery-address management, restaurant and dish discovery, menus and customizations, cart and coupons, payment, ordering, history and tracking. |
| Instamart | 19 | Delivery addresses, grocery and essentials search, frequently ordered items, cart and coupons, payment, checkout, history and tracking. |
| Dineout | 12 | Restaurant discovery and details, slots, free reservations and paid prebook deals, payment, booking status and cancellation. |
| Scenes | 15 | Event discovery and search, venue and event details, show and ticket selection, cart, free or paid checkout, booking status. |

Counts total **66 server-specific tool entries**; common names appear on multiple servers. [Food](https://mcp.swiggy.com/builders/docs/reference/food/index.md), [Instamart](https://mcp.swiggy.com/builders/docs/reference/instamart/index.md), [Dineout](https://mcp.swiggy.com/builders/docs/reference/dineout/index.md), [Scenes](https://mcp.swiggy.com/builders/docs/reference/scenes/index.md).

### Food's verified 20-tool inventory

`create_address`, `delete_address`, `get_addresses`, `get_restaurant_menu`, `search_menu`, `search_restaurants`, `apply_food_coupon`, `fetch_food_coupons`, `flush_food_cart`, `get_food_cart`, `update_food_cart`, `check_payment_status`, `confirm_order`, `get_payment_options`, `place_food_order`, `get_food_delivery_status`, `get_food_order_details`, `get_food_orders`, `track_food_order`, `report_error`. [Food reference](https://mcp.swiggy.com/builders/docs/reference/food/index.md).

The remaining tool inventories and schemas should be fetched from their domain reference before implementation. Avoid flattening shared names such as `confirm_order` without retaining the server identity. [Reference index](https://mcp.swiggy.com/builders/docs/reference/index.md).

## Authentication and access

Direct integrations use OAuth 2.1 with PKCE S256. The user enters phone and OTP in Swiggy's browser consent UI; its internal OTP endpoints are explicitly outside the third-party contract. MCP clients can use Dynamic Client Registration. The documented access token lasts five days; refresh-token issuance is not wired in v1.0 despite being advertised in metadata, so expiry or revocation requires reauthorization. Redirect URIs require exact matching and HTTPS, with localhost allowed for development. Fine-grained read/write and domain scopes are roadmap items, not current guarantees. [Authenticate](https://mcp.swiggy.com/builders/docs/start/authenticate.md).

Hungii serving multiple users must keep separate, secure per-user tokens and act with each user's authorization. The enterprise delegated-auth guide describes this integration pattern; it does not authorize a shared account token for all users. [Delegated auth](https://mcp.swiggy.com/builders/docs/start/enterprise/delegated-auth.md).

Production is invite-based and reviewed. Build a localhost experience with a local stub, submit an application and demo video, obtain staging access, then qualify for production after at least 48 hours of green staging. Staging uses seeded data and does not place real orders. Application details include redirect URIs, domains, use case, expected volume, and technical contact. Enterprise onboarding can take four or more weeks. [Access and onboarding](https://mcp.swiggy.com/builders/docs/operate/access.md).

## Capabilities that affect the product

- **Food customization requires live identifiers.** `search_menu` requires an address returned by `get_addresses`. Items use either `variations` or `variantsV2`; preserve that format. Add the variant first, then inspect valid add-ons. The documented filter supports vegetarian-only or mixed results; it has no nonvegetarian-only mode. [Dish search](https://mcp.swiggy.com/builders/docs/reference/food/search_menu.md).
- **Menu browsing is capped.** `get_restaurant_menu` returns at most 150 unique dishes and can flag truncation. Ordering needs a scoped dish search for full customization information. [Menu browse](https://mcp.swiggy.com/builders/docs/reference/food/get_restaurant_menu.md).
- **Instamart variants and stock are local.** Search at a selected saved address and ask the user which product variant they want. `update_cart` replaces the entire cart, so a conversational addition must preserve existing items deliberately. [Product search](https://mcp.swiggy.com/builders/docs/reference/instamart/search_products.md), [Cart update](https://mcp.swiggy.com/builders/docs/reference/instamart/update_cart.md).
- **Reservations have distinct free and paid paths.** Dineout books free slots directly; paid prebook deals first require a cart and then UPI. Manual booking requests need confirmation. Slot and deal identifiers must come from availability results. Guest counts are 1–20. Cancellation is available subject to account support and confirmation. [Table booking](https://mcp.swiggy.com/builders/docs/reference/dineout/book_table.md).
- **Scenes has an explicit checkout state.** `create_event_cart` returns `FREE`, `UPI`, or `UNAVAILABLE`; branch on that result. Use the returned show timestamp rather than calculating it. Free tickets also need confirmation. `PENDING_PAYMENT` and `PROCESSING` do not establish a completed booking. [Event cart](https://mcp.swiggy.com/builders/docs/reference/scenes/create_event_cart.md), [Event order](https://mcp.swiggy.com/builders/docs/reference/scenes/place_event_order.md).
- **Payment choices are live.** Offer only methods returned for the user's cart. Food supports UPI app links or desktop QR, Cash/COD when available, and Swiggy Money when returned. Never collect a UPI ID or VPA. Pending UPI payment requires status checking and successful order confirmation before announcing success. [Payment options](https://mcp.swiggy.com/builders/docs/reference/food/get_payment_options.md), [Food ordering](https://mcp.swiggy.com/builders/docs/reference/food/place_food_order.md).
- **Cart state is shared with the Swiggy app.** Refresh before modifying or confirming. Food carts belong to one restaurant; changing restaurant can clear them. Carts and orders are separate across servers, while authentication is shared. [Multi-turn state](https://mcp.swiggy.com/builders/docs/build/agent-patterns/multi-turn-state.md).

## Practical limits and documentation conflicts

The dedicated rate-limit page currently states enforcement at 70 requests per minute per authenticated user per server, with 30 write requests per minute and a burst allowance. It advises persistent sessions, sequential domain initialization, honoring throttling, and tracking no faster than every ten seconds. The production guide still says rate limits are not enforced in v1.0. Treat the dedicated rate-limit page as the operative documented limit and confirm the discrepancy at onboarding. [Rate limits](https://mcp.swiggy.com/builders/docs/operate/rate-limits.md), [Production guide](https://mcp.swiggy.com/builders/docs/build/ship-to-production.md).

The error reference says symbolic error codes are planned and **none are emitted today**, including `RATE_LIMITED`; the rate-limit introduction nevertheless describes it as an emitted error. Implement current error-message, HTTP-status and JSON-RPC classification, including HTTP 429, instead of assuming a stable symbolic registry. [Errors](https://mcp.swiggy.com/builders/docs/reference/errors.md), [Rate limits](https://mcp.swiggy.com/builders/docs/operate/rate-limits.md).

Order-placement operations are not safe to retry blindly on a lost response. The production guide recommends checking order or booking state before retrying, and requires user-visible confirmation of items and total. [Production guide](https://mcp.swiggy.com/builders/docs/build/ship-to-production.md).

Food has **immediate delivery only** in the documented v1 surface. The combined recipe diagram says “scheduled 10pm,” but its explicit scheduling gotcha says future-scheduled delivery is unsupported. Hungii can offer reminders to order later, without promising a scheduled Swiggy delivery. [Combined recipe](https://mcp.swiggy.com/builders/docs/build/recipes/combined.md).

There is no documented atomic checkout across servers, split payment, recurring automatic purchase, or guaranteed nutrition/allergen dataset in the reviewed tool references. These should remain research questions or Hungii-owned planning features, not advertised provider capabilities. Food's ordering tool directs cancellation to customer care rather than offering a cancellation tool; Scenes' listed surface also has no cancellation tool. [Food ordering](https://mcp.swiggy.com/builders/docs/reference/food/place_food_order.md), [Scenes inventory](https://mcp.swiggy.com/builders/docs/reference/scenes/index.md), [Reference index](https://mcp.swiggy.com/builders/docs/reference/index.md).

Hosted widget URLs and the widget opt-in header are described as a designed surface that is not live in v1.0. Build Hungii's own cards and checkout UI from tool data rather than depending on hosted widgets. [Widgets](https://mcp.swiggy.com/builders/docs/build/widgets.md).

Swiggy's compliance documentation limits use of its data to the immediate user task; analytics, advertising and training need separate consent and contractual handling. It also states that processing MCP responses outside India requires a signed DPA and transfer arrangements. These are Swiggy's stated partner requirements, not an independent legal assessment. Discuss storage and inference geography during onboarding. [Data and compliance](https://mcp.swiggy.com/builders/docs/operate/data-and-compliance.md).

## Product opportunities — inferences, not Swiggy features

| Hungii concept | User promise | Provider composition | Main implementation boundary |
| --- | --- | --- | --- |
| Tonight planner | “Give me three good plans for tonight within my budget.” | Food, Instamart, Dineout and Scenes supply options for staying in, hosting or going out. | Hungii owns ranking and itinerary; confirm each transaction separately. |
| Hosting helper | “Six friends are coming. Sort dinner, drinks and missing essentials.” | Food for prepared meals; Instamart for snacks, beverages and household supplies. | Two carts and checkouts; live fees and stock determine the final total. |
| Cook, order or go out | “What's the best dinner option given my time and budget?” | Instamart ingredients, Food meals and Dineout reservations. | Recipe planning and comparison are Hungii logic; do not claim guaranteed nutrition or exact costs before live quotes. |
| My usuals | “Restock my staples and show my usual lunch.” | Instamart frequently ordered items and both delivery order histories. | Suggest and reconfirm live items; reminders are safer than unsupported automatic recurring purchases. |
| Dinner and a show | “Plan a date with dinner and comedy nearby.” | Dineout reservations plus Scenes events and tickets. | Hungii owns timing and travel buffers; tickets and dinner remain separate bookings. |

These ideas derive from the verified domain capabilities above. The most distinctive product direction is a decision experience spanning a whole evening. A focused first build could use Food and Instamart for hosting, prove search → selection → live cart → confirmed checkout → tracking, then add Dineout and Scenes. Access, transaction reliability and user trust matter more than exposing every tool as a top-level feature.
