# Mobile design verification — v0.7.5

Checked 3–4 October 2026 on Android 15, emulator `Hungii_Pixel`, against version 0.7.5 (code 13). Sources and implementation choices: [mobile UI](../docs/research/mobile-ui-video-research.md), [UX psychology](../docs/research/ux-psychology-design-research.md), [product-page redesign](../docs/research/product-page-design-research.md).

## Visual evidence

| Flow | Before / after |
| --- | --- |
| Home hierarchy and four destinations | [v0.7.4 before](mobile-ui-home-before.png), [v0.7.5 after](mobile-ui-home-after.png) |
| Meal identity, price, contrasting image control and persistent Pass/Keep | [v0.7.4 before](mobile-ui-meals-before.png), [v0.7.5 after](mobile-ui-meals-after.png) |
| Basket quantity and returned total | [2× basket](mobile-ui-basket-after.png) |
| Day editor at larger font size with numeric keyboard | [315 dp width, 130% text](mobile-ui-goals-large-keyboard.png) |
| Failed discovery with recovery actions | [Offline response](mobile-ui-discovery-error.png) |

Images are raw captures. Meal and basket images use Simulator: fictional restaurants, synthetic addresses, stock food photos and no real orders or money. No video was reopened or played during the supplied-transcript work.

## Device checks

- Home, Meals, Assistant and My day form four destinations; Saved is reached from Meals. Tapping navigation does not launch a network request or settings dialog.
- Filters are draft values until applied. Pass/Keep work without a swipe. Undo is disabled without a prior choice; shortlist progress advanced through the actual 1/3, 2/3 and 3/3 choices.
- Focused finalists and shuffled-card selection preserve back navigation and hide the primary dock. Winner exposes the price and allowance context.
- With the emulator temporarily offline, discovery displayed the specific failure, Retry and Check connection. After networking returned, Retry loaded meals again.
- Day editing and the nested meal details scroll remained usable at 315 dp width and 130% text. Save stayed visible above the real numeric keyboard. Normal checks used 360 dp width. Display size, font scale, keyboard and airplane settings were restored.
- In the synthetic basket, 1× returned ₹240.45; 2× returned ₹368.90. The persistent checkout action updated to that amount and displayed ₹18.90 over the current ₹350 allowance. Payment review and its confirmation repeated ₹368.90; confirmation was dismissed.
- The real build installed as an update and opened its existing local day plan without requiring sign-in. Existing plan values were retained. Fresh-install first-use copy was reviewed in source; existing app data was preserved.

## Automated checks and limits

Both real and demo flavors pass their unit tests (20 executions total, including six allowance comparison cases per flavor), debug assembly and Android lint. Allowance tests cover estimated versus menu-only cost, fee qualification, overspend, non-positive allowance, invalid/unknown cost and explicit zero. Repository publication guard and whitespace checks pass. Lint retains existing advisory warnings.

After a failed basket update, the UI requires Refresh basket before payment. Loading removes the actionable prior total. An offline 3× update was forced on-device: Refresh basket appeared; after networking returned, refresh restored the unchanged confirmed ₹368.90 total and checkout action. The Swiggy tool names, parameters, provider contract and hosted backend were not changed. Live Swiggy access, real payment, user-study outcomes, rating data and medical suitability of default nutrition targets are not established by these checks.
