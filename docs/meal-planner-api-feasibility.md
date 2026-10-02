# Hungii next-meal planner: API feasibility

Verified 2026-10-01 from the exact Swiggy Builders Club Markdown pages linked below, fetched directly over HTTPS. No authenticated live requests were made. Product direction: an economical, low-decision-load next-meal swipe planner constrained by preferences, time, spend and nutrition goals.

## Finding

A dish swipe experience with stock, photos, indicative prices, restaurant delivery estimates, live checkout totals and coupon application is supported. **Exact calorie/macro filtering and exact cheapest all-in pricing across many hypothetical meals are not supported by the reviewed published schemas.** Hungii needs an explicitly sourced nutrition layer and a separate estimated-versus-confirmed pricing experience. These are conclusions from the capability inventory below, not claims about undocumented internal Swiggy APIs.

## Discovery and card fields

| Tool | Verified inputs / outputs | Implication |
| --- | --- | --- |
| `search_restaurants` | Requires `addressId`, `query`; optional `offset` and one `collection`: `EATRIGHT`, `BOLT`, or `STORE_99`. Restaurants can include `deliveryTimeMinutes`, `deliveryTimeRange`, rating, image and `availabilityStatus`. Dishes can include price, image, description, stock, restaurant identifiers and `deliveryTimeMinutes`. | Useful first-pass cards and discovery intent. Collections indicate healthy/high-protein/low-calorie, fast delivery or budget discovery; they do not guarantee numeric nutrition, arrival time or final ₹99 checkout. Only recommend returned `OPEN` restaurants. |
| `search_menu` | Requires saved `addressId` and dish `query`; optional restaurant scope `restaurantIdOfAddedItem`, `vegFilter` and pagination. Structured items include name, optional price/image/stock/rating, restaurant and menu identifiers, variants and addons. | Drill into a selected dish and retrieve valid customization identifiers. No structured description, ETA or nutrition fields in this schema. Vegetarian-only is supported (`vegFilter=1`); there is no nonvegetarian-only filter. |
| `get_restaurant_menu` | Requires `addressId`, `restaurantId`. Restaurant fields include optional `deliveryTime`, `slaString` and `isOpen`. Items contain IDs, names, prices, stock, vegetarian flag, bestseller flag and categories. At most 150 unique items; `truncated` can be true. | Browse categories; the compact item view intentionally omits descriptions and images. |

