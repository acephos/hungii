# Hungii — Swiggy developer application draft

Prepared 4 October 2026. **Not submitted.** Individual developer profile/contact and terms acknowledgement must be supplied by the founder. This is a draft, not evidence of access or production approval.

## Form-ready description

**Integration:** Hungii Android — food discovery and user-confirmed ordering.

**Applicant:** Individual developer. Founder name, developer profile link and technical/security email: **to be provided**. No company or restaurant partnership is claimed.

**Initial geography:** Hyderabad, India. Actual delivery serviceability will come from the user's selected Swiggy address and live tool responses; city-wide availability is not assumed.

**Use case:** Hungii helps a person choose their next meal against their remaining food allowance, entered nutrition goals and taste preferences. Users connect their own Swiggy account, choose a saved delivery address, browse available meals, shortlist three and reveal a winner. The winner is a suggestion until the user reviews a current Swiggy basket, address, payable amount and returned payment option and explicitly confirms an order. Swiggy provides fulfillment and payment services. Hungii tracks the order and allows the user to separately log what they actually ate. Missing restaurant nutrition is displayed as unknown.

**Requested server:** Food only. Request seeded staging access first, followed by reviewed production access after validation. No Instamart, Dineout or Scenes integration is requested.

**Expected traffic:** Founder to confirm a small private pilot size and orders/day. Do not invent a production volume. API calls are serialized per user, with persistent request accounting/cooldowns and bounded payment polling.

## Architecture and authentication

```text
Hungii Android (Compose, encrypted local tracker)
  → WorkOS account authentication
  → authenticated Supabase Edge API, forced Mumbai (ap-south-1)
  → per-user OAuth PKCE connection and durable MCP Food session
  → Swiggy Food tools (runtime schema validation)
  → user-selected UPI intent/QR/payment bridge, Cash or Swiggy Money when returned
```

Proposed exact OAuth callback (requires Swiggy registration):

```text
https://qvqnzsqrxejdlvnbqwcs.supabase.co/functions/v1/hungii-api/callback?forceFunctionRegion=ap-south-1
```

WorkOS email sign-in is separate from Swiggy authorization. The Swiggy browser owns phone/OTP entry. Provider tokens, PKCE verifiers, session metadata and checkout recovery are encrypted server-side per user. The APK contains public identifiers, not service-role, encryption, WorkOS API or Swiggy tokens. Exact staging hosts/client registration come from Swiggy; none are guessed.

**Hosting:** Existing Supabase Free Mumbai project. Static egress IP is **not currently provisioned**; ask Swiggy whether the approved developer route accepts current Edge egress, or what allowlisting is required. No fixed IP, SLA or production availability guarantee is claimed.

## Privacy declaration draft

Local-only tracking needs no account. Cloud profile sync and saved meal shortcuts require separate consent. Device tracker and snapshots are encrypted with per-owner Android Keystore keys; backup/transfer exclusions are configured. Cloud tracker expires after 90 days without updates; saved shortcuts after 30 days; Swiggy credentials/session within five days. The checkout ledger stores only each attempt's basket, delivery address and payment/confirmation context, encrypted, to avoid replaying an uncertain order. It remains until account deletion; retention/minimization must be agreed with Swiggy before production. Disconnect removes connection credentials without cancelling orders or deleting recovery. Account deletion erases the ledger but does not cancel/refund an order. No production cloud inference over Swiggy data is enabled. The [privacy notice](privacy-notice-draft.md) remains a founder-review draft.

## Demo video script

Use the developer Simulator **with a visible fictional-data banner throughout**, then repeat against approved Swiggy staging once issued. Do not present authored fixtures as Swiggy staging or a live delivery.

1. Show the crimson Home page and remaining food/nutrition allowance.
2. Open Delivery settings: one connection action and address choice; show separate Profile and Privacy sections.
3. Search meals, shortlist three, shuffle and reveal the winner.
4. Review basket quantities and fees, with the returned payable total.
5. Choose a returned payment method; explicitly review address, items and total before confirmation.
6. Show pending payment; demonstrate failure and a refreshed retry, then success.
7. Show order confirmation and tracking. Log consumption separately.
8. Demonstrate restarting/recovering an interrupted checkout. Explain that real unknown placement blocks a second order and is not automatically replayed.

The Simulator demonstrates UX only. The actual prepared ordering coordinator is exercised by deterministic backend tests for changed totals, cross-owner quote rejection, repeated requests, ambiguous placements, UPI pending/success, COD and refund ambiguity. Neither is a substitute for provider staging validation.

## Questions to resolve during staging

- Actual `tools/list` simple-item and customization member schemas; replace/remove/empty-cart semantics. Public `cartItems: object[]` is insufficient to assert these.
- Price units and complete cart totals/charges. Confirm the currently documented ₹1,000 order cap.
- Payment surface identifiers, bridge domains, inline Swiggy Money, terminal failure/refund semantics and Food confirmation context.
- Durable session reuse across Edge workers and egress approval.
- Exact attribution text, meal sorting/offer eligibility rules and encrypted checkout retention.
- Successful staging for at least 48 hours, explicit production authorization, founder support contact and production identity/signing readiness.

## Submission

Use [Swiggy's individual developer application](https://forms.gle/4vkeKyqm15Qb6fnJA), linked from its [access page](https://mcp.swiggy.com/builders/access/). Attach this description and a hosted demo video. Submit only when the founder supplies identity/contact, reviews privacy/terms and explicitly authorizes submission. This document does not accept terms or contact Swiggy.
