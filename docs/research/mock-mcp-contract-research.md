# Local Food MCP contract research

> Dated research. This preserves the original findings or correspondence; use [current status](../approval-readiness.md) and the [documentation index](../README.md) for today’s implementation and setup.

Verified against Swiggy Builders Club on 2 October 2026. This is a design contract for Hungii's synthetic simulator, not a claim of live Swiggy access or payment processing. Every restaurant, address, price, coupon, nutritional value, payment and order in the simulator must be authored fixtures.

## Current scope

The current [documentation index](https://mcp.swiggy.com/builders/llms.txt) links **20 Food tools**. The [Food server reference](https://mcp.swiggy.com/builders/docs/reference/food/index.md) explicitly says “Tools available: 20.” The original 14-tool smoke-test answer is outdated; implementing only that older set would omit documented address and payment functionality. None of these 20 is a Food cancellation tool.

Each input row below is verified against its linked individual `.md` reference. `?` means optional. Types are the documentation's types; additional simulator bounds, such as maximum cart quantity or storage size, are Hungii policy rather than an asserted Swiggy contract.

| Tool | Documented input |
| --- | --- |
| [get_addresses](https://mcp.swiggy.com/builders/docs/reference/food/get_addresses.md) | `page?:number` (1-based, default 1), `pageSize?:number` (default/max 10) |
| [create_address](https://mcp.swiggy.com/builders/docs/reference/food/create_address.md) | Required `fullAddress,addressLine,addressLine2,city,postalCode,userName,userPhone:string`; required `addressCategory:"HOME"\|"WORK"\|"OFFICE"\|"FRIENDS_AND_FAMILY"\|"OTHER"`. Optional `locality,addressTag,receiverName,receiverPhone:string`, `latitude,longitude:number`. |
| [delete_address](https://mcp.swiggy.com/builders/docs/reference/food/delete_address.md) | `addressId:string` |
| [search_restaurants](https://mcp.swiggy.com/builders/docs/reference/food/search_restaurants.md) | `addressId:string,query:string,offset?:number,collection?:"EATRIGHT"\|"BOLT"\|"STORE_99"` |
| [search_menu](https://mcp.swiggy.com/builders/docs/reference/food/search_menu.md) | `addressId:string,query:string,restaurantIdOfAddedItem?:string,vegFilter?:0\|1,offset?:number`. Query cannot be empty. `1` is vegetarian-only; `0` is mixed. |
| [get_restaurant_menu](https://mcp.swiggy.com/builders/docs/reference/food/get_restaurant_menu.md) | `addressId:string,restaurantId:string` |
| [update_food_cart](https://mcp.swiggy.com/builders/docs/reference/food/update_food_cart.md) | `restaurantId:string,cartItems:object[],addressId:string,restaurantName?:string,cutleryOptIn?:boolean` |
| [get_food_cart](https://mcp.swiggy.com/builders/docs/reference/food/get_food_cart.md) | `addressId:string,restaurantName?:string` |
| [flush_food_cart](https://mcp.swiggy.com/builders/docs/reference/food/flush_food_cart.md) | No parameters. |
| [fetch_food_coupons](https://mcp.swiggy.com/builders/docs/reference/food/fetch_food_coupons.md) | `restaurantId:string,addressId:string,couponCode?:string` |
| [apply_food_coupon](https://mcp.swiggy.com/builders/docs/reference/food/apply_food_coupon.md) | `couponCode:string,addressId:string,cartId?:string` |
| [get_payment_options](https://mcp.swiggy.com/builders/docs/reference/food/get_payment_options.md) | `addressId?:string` (recommended for Food) |
| [place_food_order](https://mcp.swiggy.com/builders/docs/reference/food/place_food_order.md) | `addressId:string,paymentMethod?:string,intentApp?:string,generateUPIQR?:boolean,noteToRestaurant?:string` |
| [check_payment_status](https://mcp.swiggy.com/builders/docs/reference/food/check_payment_status.md) | `paasId:string,orderId?:string,addressId?:string,cartId?:string,lat?:number,lng?:number` |
| [confirm_order](https://mcp.swiggy.com/builders/docs/reference/food/confirm_order.md) | For Food: required `orderId:string,addressId:string,lat:number,lng:number`; optional `cartId:string`. Generic `transactionId?/paasId?:string` apply to Instamart/Dineout, not Food confirmation. |
| [get_food_orders](https://mcp.swiggy.com/builders/docs/reference/food/get_food_orders.md) | `addressId:string,activeOnly?:boolean` (default false) |
| [get_food_order_details](https://mcp.swiggy.com/builders/docs/reference/food/get_food_order_details.md) | `orderId:string` |
| [track_food_order](https://mcp.swiggy.com/builders/docs/reference/food/track_food_order.md) | `orderId?:string`; omitted means all active orders. |
| [get_food_delivery_status](https://mcp.swiggy.com/builders/docs/reference/food/get_food_delivery_status.md) | `orderId:string` |
| [report_error](https://mcp.swiggy.com/builders/docs/reference/food/report_error.md) | Required `tool:string,errorMessage:string`; optional `domain:string,flowDescription:string,toolContext:object,userNotes:string`. |

## Wire protocol and ownership

The real endpoint is `https://mcp.swiggy.com/food`. Swiggy's SDK examples initialize an MCP client, discover schemas with `tools/list`, and call tools using JSON-RPC `tools/call` with `params.name` and `params.arguments`. The Python example uses Streamable HTTP. [Build an agent](https://mcp.swiggy.com/builders/docs/start/developer/build-an-agent.md)

For a compatible simulator, use the existing MCP SDK server rather than invent a REST API with tool names. Streamable HTTP uses POST, supports JSON or SSE responses, and requires clients to accept both. When initialization returns `Mcp-Session-Id`, retain it on subsequent requests; HTTP 404 for a session means initialize again. Subsequent requests carry the negotiated `MCP-Protocol-Version`. Bind locally and validate browser origins. These are general MCP protocol requirements, not proof of extra Swiggy behavior. [MCP transport specification](https://modelcontextprotocol.io/specification/2025-06-18/basic/transports)

Keep the MCP result separate from the Swiggy domain envelope. A compatible mock may return the domain envelope in `structuredContent` and serialize the same envelope in a text content block, per the general MCP tools specification. Swiggy's pages document the domain envelope, but do not guarantee that every live tool uses both outer result fields. [MCP tools specification](https://modelcontextprotocol.io/specification/2025-06-18/server/tools)

The domain success envelope is `{success:true,data:toolPayload,message?:string}`. Failure is `{success:false,error:{message:string,reportLink?:string,reportHint?:string}}`. HTTP 200 can contain a domain failure. Swiggy's symbolic error codes remain planned, so do not emit fabricated provider `error.code` values as if verified. HTTP 401 / JSON-RPC `-32001` means auth failure; `-32603` covers internal failures. [Errors](https://mcp.swiggy.com/builders/docs/reference/errors.md)

Real Swiggy auth uses browser OAuth 2.1 PKCE S256, per-user bearer tokens, exact allowlisted HTTPS callbacks (localhost exception), and phone/OTP only within the provider browser consent UI. Access tokens last five days; refresh-token issuance is not implemented. A local simulator does not need to imitate phone OTP or request real Swiggy credentials. Its separate developer bearer token, reset controls and fake payment state belong to Hungii and must be labeled accordingly. [Authenticate](https://mcp.swiggy.com/builders/docs/start/authenticate.md)

The cart is provider-side state associated with the authenticated user/session and is authoritative across calls. Re-read it before changing/placing an order. A Food cart contains one restaurant: switching restaurants clears the previous cart, so warn and obtain user agreement first. Transport reconnection should not erase an authenticated synthetic user's cart. Multiple simulator users must not share carts or orders. [Multi-turn cart state](https://mcp.swiggy.com/builders/docs/build/agent-patterns/multi-turn-state.md)

Current published quotas are 70 requests/minute per authenticated user/server and 30 write requests/minute; auth/initialize events are separately throttled. Respect `Retry-After` on 429 and reuse a session. Any faster demo polling interval is a simulator configuration, not an asserted production interval. [Rate limits](https://mcp.swiggy.com/builders/docs/operate/rate-limits.md)

## Catalog and nutrition fidelity

`search_restaurants` returns `restaurants[]` with required `name,cuisines:string[]`, and `dishes[]` with required `name`; most other fields are optional. Restaurant fields include `id,availabilityStatus,deliveryTimeMinutes,distanceKm,offer`. Dishes use `id,restaurantId,restaurantName,price,isVeg,inStock:boolean,description,imageUrl,deliveryTimeMinutes`. The response also requires `query:string`; optional `nextOffset` may be a string or number although input `offset` is documented as a number. [Restaurant search](https://mcp.swiggy.com/builders/docs/reference/food/search_restaurants.md)

`search_menu` instead returns `items[]` with `menu_item_id,restaurant_id,restaurant_name` optional identifiers and `inStock?:number`; required `name`. Top-level `query,totalItems,hasMore` are required. No ETA or structured nutrition fields appear in its schema. Legacy `variations[]` and newer `variantsV2[]` are alternatives. `addons[]` groups include `groupId,groupName,choices:[{id,name,price}]`, plus optional minimum/maximum/free-count constraints. Resolve the selected dish with restaurant-scoped `search_menu` before ordering. [Menu search](https://mcp.swiggy.com/builders/docs/reference/food/search_menu.md)

`get_restaurant_menu` is a compact browse response: `restaurant:{id,name,...}`, `items:[{id,name,categories:string[],price?,inStock?:number,isVeg?,hasVariants?,hasAddons?,...}]`, `categoryLabels:string[],totalItems:number,totalCategories:number,truncated?:boolean`. It caps the browse list at 150 unique items and omits description/images. Use `restaurantIdOfAddedItem` for scoped `search_menu` even though prose on the browse page calls it `restaurantId`. [Restaurant menu](https://mcp.swiggy.com/builders/docs/reference/food/get_restaurant_menu.md)

None of these reviewed menu schemas supplies structured kcal/protein/carbohydrate/fat values. Maintain synthetic nutrition/calibration in Hungii-owned fixture metadata, separate from provider response schemas. Show nutrition as a labeled estimate with uncertainty rather than “Swiggy verified.” The user has explicitly allowed estimated nutrition. Synthetic dish images/descriptions must not imply actual live menus.

## Cart response and documented gaps

`update_food_cart` documents `cartItems` only as `object[]`. It does **not** publish required member fields, quantity replacement/merge semantics, nor full variant/add-on request schemas. A simulator can deliberately define a `menu_item_id`/`quantity` convention and paired customization IDs, but must document it as a Hungii mock convention and gate live writes until real authenticated `tools/list` schemas are inspected. Do not claim the cart output type proves the request type. [Cart update](https://mcp.swiggy.com/builders/docs/reference/food/update_food_cart.md)

The cart payload is double-nested: domain envelope `data` contains another optional `data:{cart_id?,result?,restaurant?,items?,item_count?,pricing?,offers?}`. `get_food_cart` also has required `addressId:string` beside that inner `data`, plus optional `availablePaymentMethods:string[]`, `paymentOptions`, `gpoError`. [Cart read](https://mcp.swiggy.com/builders/docs/reference/food/get_food_cart.md)

Cart item required fields: `menu_item_id,name:string`, `quantity,subtotal,total,final_price:number`; optional `imageUrl,is_veg:boolean|string|number,in_stock:boolean,variants[],addons[],valid_addons[]`. Selection IDs may use `group_id/groupId` and `variation_id/variationId`. Preserve matched group/choice IDs. Cart pricing requires `item_total,delivery_charge,taxes_and_charges,to_pay:number`; offers can include `coupon_applied:string|null,coupon_discount:number,free_delivery_applied:boolean`. Display returned `to_pay` as the authority. [Cart read](https://mcp.swiggy.com/builders/docs/reference/food/get_food_cart.md)

After every update, immediately call `get_food_cart` for the visible updated state. An auto-suggested coupon with zero discount is not an applied discount. Customized quantity increases require the user's choice about reusing add-ons; do not silently replicate them. Variant selection can narrow add-ons: offer the returned `valid_addons`, not every original menu option. [Cart update](https://mcp.swiggy.com/builders/docs/reference/food/update_food_cart.md)

`fetch_food_coupons` returns `coupon_sections[].coupons[]` and required `summary:{total_coupons,applicable_coupons,sections_count}`. Coupons have optional `id,applicable,applicabilityStatus,title,subtitle,description,ribbon_text,terms_and_conditions:{title?,bullet_texts?:string[]}`. The docs do not give machine-readable minimum spend or discount formulas, nor promise `id` equals the application `couponCode`. Put explicit thresholds/formulas in Hungii's synthetic fixture configuration and advertise them through the documented display/terms fields. [Coupons](https://mcp.swiggy.com/builders/docs/reference/food/fetch_food_coupons.md)

`apply_food_coupon` returns nested pricing with different names: optional `item_total,delivery_fee,packaging_fee,taxes,coupon_discount,to_pay`. Its `coupon_discount` is explicitly rupees; the reviewed menu/cart schemas do not consistently specify all money units. The mock can establish its own rupee contract; live normalization remains a separate verification gate. The coupon fetch prose still restricts recommendations to COD-compatible offers despite current UPI support; do not infer online-only coupon eligibility. [Coupon application](https://mcp.swiggy.com/builders/docs/reference/food/apply_food_coupon.md), [Coupon fetch](https://mcp.swiggy.com/builders/docs/reference/food/fetch_food_coupons.md)

There is no documented isolated Food hypothetical quote tool. Synthetic ranking can compare authored baskets without changing the cart; label these quotes as Hungii simulation. For live integration, candidate prices are provisional until the chosen meal is added and the provider returns a cart total.

## Complete checkout and simulated payment

1. Select a returned delivery address, discover the dish, resolve its current customizations, and explicitly add it. For another restaurant, warn that the current cart will be replaced.
2. Call `update_food_cart`, then `get_food_cart`. Let the user adjust quantities/customizations and explore returned coupons. Apply a chosen coupon and refresh the authoritative cart.
3. Call `get_payment_options` with the same `addressId`; offer only enabled returned choices. Show the item/customization summary, selected full address, payable total and payment choice.
4. The user explicitly presses a clearly labeled order/payment confirmation action. Lucky-draw reveal and a shortlist swipe are not ordering consent.
5. Call `place_food_order`. For UPI, use `paymentMethod="UPI"` with the returned app ID as `intentApp`, or `generateUPIQR=true` for QR. Cash is allowed only if returned. Swiggy Money uses `SwiggyPay` only if available. Restaurant notes use `noteToRestaurant`; no delivery-partner instruction field is documented.
6. For UPI pending results, simulate the payment in a Hungii-owned payment sheet/control. Never launch a real UPI URI or show a payable real QR in the mock. A synthetic receipt is not proof of payment.
7. Poll `check_payment_status` using returned `paasId` and context, respecting `pollingIntervalInMs` and `maxTimeToPollForInMs`. On successful terminal status, skip confirmation if `confirmed=true`; otherwise call Food `confirm_order` with exact `orderId,addressId,lat,lng` and optional `cartId`. On failure/cancel/refund/cart change, do not confirm.
8. Show success only for immediate `normalizedStatus="success"` or successful confirmation. Then show order tracking and a mock receipt. A purchase alone never automatically records consumed nutrition.

This flow follows [place order](https://mcp.swiggy.com/builders/docs/reference/food/place_food_order.md), [payment options](https://mcp.swiggy.com/builders/docs/reference/food/get_payment_options.md), [payment status](https://mcp.swiggy.com/builders/docs/reference/food/check_payment_status.md) and [confirmation](https://mcp.swiggy.com/builders/docs/reference/food/confirm_order.md).

Payment options required fields are `allMethods:PaymentMethod[]` and `placeOrderToolName`, which is `place_food_order` for Food. Each method requires `id:string` and can have `groupName,displayName,kind:"intent"|"qr",iconUrl,enabled`. Optional `platforms.mobile/desktop` contain `groupName` and `methods`; optional `cod`/`swiggyMoney` contain `available,id,displayName`. Optional `paymentAmount` is a string or null. The embedded cart payment schema is different, so refresh standalone options. Do not ask for VPA/UPI ID, card data or UPI PIN. [Payment options](https://mcp.swiggy.com/builders/docs/reference/food/get_payment_options.md)

UPI placement requires returned `orderId,paasId,transactionId,upiIntentUrl,bridgeUrl:string`, `isQrFlow:boolean`, `pollingIntervalInMs,maxTimeToPollForInMs:number`, `paymentMethod:"UPI",status:"PENDING_PAYMENT",normalizedStatus:"pending"`, `addressId:string,cartId:string|null,lat,lng:number`. Optional display fields are `totalAmount:number,restaurantName,restaurantAddress,deliveryAddress`. Cash and successful SwiggyPay return `normalizedStatus:"success"` with `orderId:string|null,status:string,items[]` and nullable display/total fields; no UPI polling is needed. [Place order](https://mcp.swiggy.com/builders/docs/reference/food/place_food_order.md)

Payment status requires `paasId,status:string,terminal,isTerminalSuccess,isTerminalFailure:boolean`; optional `confirmed,orderStatus,cartTotal,orderId,addressId,cartId,lat,lng`. Confirmation returns `orderId,result:string` with optional `paasId,orderStatus`. `confirm_order` is documented as idempotent for the same identifiers; placement is not safe to blindly retry. A mock should freeze the attempted basket and reject confirmation after intervening cart changes. [Payment status](https://mcp.swiggy.com/builders/docs/reference/food/check_payment_status.md), [Confirmation](https://mcp.swiggy.com/builders/docs/reference/food/confirm_order.md)

There is a documentation contradiction: the [UPI recipe](https://mcp.swiggy.com/builders/docs/build/recipes/pay-with-upi.md) suggests one confirmation at the polling deadline while still pending, but the current `confirm_order` reference says to call only after successful terminal payment. Use the stricter tool reference in Hungii: stop at the deadline, show unresolved state, and do not place another order automatically. Resolve this with Swiggy before live payments.

The provider offers no “simulate payment,” card processing, order cancellation, or arbitrary payment-completion mutation among the 20 tools. Simulator success/failure/cancel/cart-change controls must be a separate Hungii-owned developer endpoint/UI, not fabricated Swiggy tools. Do not claim the mock creates a cart in the real Swiggy account. The supported future live mechanism is `update_food_cart`; execution remains approval/auth/schema-gated.

## Address, history, tracking and support output

`get_addresses` returns `addresses:[{id,addressLine,phoneNumber:string,addressCategory?,addressTag?}]` and pagination `page,pageSize,total,totalPages:number,hasMore:boolean`. `create_address` returns `{addressId:string}`. `addressLine2` is required even when empty; user-provided full address is parsed for components. `delete_address` returns `{statusCode:number,statusMessage:string}` and requires explicit permanent-deletion confirmation. The simulator must not collect actual account-holder contact information to demonstrate this. [Addresses](https://mcp.swiggy.com/builders/docs/reference/food/get_addresses.md), [Create](https://mcp.swiggy.com/builders/docs/reference/food/create_address.md), [Delete](https://mcp.swiggy.com/builders/docs/reference/food/delete_address.md)

`get_food_orders` entries require string `orderId,restaurantId,restaurantName,orderTotal,orderStatus,orderType,orderedItems,orderedTime`, boolean `isActiveOrder`, and `actions[]`. Each action requires `type,title:string,priority:number,isEnabled:boolean`, with optional reorder metadata. A generic action `type` is not evidence that a corresponding callable tool exists. Historical reorder IDs/quantities/customizations are optional and can use mixed string/number types: refresh current menus before reordering. [Order history](https://mcp.swiggy.com/builders/docs/reference/food/get_food_orders.md)

`get_food_order_details` instead uses `order.order_id:number`, `restaurant_id:string`, an address containing names/phone/coordinates, string item quantities/prices and numeric overall charges. Use numeric-string synthetic order IDs so these differing view schemas stay consistent. Detail responses include PII in real mode, so avoid logging or saving full responses. [Order details](https://mcp.swiggy.com/builders/docs/reference/food/get_food_order_details.md)

`track_food_order` returns `orders:[{orderId,title,subtitle,orderStatus:string,etaText?,progressPercentage?,pollingDuration?,icon?,businessLine?:{id,name?}}]`. Structured `get_food_delivery_status` returns required `orderId:string,deliveryBy:number|null,serverNow:number,pollIntervalSec:number`, optional `etaText,statusText:string,cancelled,delivered:boolean`. Timestamp values are epoch milliseconds; stop polling at terminal delivery/cancellation. [Tracking](https://mcp.swiggy.com/builders/docs/reference/food/track_food_order.md), [Delivery status](https://mcp.swiggy.com/builders/docs/reference/food/get_food_delivery_status.md)

`report_error` returns `{mailto:string,summary:{subject:string,body:string}}`; generating this response does not send email. The user's existing instruction permits draft-only communications. [Report error](https://mcp.swiggy.com/builders/docs/reference/food/report_error.md)

## Minimum useful simulator verification

- List exactly the verified 20 tools; reject invented tools and malformed arguments.
- Maintain authenticated user isolation and cart continuity across transport reconnects.
- Search/paginate/filter actual synthesized fixtures; include unavailable restaurants/dishes and both customization formats.
- Enforce one restaurant per cart, customization IDs/constraints and authoritative totals.
- Exercise a coupon threshold where a larger item subtotal gives a smaller final payable total; do not mark zero discount as applied.
- Exercise immediate cash success, UPI pending/success/manual confirmation, auto-confirmed success, failed/cancelled/expired payment, and changed cart.
- Do not duplicate orders on repeated confirmation; reconcile ambiguous placement rather than blindly retry.
- Keep unknown nutrition visible, with estimate provenance and uncertainty; never treat ordering as eating.
- Preserve real production write gates, and keep mock data/payment credentials disconnected from Swiggy and paid services.
