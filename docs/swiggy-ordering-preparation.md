# Prepared Swiggy checkout — 0.8.0

4 October 2026. The single public APK is **hungii.apk**. `real` retains the original application ID and signing identity for updates. `demo` remains a developer-only build, with a fictional-data banner; it is no longer a second APK in new GitHub releases. Historical assets are preserved.

## Implemented flow

```text
per-user Swiggy connection → selected address → discover → shortlist/draw
→ basket → fresh quote/payment choices → explicit confirmation
→ durable checkout claim → place_food_order (one attempt)
→ pending UPI → check_payment_status → confirm_order only after success
→ confirmed order → live tracking → separate manual consumption log
```

Delivery, Profile and Privacy controls are separate sections in a rounded bottom sheet. The consumer build has a local plan check-in; it no longer directs users to a second Simulator APK. Real meals never fall back to fictional inventory. Staging and Simulator states are explicitly labeled.

`ordering.ts` is the actual backend coordinator, also exercised by offline authored contract tests. An encrypted, owner-bound two-minute quote carries the reviewed basket, address and available payment methods. Before placing an order, the backend rechecks basket identity/contents/prices and method availability. The currently documented ₹1,000 total cap is enforced. Unsupported/unknown currency remains unavailable. Live payment links require HTTPS and a Swiggy domain; additional returned provider domains need explicit staging verification.

`hungii_checkouts` saves the attempt before the non-idempotent placement call. A database transaction and unique active index block overlapping attempts. Repeated request IDs return recovery instead of replaying placement. An interrupted or ambiguous placement stays unresolved, even after worker retirement. UPI context/coordinates are echoed from the provider response and remain encrypted on the server. Pending is never shown as placed. Refund ambiguity blocks retries. Tracking failure does not undo a known confirmation or double-count spending. The app persists the request ID before its call and offers recovery on the Home page after restart.

## Required staging activation

Apply migration `202610040001_checkout_attempts.sql` before deploying the handler. The table has RLS and no public/anon/authenticated direct access. All access goes through verified WorkOS ownership. No migration is automatically applied by CI.

Keep all gates false until reviewed access and verified fixtures are available:

```text
SWIGGY_ORDERING_ENABLED=false
SWIGGY_PRODUCTION_APPROVED=false
SWIGGY_CART_CONTRACT_VERIFIED=false
SWIGGY_SESSION_RESUME_VERIFIED=false
SWIGGY_PRICE_UNIT=unverified
SWIGGY_CART_ITEM_ID_FIELD=
SWIGGY_CART_QUANTITY_FIELD=
```

Set item/quantity field names from the actual approved simple-item schema, not the Simulator. `withFood` still validates every call against authenticated runtime input schemas and restricts writes to the specific basket/coupon/placement/confirmation tools. The Food endpoint and OAuth client/redirect must be registered by Swiggy. Activation must first target Swiggy staging. Production additionally requires `SWIGGY_PRODUCTION_APPROVED=true` after explicit Swiggy approval; changing the endpoint alone cannot enable production writes.

## Deliberate remaining limits

- Public cart docs omit nested item/customization request details. Initial prepared writes support only verified simple items. Variants/add-ons and edits of customized existing baskets remain explicitly unavailable until their staging contract is implemented and tested. A user can complete them in Swiggy; that is not claimed as in-Hungii ordering.
- Unknown placement without usable payment/order identifiers cannot be safely matched to an order automatically. It stays blocked for support reconciliation. There is no client-controlled retry unlock. Account deletion erases recovery, so the UI warns to check unresolved orders first.
- Checkout records currently remain until account deletion. Production retention/minimization, support reconciliation and refund accounting require provider/founder review. No automatic refund credit is applied to the food allowance.
- Saved-address lookup currently scans at most ten pages and fails clearly if the selected address is not found, rather than inventing a delivery address.
- UPI authorization uses the user's UPI app/payment bridge; Hungii does not collect a PIN or VPA.
- No Swiggy credentials, staging access, production access or live transactions have been established by these changes. A simulated demo and unit tests do not establish production readiness.

See the [application draft and demo script](swiggy-developer-application.md), [setup](swiggy-setup.md), and [privacy draft](privacy-notice-draft.md).
