# Hungii Swiggy integration contract

Verified 2 October 2026 from Swiggy's `.md` documentation. Exact downloaded pages are in `/tmp/hungii-swiggy-docs/`, named by replacing `/` with `__`; this is a local reference cache, not user data. This note records documented fields and gaps; it is not evidence that live tool calls or payment have been tested.

## Connection and authorization

Food endpoint: `https://mcp.swiggy.com/food`. Transport: MCP Streamable HTTP; authenticated calls carry `Authorization: Bearer <per-user-token>`. Calls use JSON-RPC `tools/call` with `params.name` and `params.arguments`. Initialize/list tools through the MCP client; verify runtime input schemas before mutations. [Build an agent](https://mcp.swiggy.com/builders/docs/start/developer/build-an-agent.md)

OAuth uses PKCE S256 and per-user browser authorization. Metadata lives at `/.well-known/oauth-authorization-server` and `/.well-known/oauth-protected-resource`; dynamic registration is `POST /auth/register`. Authorization is `GET /auth/authorize` with `response_type=code`, returned `client_id`, exact callback `redirect_uri`, `code_challenge`, `code_challenge_method=S256`, random `state` and `scope=mcp:tools`. Token exchange is JSON `POST /auth/token`: `grant_type=authorization_code`, `code`, `code_verifier`, `client_id`, `redirect_uri`. Response fields: `access_token`, `token_type`, `expires_in`, `scope`. The delegated example includes `client_id`; the direct example omits it. [Authenticate](https://mcp.swiggy.com/builders/docs/start/authenticate.md), [delegated auth](https://mcp.swiggy.com/builders/docs/start/enterprise/delegated-auth.md)

Callbacks require exact-match allowlisted HTTPS (localhost HTTP is a development exception). Hungii's custom URI scheme is not automatically accepted. Tokens last five days; authorization codes last 120 seconds and are single-use. Refresh-token issuance is not implemented despite metadata advertising the grant. Reauthorize on 401. Disconnect uses `POST /auth/logout` and local token deletion. Store tokens securely per Hungii user and bind each state/verifier to that user; never use a shared Swiggy token. [Authenticate](https://mcp.swiggy.com/builders/docs/start/authenticate.md), [delegated auth](https://mcp.swiggy.com/builders/docs/start/enterprise/delegated-auth.md)

## Tool response envelope

The documented domain envelope has `success:boolean`, successful `data`, optional `message`; failure has `success:false` and `error.message`, optionally `error.reportLink`/`error.reportHint`. This is distinct from the surrounding MCP tool-result/JSON-RPC envelope. Symbolic `error.code` values remain planned, not emitted. Auth also uses HTTP 401 / JSON-RPC `-32001`; internal failure may use `-32603`. A HTTP 200 can still be a domain failure. [Errors](https://mcp.swiggy.com/builders/docs/reference/errors.md)

## Discovery

Notation: `?` means optional; preserve field spellings and returned identifiers.

| Tool | Exact documented arguments | Important successful `data` shape |
| --- | --- | --- |
| [get_addresses](https://mcp.swiggy.com/builders/docs/reference/food/get_addresses.md) | `page?:number` (1-based, default 1), `pageSize?:number` (default/max 10) | `addresses:[{id:string,addressLine:string,phoneNumber:string,addressCategory?:string,addressTag?:string}]`; `pagination:{page,pageSize,total,totalPages:number,hasMore:boolean}` |
| [search_restaurants](https://mcp.swiggy.com/builders/docs/reference/food/search_restaurants.md) | `addressId:string,query:string,offset?:number,collection?:"EATRIGHT"\|"BOLT"\|"STORE_99"` | `restaurants[]` and `dishes[]`; `nextOffset?:string\|number,query:string,totalRestaurants?:number,hasMore?:boolean` |
| [search_menu](https://mcp.swiggy.com/builders/docs/reference/food/search_menu.md) | `addressId:string,query:string,restaurantIdOfAddedItem?:string,vegFilter?:0\|1,offset?:number` | `items[]`; `query:string,restaurantIdOfAddedItem?:string,totalItems:number,hasMore:boolean,nextOffset?:number` |
| [get_restaurant_menu](https://mcp.swiggy.com/builders/docs/reference/food/get_restaurant_menu.md) | `addressId:string,restaurantId:string` | `restaurant:{id,name,...}`, `items:[{id,name,categories:string[],price?,inStock?,isVeg?,hasVariants?,hasAddons?,...}]`, `categoryLabels:string[],totalItems:number,totalCategories:number,truncated?:boolean` |

Restaurant entries have `name`, `cuisines:string[]`; optional `id`, `avgRating`, `totalRatings`, `costForTwo`, `areaName`, `distanceKm`, `deliveryTimeMinutes`, `deliveryTimeRange`, `veg`, `offer`, `imageUrl`, `availabilityStatus`, `nextOpenTime`. Recommend only explicit `availabilityStatus="OPEN"`. Dish entries use optional `id`, `restaurantId`, `restaurantName`, `price`, `isVeg`, `description`, `imageUrl`, `category`, `inStock:boolean`, restaurant rating/area and `deliveryTimeMinutes`. Search output offset may be string although input says number: validate runtime schema, do not blindly coerce arbitrary cursors. [search_restaurants](https://mcp.swiggy.com/builders/docs/reference/food/search_restaurants.md)

Menu search entries use `name:string` and optional `menu_item_id:string`, `restaurant_id:string`, `restaurant_name:string`, `price:number`, `isVeg:boolean`, `inStock:number`, `imageUrl`, ratings, `hasVariants`, `hasAddons`, `isBestseller`. No ETA or structured nutrition is specified in this schema. Customizations are mutually exclusive:

- `variations:[{id?,groupId?,name?,price?,isVeg?,default?,inStock?}]` (legacy search format).
- `variantsV2:[{groupId:string,name:string,variations:[{id:string,name:string,price?,inStock?,default?}]}]`.
- `addons:[{groupId:string,groupName:string,minAddons?,maxAddons?,maxFreeAddons?,choices:[{id:string,name:string,price:number}]}]`.

A chosen cross-restaurant dish must be searched again with its restaurant in `restaurantIdOfAddedItem` to resolve full customizations. Veg filter 1 is vegetarian-only; 0 is mixed, not non-vegetarian-only. [search_menu](https://mcp.swiggy.com/builders/docs/reference/food/search_menu.md)

Address selection must occur before further tools. Browse menus cap at 150 unique items and omit descriptions/images; use scoped search to order. Examples in the order recipe use older address fields and should not override the tool reference. [get_addresses](https://mcp.swiggy.com/builders/docs/reference/food/get_addresses.md), [get_restaurant_menu](https://mcp.swiggy.com/builders/docs/reference/food/get_restaurant_menu.md)

## Cart and coupons

| Tool | Exact documented arguments | Important successful `data` shape |
| --- | --- | --- |
| [get_food_cart](https://mcp.swiggy.com/builders/docs/reference/food/get_food_cart.md) | `addressId:string,restaurantName?:string` | Nested `data?:{cart_id?,result?,restaurant?,items?,item_count?,pricing?,offers?}` plus `addressId:string,availablePaymentMethods?:string[],paymentOptions?,gpoError?:string` |
| [update_food_cart](https://mcp.swiggy.com/builders/docs/reference/food/update_food_cart.md) | `restaurantId:string,cartItems:object[],addressId:string,restaurantName?:string,cutleryOptIn?:boolean` | Nested `data?` cart payload plus `statusCode?:number,statusMessage?:string` |
| [fetch_food_coupons](https://mcp.swiggy.com/builders/docs/reference/food/fetch_food_coupons.md) | `restaurantId:string,addressId:string,couponCode?:string` | `coupon_sections:[{title?,type?,coupons:[{id?,applicable?,applicabilityStatus?,title?,subtitle?,description?,ribbon_text?,terms_and_conditions?}]}]`; `summary:{total_coupons,applicable_coupons,sections_count:number,filter_applied?:string}`, `status_message?` |
| [apply_food_coupon](https://mcp.swiggy.com/builders/docs/reference/food/apply_food_coupon.md) | `couponCode:string,addressId:string,cartId?:string` | `statusCode?,statusMessage?,data?:{cart_id?,result?,pricing?:{item_total?,delivery_fee?,packaging_fee?,taxes?,coupon_discount?,to_pay?},offers?:{coupon_applied?,coupon_discount?},item_count?}` |

**Cart request gap:** the reference documents `cartItems` only as `object[]`; it does not publish exact member required fields, quantity semantics, or variant/add-on request schemas. Retrieve authenticated `tools/list` and validate these before implementing a live mutation. Search legacy `variations` is described as cart `variants`; do not copy the search object wholesale or assume output schema equals input schema. Preserve all group/choice IDs. Add variants first, inspect returned `valid_addons`, then select permitted add-ons. Immediately call `get_food_cart` after update. [update_food_cart](https://mcp.swiggy.com/builders/docs/reference/food/update_food_cart.md)

Cart items: required `menu_item_id,name,quantity,subtotal,total,final_price`; optional `imageUrl,is_veg:boolean|string|number,in_stock,variants[],addons[],valid_addons[]`. Selection fields vary between `group_id`/`groupId` and `variation_id`/`variationId`; `id,name,price` are optional. Cart pricing requires `item_total,delivery_charge,taxes_and_charges,to_pay` with optional `delivery_charge_strikeoff`. Offers have optional `coupon_applied,coupon_discount,free_delivery_applied`. Use `to_pay` rather than reconstructing totals. [get_food_cart](https://mcp.swiggy.com/builders/docs/reference/food/get_food_cart.md)

Only a positive returned discount proves coupon savings; a suggested code with zero discount is not applied. Coupon response `id` is not documented as a `couponCode` alias, so verify against live fixtures. Minimum spend/discount formulas are not structured fields; terms use `terms_and_conditions.bullet_texts:string[]`. Coupon docs still restrict recommendations to COD-compatible offers despite newer UPI support; this needs clarification before online-only offer optimization. [fetch_food_coupons](https://mcp.swiggy.com/builders/docs/reference/food/fetch_food_coupons.md), [apply_food_coupon](https://mcp.swiggy.com/builders/docs/reference/food/apply_food_coupon.md)

**Currency:** `apply_food_coupon` explicitly labels `coupon_discount` as rupees. Menu `price`, cart `to_pay` and historical values are not explicitly unit-labelled in the reviewed schemas. Do not assume raw paise or divide every number by 100; verify live fixtures/Swiggy's contract before presenting unlabelled money. Preserve original numeric/string values until that normalization is established. [apply_food_coupon](https://mcp.swiggy.com/builders/docs/reference/food/apply_food_coupon.md)

## History and favorites

[get_food_orders](https://mcp.swiggy.com/builders/docs/reference/food/get_food_orders.md): args `addressId:string,activeOnly?:boolean`; `data.orders[]` requires `orderId,restaurantId,restaurantName,orderTotal,orderStatus,orderType,orderedItems,orderedTime:string`, `isActiveOrder:boolean`, `actions[]`, with optional area/delivery status. Actions include `type,title:string`, `priority:number`, `isEnabled:boolean`, optional `reorderMeta.orderItems[]`. Reorder items may contain `menu_item_id` or `item_id`, `quantity:number|string`, variants with `variation_id/group_id`, add-ons with `addon_id/group_id`. Use history to suggest favorites, not infer eaten food.

[get_food_order_details](https://mcp.swiggy.com/builders/docs/reference/food/get_food_order_details.md): args `orderId:string`; `data.order` uses `order_id:number`, `restaurant_id:string`, `order_items[]`, `delivery_address`, `charges:Record<string,string>`, status/times/payment fields and numeric `order_total,item_total,order_tax,order_discount,coupon_discount,order_delivery_charge`. Items use `item_id:string`, optional `external_item_id`, `quantity:string`, price totals as strings, `is_veg:string`, variant/add-on arrays and optional portion/spice attributes. Historical IDs are not guaranteed to be current `menu_item_id`; refresh the menu before reordering. The details response includes address/person/phone/coordinates: treat these as PII.

## Payment and placement

| Tool | Exact documented arguments | Important successful `data` shape |
| --- | --- | --- |
| [get_payment_options](https://mcp.swiggy.com/builders/docs/reference/food/get_payment_options.md) | `addressId?:string` | `allMethods:[{id:string,groupName?,displayName?,kind?:"intent"|"qr",iconUrl?,enabled?:boolean}]`, optional `platforms.mobile/desktop:{groupName,methods}`, `cod/swiggyMoney:{available,id,displayName}`, `paymentAmount?:string|null,addressId?,placeOrderToolName` |
| [place_food_order](https://mcp.swiggy.com/builders/docs/reference/food/place_food_order.md) | `addressId:string,paymentMethod?:string,intentApp?:string,generateUPIQR?:boolean,noteToRestaurant?:string` | Immediate result or UPI pending result; see below |
| [check_payment_status](https://mcp.swiggy.com/builders/docs/reference/food/check_payment_status.md) | `paasId:string,orderId?:string,addressId?:string,cartId?:string,lat?:number,lng?:number` | `paasId,status:string,terminal,isTerminalSuccess,isTerminalFailure:boolean`; optional `confirmed,orderStatus,cartTotal,orderId,addressId,cartId,lat,lng` |
| [confirm_order](https://mcp.swiggy.com/builders/docs/reference/food/confirm_order.md) | Food: `orderId:string,addressId:string,lat:number,lng:number,cartId?:string`; generic schema also has `transactionId?/paasId?` for other domains | `orderId:string,result:"success"|"failed"|"pending"|string,paasId?:string,orderStatus?:string` |

UPI selection uses `paymentMethod="UPI"`; copy returned app ID to `intentApp`, or use `generateUPIQR=true` for returned QR choice. Cash/COD must be returned; Swiggy Money uses `SwiggyPay` only when offered. Never ask for a UPI ID/VPA. Cart's embedded payment shape differs from standalone options; prefer refreshed standalone options at payment. [get_payment_options](https://mcp.swiggy.com/builders/docs/reference/food/get_payment_options.md)

Immediate placement (Cash or successful SwiggyPay): `normalizedStatus="success"`, `orderId:string|null,status:string`, `items[]`, `restaurantName/restaurantAddress/estimatedDelivery/deliveryAddress:string|null`, `totalAmount:number|string|null`. UPI: `status="PENDING_PAYMENT",normalizedStatus="pending"`, required `orderId,paasId,transactionId,upiIntentUrl,bridgeUrl:string`, `isQrFlow:boolean`, polling hints in milliseconds, `paymentMethod="UPI"`, confirmation context `addressId:string,cartId:string|null,lat,lng:number`; optional total/restaurant/address display. Pending payment is not a placed order. [place_food_order](https://mcp.swiggy.com/builders/docs/reference/food/place_food_order.md)

After successful terminal UPI payment, skip confirmation if `confirmed=true`; otherwise echo exact order/address/cart/lat/lng into Food `confirm_order`. Never confirm after terminal failure. Respect returned polling cadence/deadline. No polling/confirm is needed for already successful Cash or SwiggyPay. [check_payment_status](https://mcp.swiggy.com/builders/docs/reference/food/check_payment_status.md), [confirm_order](https://mcp.swiggy.com/builders/docs/reference/food/confirm_order.md)

## Required integration guards

- Show current items, payable total, full selected address and returned payment choice; require explicit confirmation immediately before order placement. Draw/reveal is never ordering consent. [place_food_order](https://mcp.swiggy.com/builders/docs/reference/food/place_food_order.md)
- Placement is not safe to blind-retry. After unknown network/5xx outcome, wait 2–5 seconds and reconcile recent orders before retrying. Read/cart/coupon retry policies differ; user-facing retries cap at 30 seconds. [Production guidance](https://mcp.swiggy.com/builders/docs/build/ship-to-production.md)
- There is no documented isolated hypothetical Food quote API. Candidate costs remain estimates until the selected meal enters the user's live cart; do not silently compare candidates by repeatedly changing it. [Cart reference](https://mcp.swiggy.com/builders/docs/reference/food/get_food_cart.md), [cart mutation reference](https://mcp.swiggy.com/builders/docs/reference/food/update_food_cart.md)
- Swiggy-originated data persistence beyond the immediate session requires the applicable consent/lawful basis. Outside-India processing requires a Swiggy DPA plus transfer mechanism before production. Do not log tokens or full PII response bodies. Mumbai database placement alone does not prove every edge/inference task runs in India. [Data contract](https://mcp.swiggy.com/builders/docs/operate/data-and-compliance.md)
- Unknown money units, `cartItems` member schema and contradictory coupon/customization guidance are live-integration gates, not fields we should guess. Integration scaffolding can be built while those checks remain explicit.
