# Public food services and real ordering for Hungii

Verified on **2026-10-04** against first-party documentation and limited read-only API samples. No user location, private credentials, orders, payments, account registration, or provider messages were used. Findings describe external feasibility; they do not establish that Hungii has production access.

## Decision

**Latest constraint:** launch in Hyderabad, with no business/restaurant partner and no outreach or tie-ups. Under this constraint, **no verified public self-service consumer ordering-and-payment API was found that fulfills real delivery checkout inside Hungii**. ONDC production participation, a buyer TSP contract, and direct restaurant partnerships all require external onboarding or agreements and therefore do not currently satisfy the user's constraint. The routes below document why they were considered and rejected for immediate use; they are not recommendations to contact anyone.

The required complete flow is **discover food → choose a real restaurant/menu → cart → pay and place a real delivery order inside Hungii → track/support → log what was eaten**. Public place and nutrition databases cannot supply this ordering flow. The strongest verified India route is **an ONDC food-and-beverage buyer integration**, operated by Hungii as a buyer network participant or with a contracted buyer-side technology provider. ONDC explicitly allows a customer-facing app with a single checkout, and assigns buyer support responsibilities to that participant. [ONDC buyer requirements](https://ondc.org/pages/buyer-network-participants.html).

Publicly documented does not mean anonymously accessible in production. No reviewed source establishes an unrestricted, self-service API that gives any APK India's live restaurant inventory, checkout, payments, and delivery with no business onboarding. This is a bounded research finding, not a claim that no other service exists. Opening another delivery app would not fulfill the required inside-Hungii flow.

## Correction: Swiggy provides a documented developer ordering route

The user supplied Swiggy's [Developer quickstart](https://mcp.swiggy.com/builders/docs/start/developer/). Swiggy does publish MCP tools for real restaurant discovery, menus, cart mutation, order placement and tracking, plus a documented [UPI payment flow](https://mcp.swiggy.com/builders/docs/build/recipes/pay-with-upi/). Its [food-order recipe](https://mcp.swiggy.com/builders/docs/build/recipes/order-food/) currently specifies a ₹1,000 cart cap. Excluding Swiggy as requested earlier must not be described as absence of a developer ordering API.

The relevant distinction is production authorization: local development can start without approval; the quickstart describes staging, while the more specific [access documentation](https://mcp.swiggy.com/builders/docs/operate/access/) says credential-free development uses a local stub, staging credentials are issued during review, and staging is seeded with no real orders. Production remains application-reviewed/invite-based in v1. Individual developers are explicitly eligible to apply; the cited pages do not establish a requirement for the developer to recruit restaurant partners or incorporate a company. They do request an integration name/organization and technical contact. Eligibility and acceptance for Hungii have not been verified.

This is a plausible provider route for Hungii without direct restaurant tie-ups, provided the user accepts Swiggy's developer access application. It does not satisfy a strict requirement of zero external onboarding. UPI authorization uses a UPI app or scan-QR/payment bridge; an app-based checkout should not promise that the UPI PIN is entered inside Hungii. No application has been submitted and no live transaction has been attempted.

## Real ordering routes

### 1. Own ONDC buyer backend — verified official route

ONDC's official developer catalog identifies food and beverages as **RET11** under Retail. Its currently listed latest B2C contract is **1.2.5**; older reference apps are **1.2.0**, so copying a reference app does not establish current feature compliance. Requests and asynchronous callbacks are signed and verified. A portal account is mandatory to start integration; reference-app end-to-end tests are required. [Official developer catalog](https://github.com/ONDC-Official).

Prerequisites include a domain/FQDN, valid TLS certificate, subscriber ID, separate environment approval/whitelisting, signing and encryption keys, and hosted subscriber/callback endpoints. Production adds DNS-based verification. These are server/operator setup tasks, not “connect your delivery account” tasks for a shopper. [Registry onboarding](https://github.com/ONDC-Official/developer-docs/blob/main/registry/Onboarding%20of%20Participants.md).

Production participation also has a Network Participant Agreement and network policies covering onboarding, commercial terms, complaints, privacy, technology certification, and audits. This is not a free public-data license. Cost, commercial terms, and acceptance must be established through actual onboarding rather than inferred from open specifications. [Network policy](https://www.ondc.org/pages/resources-network-policy.html).

The official quickstart describes search, select, initialize, and confirm transaction stages. Build discovery callbacks, revalidated seller quotes, serviceability, payment orchestration, order confirmation, status/cancellation, and grievance handling into Hungii's backend and UI. An HTTP acknowledgement or payment SDK success is not sufficient evidence of a restaurant-confirmed order. This last point is an implementation recommendation. [Transaction flow references](https://github.com/ONDC-Official/developer-docs/blob/main/Tech_Quickstart_Guide.md).

Pramaan supports domain scenarios including F&B and prescribes mandatory flows, test reports, and certification. Development/test environments must remain visibly separate from real ordering. [Official Pramaan introduction](https://github.com/ONDC-Official/pramaan/blob/master/introduction.md).

ONDC advertises a live interoperable restaurant network, but city-level network activity does not guarantee a particular delivery address, restaurant, menu item, price, or ETA. Obtain those from production seller responses and live quotes. [Food-and-beverage network](https://ondc.org/pages/food-and-beverages.html).

### 2. Contract an ONDC buyer technology provider — possible faster route

The official ecosystem directory lists buyer-side Retail TSPs including **Aarambh.tech, Bitxia, Cyber Expert, Easy Pay, and ENS Enterprises**. These are procurement leads, not verified plug-and-play F&B APIs. The directory does not endorse providers or warrant their offerings. Before choosing one, request an embedded own-app RET11 demo, supported contract version, sandbox/live access, actual target-area sellers, fees, onboarding responsibility, settlement, refunds, and support ownership. [ONDC ecosystem directory](https://www.ondc.org/pages/ecosystem-participants.html).

The official participant directory lists **Magicpin** and **Adloggs** as F&B buyer/seller participants. That establishes network roles, not a right for Hungii to reuse their marketplace API. Magicpin's `/ondc/` URL redirected to its JavaScript merchant portal during this investigation; no first-party public self-service buyer API contract/credentials were established. [Participant directory](https://www.ondc.org/pages/network-participants.html), [Magicpin merchant portal](https://magicpin.in/partners/).

No provider contact or commercial commitment has been made. A provider agreement is still required before claiming one of these routes is enabled.

### 3. Direct restaurant partnerships plus delivery/payments — narrower alternative

Adloggs publishes delivery API documentation for creation, serviceability, status, cancellation, webhooks, and merchant wallet balance, with environment-specific keys from its merchant dashboard. This is a credible logistics integration lead, **not a public menu/restaurant inventory source**. Public docs inconsistently describe Bearer authentication versus `x-api-key`, and overview GET operations versus detailed POST operations; confirm actual endpoints and contract with the provider before implementation. No authenticated delivery calls were made. [Adloggs API](https://adloggs.com/resources/api-documentation/).

Its restaurant offering accepts orders from a restaurant's POS/ordering platform and dispatches riders. Thus Hungii would still need restaurant agreements, authoritative menus/stock, merchant acceptance, invoicing, cancellation/refunds, and a payment arrangement. Delivery access alone cannot convert an OSM restaurant into an orderable seller. [Restaurant delivery product](https://adloggs.com/case-studies/qsr-restaurants/).

This route could satisfy a limited launch with partnered restaurants and an agreed delivery area. It is not a shortcut to citywide inventory.

### Payment layer

Razorpay's official Android SDK supports an embedded checkout. Its integration requires server-created payment orders, server-side signature verification, and captured-payment status verification; test and live keys are distinct. Use provider events/status to recover pending results, not a client-only “paid” flag. A payment gateway supplies payments, not restaurant inventory or delivery. [Android integration](https://razorpay.com/docs/payments/payment-gateway/android-integration/standard/integration-steps/).

Live merchant access requires account activation/KYC and approval; test integration can precede activation. Which party collects and settles funds depends on the selected ordering agreement, so do not assume Hungii can collect marketplace funds with a basic gateway account. [Razorpay activation FAQ](https://axisbank-docs.razorpay.com/payments/FAQs/). The ONDC buyer reference separately configures Juspay credentials; those are not supplied automatically by its source code. [ONDC SDK configuration](https://github.com/ONDC-Official/ondc-sdk/blob/main/README.md).

## Public data that can enrich Hungii

| Source | Actually useful for | Does not establish |
| --- | --- | --- |
| OpenStreetMap / Overpass | Public mapped restaurant names, coordinates, optional cuisine/address/contact/opening hours | Live menus, item prices, stock, delivery serviceability, orders or payment |
| Open Food Facts | Packaged product barcode/name/label nutrients and ingredients | Nutrition of an arbitrary restaurant dish, delivery availability |
| USDA FoodData Central | Generic ingredient/food nutrient references, some branded foods | Exact Indian restaurant recipe, portion, menu or seller availability |
| TheMealDB | Recipe inspiration, ingredients and instructions | Restaurant sellers, orders, authoritative calorie totals |

### OpenStreetMap / Overpass

OSM restaurant tags support cuisine, dietary information, opening hours, contact, and related attributes when contributors supplied them. Missing fields must remain unknown. [Restaurant tagging](https://wiki.openstreetmap.org/wiki/Tag:amenity=restaurant). A sampled central-New-Delhi bounding box returned three real mapped restaurant records; this proves some India data exists, not national completeness or current business operation.

Overpass's main public operator gives an approximate **10,000 requests/day and <1 GB/day** guideline, applies dynamic slot/cooldown/load shedding, and expressly warns against relying on public instances as the backend of a general consumer app. Use a managed provider or own instance for sustained production. A timeout is an unavailable service, not an empty restaurant list. [Overpass operator guidance](https://dev.overpass-api.de/overpass-doc/en/preface/commons.html).

OSM data is ODbL: show **© OpenStreetMap contributors** and a license link. Attribution and applicable share-alike database obligations must be retained; this is not a requirement to open-source every unrelated app component. Map tiles have separate hosting policies. [OSM copyright/license](https://www.openstreetmap.org/copyright).

### Nominatim is geocoding, not nearby restaurant inventory

**Public-instance policy must guide any deliberate developer decision to use it:** absolute **1 request/second across the whole application**, identifiable User-Agent/Referer, visible attribution, caching, user-triggered queries, and an endpoint switchable without an app update. Autocomplete and systematic POI downloads are forbidden. The current policy additionally prohibits generic public geocoding/place services generated by low-code/vibe-coding platforms without direct informed developer responsibility. Do not use public Nominatim as Hungii's automatic address autocomplete. Prefer a hosted provider or self-hosted service. Do not send private addresses casually to a community geocoder. [Public Nominatim usage policy](https://operations.osmfoundation.org/policies/nominatim/).

### Open Food Facts

Current official documentation recommends **v3** and marks v2 deprecated. Read access needs no user login; identify the app and register usage through its form. Published limits are **15 product reads/minute/IP** and **10 searches/minute/IP**; avoid search on every keystroke. Volunteer records may be incomplete/inaccurate. Nutrients may be per 100 g or serving: preserve units, require consumed quantity, and show missing values rather than zero. Database license is ODbL, individual contents DbCL, and images CC BY-SA with possible additional artwork rights. This is product-label data, not restaurant nutrition. [OFF API and licensing](https://openfoodfacts.github.io/openfoodfacts-server/api/).

### USDA FoodData Central

Anyone can obtain a data.gov API key; keep it server-side. Default quota is **1,000 requests/hour/IP**. The public exploratory `DEMO_KEY` is **30/hour/IP and 50/day/IP**. The database is **CC0/public domain**, with source credit requested. Search/details support Foundation, FNDDS, SR Legacy, and branded-food records. Food matching and portion conversion need care; a generic cooked-rice record is not a known restaurant's biryani recipe. [FDC API guide](https://fdc.nal.usda.gov/api-guide/).

### TheMealDB

Official API supports recipe search, lookup, categories, and random selection. Development key `1` works for educational/development use; public app-store distribution requires supporter access. Some multi-ingredient/full-database features are paid. [API](https://www.themealdb.com/api.php). Terms allow copying/modifying returned API content but prohibit website scraping, require source attribution for artwork, and restrict API resale; check artwork's Creative Commons flag rather than treating all content as CC0. No public production SLA or numerical rate limit was established here. This adds optional recipe inspiration, not the required delivery flow. [Terms](https://www.themealdb.com/terms_of_use.php).

## Safe API observations

Run on 2026-10-04 using identifiable `Hungii-Research/0.1` User-Agent. Only public demo/development access was used. Responses were summarized; no private credentials or user data are stored here.

| Read-only request | Observed outcome | What it proves |
| --- | --- | --- |
| `overpass-api.de/api/interpreter`, nwr food POIs within 800 m of public Delhi landmark (28.6315, 77.2167), timeout 20 s, limit 8 | **HTTP 504** | Public endpoint can fail; not evidence of no restaurants |
| Same host, reduced node-only restaurant bounding box `(28.630,77.215,28.633,77.219)`, timeout 10 s, limit 3 | **HTTP 200**, 3 records: Indian Coffee House, Pind Balluchi, Fa Yian | Sample India POIs and optional website/cuisine/opening-hours tags exist |
| `world.openfoodfacts.org/api/v3/product/3017620422003.json?fields=code,product_name,nutriments` | **HTTP 200**, Nutella with nutrients including kcal, fat, carbohydrate, protein | Packaged-product nutrition endpoint works for this barcode |
| `api.nal.usda.gov/fdc/v1/foods/search?api_key=DEMO_KEY&query=rice&pageSize=1` | **HTTP 200**, 1 returned record (search reported 40,879 hits) | Public demo nutrient search works; not India menu data |
| `themealdb.com/api/json/v1/1/search.php?s=Arrabiata` | **HTTP 200**, Spicy Arrabiata Penne | Development recipe API works |

No Nominatim autocomplete or bulk requests were attempted. No ONDC production search/confirm/payment, Adloggs delivery, or gateway transaction was attempted because participant/merchant access and necessary agreements were not established. Public HTTP success alone is not production acceptance.

## Recommended product flow and honest failure behavior

These are product/implementation recommendations based on the scope distinctions above:

1. **Set delivery address once.** Ask for location only when useful; manual address must work. Provider setup belongs to the operator, not a shopper dialog full of backend choices.
2. **Home discovers orderable meals from live ONDC/partner catalogs.** Shuffle only real results eligible for that delivery address. Public recipes/places can be separate inspiration; never fabricate menu items or dress a public POI as a delivery seller.
3. **Meal detail → cart → checkout inside Hungii.** Show seller, customization, real quote/fees, address, delivery option, cancellation terms, and one clear primary action. Revalidate availability and price before payment.
4. **Pay → confirm → track in Hungii.** Preserve provider order/payment identifiers server-side and reconcile lost callbacks/pending results. No automatic blind second payment/order attempt. Only show success after authoritative confirmation; otherwise say “Checking your order” with safe status recovery.
5. **My Day logs consumed food.** Offer merchant nutrition when supplied; otherwise clearly label a user-approved estimate with source and portion. Do not silently assign packaged-product nutrients to a restaurant dish.
6. **Offline/degraded:** retain local logs and saved selections. Label cached public data with fetched time; do not allow stale inventory to create a purported order. Distinguish “No delivery available here,” “Couldn't load restaurants,” and “Ordering isn't available yet.” Keep the action simple: retry or edit address. Nutrition lookup failure should allow manual entry, not zero calories. No fake success, fabricated inventory, or simulator fallback in a phone release.

With the Hyderabad/no-outreach constraint, no available researched provider can currently be enabled to fulfill the complete delivery requirement. Public databases can make discovery/nutrition real, but cannot close the ordering gap. UI work can remove confusing Swiggy dialogs and prepare provider-neutral screens; it cannot truthfully claim real delivery checkout is released. No outreach, onboarding, or agreement is recommended or initiated without a change in that constraint.