Sources: [Restaurant search](https://mcp.swiggy.com/builders/docs/reference/food/search_restaurants.md), [Dish search](https://mcp.swiggy.com/builders/docs/reference/food/search_menu.md), [Menu browse](https://mcp.swiggy.com/builders/docs/reference/food/get_restaurant_menu.md).

The menu-browse notes suggest using dish search for fuller item details, but its published structured schema still does not contain `description`. Preserve restaurant-search descriptions when supplied; do not assume every dish search includes one. The presence of `deliveryTimeMinutes` in restaurant-search dish entries is a display estimate, not a documented per-dish preparation-time guarantee. The cart restaurant exposes optional `deliverySubtitle` text, with no structured numeric cart ETA in the reviewed cart schema. [Dish search](https://mcp.swiggy.com/builders/docs/reference/food/search_menu.md), [Menu browse](https://mcp.swiggy.com/builders/docs/reference/food/get_restaurant_menu.md), [Restaurant search](https://mcp.swiggy.com/builders/docs/reference/food/search_restaurants.md), [Cart](https://mcp.swiggy.com/builders/docs/reference/food/get_food_cart.md).

## Nutrition feasibility

None of the reviewed discovery, menu, cart or order-history output schemas publish calories, protein, carbohydrate, fat, serving weight or an authoritative nutrition object. Order-item details may expose `attributes.portionSize`, `spiceLevel`, `vegClassifier`, and `accompaniments`, which are useful context but are not macro measurements. `EATRIGHT` is a storefront discovery collection, not a numeric nutrient filter. [Restaurant search](https://mcp.swiggy.com/builders/docs/reference/food/search_restaurants.md), [Dish search](https://mcp.swiggy.com/builders/docs/reference/food/search_menu.md), [Order details](https://mcp.swiggy.com/builders/docs/reference/food/get_food_order_details.md).

Product inference: distinguish restaurant-published nutrition, independently sourced estimates, and unknown values. Store source, portion assumptions and confidence beside any estimate. Unknown dishes cannot honestly satisfy a hard macro constraint. Do not turn names, photos or healthy-storefront membership into precise nutritional claims. Numeric meal budgets and daily remaining targets would be Hungii's own user-facing model, not Swiggy parameters.

## Money, fees and coupons

`get_food_cart` requires `addressId` to obtain accurate location-dependent charges. Its trimmed live pricing contains `item_total`, `delivery_charge`, optional delivery strikeoff, `taxes_and_charges`, and final `to_pay`. It can expose `coupon_applied`, `coupon_discount`, and `free_delivery_applied`. Item prices and `costForTwo` are discovery signals; the final cart `to_pay` is the checkout budget authority. Do not manually reconstruct the payable amount from individual components. [Cart](https://mcp.swiggy.com/builders/docs/reference/food/get_food_cart.md).

`fetch_food_coupons` requires `restaurantId` and `addressId`; optional `couponCode` checks one code. It is read-only but evaluates the current cart. Coupon cards expose applicability/status and optional title, subtitle, description, ribbon and textual terms (`bullet_texts`). **There are no documented structured numeric minimum-spend, maximum-discount, percentage or threshold fields.** Textual terms may contain such rules, but parsing them does not establish a live quote. The tool's instructions currently say to recommend only COD-compatible offers and filter out online/card-only offers. That restriction should be clarified with Swiggy before promising comprehensive UPI/payment-offer optimization. [Coupon discovery](https://mcp.swiggy.com/builders/docs/reference/food/fetch_food_coupons.md).

`apply_food_coupon` requires `couponCode`, `addressId`, and accepts optional `cartId`. It mutates the cart and returns an attempted application, with optional pricing fields `item_total`, `delivery_fee`, `packaging_fee`, `taxes`, `coupon_discount`, and `to_pay`. Its field naming differs from the cart-read schema; normalize explicitly. A reflected coupon code with zero discount may merely be auto-suggested. Announce savings only when an applied code and positive discount are verified. [Coupon application](https://mcp.swiggy.com/builders/docs/reference/food/apply_food_coupon.md), [Cart](https://mcp.swiggy.com/builders/docs/reference/food/get_food_cart.md).

Product inference: optimize **final outlay**, not discount size. If adding a ₹70 item unlocks ₹50 more discount, spending rises ₹20 before any charge changes. Recommend a top-up only when the new confirmed payable amount improves the user's cost objective and the extra food fits their appetite/nutrition plan. Without numeric structured thresholds, show a potential deal inferred from visible terms, then verify on the selected live cart. Do not promise the globally cheapest option.

## Shared-cart limitation

`update_food_cart` is a mutating operation requiring `restaurantId`, `cartItems`, `addressId`; optional restaurant display name and cutlery preference. It accepts actual customized items, not a documented dry-run or quote flag. Immediately follow it with `get_food_cart`. Each item uses the returned variant format; add the selected variant first and then use cart `valid_addons`, since not all menu addons work with every variant. Current stock must be rechecked. [Cart mutation](https://mcp.swiggy.com/builders/docs/reference/food/update_food_cart.md).

Food has one server-side cart bound to the restaurant, and the user can edit it in the Swiggy app. A restaurant switch can flush the existing cart. The reviewed references expose **no isolated hypothetical carts, read-only full-basket pricing endpoint, bulk multi-restaurant quote, coupon simulation or guaranteed snapshot restore**. The optional `cartId` on coupon application is not evidence of isolated-cart creation. [Multi-turn state](https://mcp.swiggy.com/builders/docs/build/agent-patterns/multi-turn-state.md), [Cart mutation](https://mcp.swiggy.com/builders/docs/reference/food/update_food_cart.md), [Coupon application](https://mcp.swiggy.com/builders/docs/reference/food/apply_food_coupon.md).

Product inference: swiping builds a Hungii-local shortlist with indicative item prices and visible offer text. The explicit “choose this meal” action hydrates one real cart, applies a supported coupon, and shows the actual bill for confirmation. Do not secretly cycle through restaurant carts to attach exact all-in prices to every swipe card. Warn about an existing cart before switching restaurants. If exact preselection cost ranking is essential, ask Swiggy for a sanctioned quote capability rather than inventing one.

## Spend history and persistence

`get_food_orders` requires `addressId`, with optional `activeOnly`; it returns the most recent orders, newest first, including `orderTotal` as a string, time, restaurant, status, ordered item text, and possible reorder metadata. It has no documented date-range, pagination or comprehensive historical export parameter. Therefore it cannot by itself guarantee a complete monthly spending ledger. [Order history](https://mcp.swiggy.com/builders/docs/reference/food/get_food_orders.md).

`get_food_order_details` takes `orderId` and returns actual order total, item total, tax, discount, coupon discount, delivery charge, generic charges and item-level prices/customizations. It also returns sensitive delivery fields including name, mobile, address and coordinates. This conflicts with the compliance page's broad “no raw phone/email/name” description; design around the concrete schema and avoid storing whole payloads. [Order details](https://mcp.swiggy.com/builders/docs/reference/food/get_food_order_details.md), [Data and compliance](https://mcp.swiggy.com/builders/docs/operate/data-and-compliance.md).

Swiggy's stated partner contract limits its data to the immediate task, requires a lawful basis and consent for retention beyond the session, and requires explicit consent and a DPA for analytics uses. It requires deletion of derived data and minimal diagnostic logging. Processing responses outside India requires contractual arrangements described on the compliance page. Persistent preference learning and a spending ledger derived from orders should be discussed during onboarding. These are provider requirements, not an independent legal opinion. [Data and compliance](https://mcp.swiggy.com/builders/docs/operate/data-and-compliance.md).

Product inference: a user-owned budget/goal profile and manually recorded meals can be Hungii data; Swiggy-derived order summaries need appropriate permission and minimization. An order is not proof of ingestion: split portions, skipped meals and other food require user correction. Label daily macros as a food log rather than assuming ordered equals eaten.

## Recommended first version

1. Ask for next-meal budget, time window, vegetarian preference and optionally user-supplied calorie/protein targets.
2. Show a small swipe deck with dish image or fallback, meal name, restaurant, indicative item price, available restaurant ETA, and one simple reason it fits. Mark nutrition as sourced, estimated or unknown.
3. Keep candidates local until the user chooses. No automatic cart changes from merely viewing or rejecting a card.
4. On selection, verify current dish variants/stock, protect the existing cart, build the chosen basket, fetch supported coupons and apply a verified choice.
5. Show final `to_pay` and available payment methods, and require confirmation before ordering. If the real total exceeds budget, offer a smaller basket or another choice.
6. Offer an editable meal/spend log with appropriate consent; learn likes/dislikes without claiming medical benefits or guaranteed ADHD outcomes.

Before a stronger guarantee, confirm with Swiggy: any nutrition feed; sanctioned isolated quote capability; numeric coupon terms and online-offer coverage; sufficiently complete history for spend accounting; and permitted retention/personalization use.
