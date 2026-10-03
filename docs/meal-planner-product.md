# Hungii: economic next-meal planner

Product specification and roadmap. The Android preview and synthetic Simulator implement parts of this design; use [current status](approval-readiness.md) for available behavior and launch gates. Requirements below include future work and must not be presented as completed features. The user approved clearly labeled nutrition estimates for broader choice; current Simulator estimates are fictional rather than calibrated.

## Problem and promise

People with irregular eating schedules can care deeply about gym goals and nutrition while relying on delivery for convenience. Choosing among many dishes, checking discounts, recording intake, and deciding what remains affordable later all demand attention at the moment they are hungry. The proposed audience includes students and industry freshers for whom repeated delivery can exceed their available food allowance.

Hungii's promise is: **find a practical next meal that fits today's remaining nutrition and money, explain the compromises, and help the person choose quickly.** The core product is a decision experience with a rolling daily plan. It does not require breakfast/lunch/dinner schedules or a diagnosis, and it does not promise medically appropriate targets or universally affordable delivery.

Swiggy Food is the first integration. Instamart can later offer ready-to-eat or user-approved minimal-preparation alternatives when restaurant delivery is poor value. Dineout and Scenes are outside this first product's scope.

## State that must stay distinct

- **Consumed:** meals, snacks, drinks, and portions the person reports eating, including food obtained outside Hungii.
- **Planned/reserved:** a selected or ordered meal that might be eaten later. Reserve its anticipated nutrition for planning without changing consumed totals.
- **Spent/committed:** the actual payable amount of an accepted order or reported outside purchase. Pending payment can hold a temporary reservation and must not become both a hold and a charge.
- **Expected opportunities:** eating occasions remaining in this food day, including the next one. Change them explicitly as the user's plans change; checkout alone does not mean an occasion has occurred.
- **Preference:** enduring restrictions and saved go-tos, distinguished from the current craving.

Keep recorded consumption and reservations separate. Show negative remaining values when a target is exceeded instead of hiding overshoot at zero. Daily intake containing estimates has an estimated remainder; do not subtract uncertain portions and then display a falsely exact number. Support undo/correction, partial portions, leftovers, shared orders, failed payments, refunds, and changing food-day boundaries without double-counting.

## Journey

1. **Check in.** An animated orb gives a short greeting and a compact tracker: consumed calories and protein/carbs/fat, remaining targets, remaining food allowance, and expected meal opportunities. A text and tap path is equally available; speech and animation are optional.
2. **Update naturally.** Examples: “I had two eggs,” “that was only half the bowl,” “I can spend ₹200 more today,” or “I have one more meal after this.” Extract a proposed change, distinguish setting a total from adding to it, resolve important portion/intent ambiguity, and show a brief receipt with undo. Do not overwrite historical intake from a vague sentence.
3. **Capture the craving.** Ask “What do you feel like?” Extract spicy/bland/sweet/cheesy, light/filling, cuisine, prep tolerance, vegetarian preference, delivery urgency, and current price ceiling. Ask only questions that materially change the search, one at a time. Previously saved constraints need not be repeated.
4. **Build a bounded pool.** Present roughly six to eight candidates, rather than an endless feed. Keep a small diverse batch and refresh deliberately. A current card stays stable while background information changes; flag material changes before selection.
5. **Round one — find three finalists.** Left means pass for this decision; right adds the meal to one of three visible finalist slots. Buttons provide equivalent actions. Show progress such as “2 of 3 finalists” and keep Undo available. Once three are selected, pause discovery and show “Meet your finalists”; do not silently advance or require the person to finish the original pool. Saving a go-to remains explicit and distinct from shortlisting.
6. **Round two — lucky draw.** Freeze the three user-selected finalists and briefly show them face up: “Three meals you'd eat. Let luck pick.” Turn the cards face down, shuffle their positions, then ask “Pick a card.” The person taps one face-down card; it flips to reveal the winner and its nutrition, price estimate, ETA, and trade-offs. Offer “Let's have this” to continue to meal review, plus a way to view or change the finalists if the reveal no longer appeals. The draw chooses among already acceptable meals; it does not place an order.
7. **Verify and confirm.** After the person chooses a candidate and agrees to update their Swiggy cart, refresh stock, variants, payable price, and usable payment/coupon conditions. Show any change. Require separate explicit order confirmation; a swipe is never purchase authorization.
8. **Record eating.** After delivery, ask whether the meal was eaten, how much, or whether some was saved. Confirm nutrition consumption separately from order placement. Support a one-tap “ate all” entry and later corrections.

Voice states can include idle, listening, processing, and speaking, but should be understandable without animation. Respect reduced motion, provide captions, and avoid forced microphone permissions, streak loss, guilt language, and compulsive infinite swiping. “ADHD-friendly” is a design goal to validate with users, not a clinical claim.

### Two-stage selection rules

The two-stage journey is a user requirement: first form a top-three shortlist, then choose a face-down card in a shuffled lucky draw. Round one asks “Would I eat this?”; round two asks “Pick a card.” The user controls which meals are eligible, then deliberately hands the final selection to chance. Whether this reduces decision friction for ADHD users remains a design hypothesis to validate.

- Three finalists is the normal target, not a reason to force an unwanted selection. If fewer acceptable candidates exist or the person wants to stop, let them explicitly continue with one or two. One finalist goes to the winner review; two can use the same face-down draw with two cards.
- During round two, do not inject new recommendations, shuffle finalists, or silently change portions. An explicit “Change finalists” action returns to round one and preserves selected candidates.
- Shuffle the meal-to-position mapping with equal probability and keep it fixed while the person chooses. With three finalists, each has a one-in-three chance of being drawn. Do not secretly favor a higher-ranked meal, sponsored restaurant, or cheaper option. Face-down cards must not leak meal-specific clues; all positions have equivalent appearance and hit targets.
- Complete the shuffle before enabling selection, and accept only one card choice per draw. Use a short, finite shuffle with an equivalent reduced-motion path; selection does not require tracking the moving cards. Accessible controls identify positions as “Pick card 1,” “Pick card 2,” and “Pick card 3,” and announce the revealed meal after selection.
- Reveal the winner's full identity and the same estimate/source labels and after-meal consequences shown in round one before asking the person to proceed. A “View finalists” path permits an explicit manual choice or a return to selection; the user is never bound to the random result. Do not create an automatic loop of repeated draws.
- Selecting a winner opens the meal review and live-cart verification. The winner stays selected while verification runs. If its availability, price, or nutrition assumptions materially change, show the change and offer acceptance, adjustment, or return to the remaining finalists. Never silently substitute a runner-up or restart the whole deck.
- The two rounds are a finite decision aid. Use calm progress and optional reduced motion; avoid timers, streaks, scarcity pressure, or requiring additional swipes to unlock checkout.

## What a meal card represents

A candidate records a restaurant, current address/serviceability, specific dish IDs, quantities, variants, add-ons, intended eaten portions, and leftovers. A multi-item meal initially comes from one restaurant. Comparing a single dish price with a multi-item checkout total is misleading.

The collapsed card prioritizes the meal name/photo when available, restaurant, full-price estimate or verified cart total, ETA estimate, calories, protein, and two short trade-offs. Expand for carbs/fat, nutrition provenance, assumptions/ranges, coupon conditions, bill components when known, and post-meal daily state.

Example card copy, **entirely illustrative**:

> Spicy chicken rice bowl · ₹220–250 estimated checkout · ~25–35 min
>
> Nutrition estimated: 550–700 kcal · protein 32–42 g · carbs 55–75 g · fat 15–25 g
>
> Why this one: good protein for the spend; matches spicy preference.
>
> Trade-off: leaves less money for your last meal; nutrition is less certain than the published-option alternative.

Use understandable bars for cost, protein contribution, delivery speed, and craving fit. Bars reflect a defined target or comparisons within this pool; they are not invented health scores. Calories/protein themselves remain visible in units. Avoid pseudo-precise match percentages or rewarding maximum protein without considering the day's plan.

## Nutrition policy

The user approved estimates for meals without published nutrition. Keep three distinct states: restaurant/brand-published nutrition for the matched portion, estimated nutrition with documented evidence/assumptions, and insufficient information.

Nutrition attaches to a portion and customization, not just a dish name. Sources may include restaurant nutrition information or an independently sourced nutrition dataset and recipe/portion evidence; source selection and calibration remain research work. Language-model guesses alone do not establish trustworthy nutrition. Do not invent fixed confidence percentages or uncertainty ranges without evidence. A published serving value is still a reported value, not a measurement of the delivered meal.

Track ranges through the remainder and trade-off calculation. Permit an unknown candidate with honest unknown stats when the person chooses, but do not pretend it is macro-verified. Changes to sauce, cheese, oil, sides, quantity, or portion require recomputation and may reduce confidence. Nutrition values in order history are not assumed to exist.

## Ranking and future-meal feasibility

The problem is constrained, multi-objective selection: money, calorie/macro fit, enough remaining resources for later opportunities, craving fit, speed, and confidence compete. There is no universally best scalar score.

1. Interpret user input into explicit constraints and preferences. The conversational model handles language; arithmetic, state updates, eligibility, and ranking use repeatable rules.
2. Search at the user's selected address using a bounded set of intent-derived queries. Dedupe the same restaurant/dish/variant and diversify restaurant, cuisine, preparation, price, and familiarity.
3. Exclude unavailable/unserviceable candidates and respect explicit dietary restrictions. Missing allergen data is not evidence that a meal is safe. Never silently relax hard restrictions. A user-stated absolute spending ceiling is respected; any alternative exceeding it is shown separately for explicit approval.
4. Allocate the remaining daily targets across expected opportunities with user-adjustable weights. Equal division is a starting reference, not a requirement. Light/filling, urgency, and expected later eating can change the next-meal allocation.
5. Evaluate the post-meal state. A cheap first meal that leaves an expensive protein shortfall later can be poor value for the day. Reserve plausible cost/nutrition capacity for remaining opportunities using available evidence; show infeasibility when it cannot be established rather than claiming a guaranteed feasible day.
6. Remove dominated candidates only when the underlying data and uncertainty are comparable. Create a balanced pool: best overall fit, protein value, lowest expected checkout, fastest, craving match, and a familiar option. Keep user-controlled trade-offs rather than treating a single weighted score as truth.
7. Explain with actual deltas: “₹60 above your next-meal guide,” “~12 g less protein than your guide,” or “leaves ₹170 for one later meal.” Values inherit nutrition/price uncertainty. Show an achievable suggestion when the original day is no longer feasible; do not encourage extreme catch-up eating or skipping food to satisfy a target.

Soft preferences can relax in a visible order, for example preferred restaurant or cuisine before craving and delivery time. Ask before exceeding a stated strict calorie or spend ceiling. Never promise a nonempty pool when the address is unserviceable, everything is unavailable, or hard constraints cannot be met. Provide an honest reason and the smallest user-approved adjustment or alternative. Passed candidates do not return automatically; ask whether the person wants to revisit them.

## Coupon and cost optimization

Optimize final payable rupees and the day's plan, not menu subtotal or discount size. Consider the exact meal, alternative portions/customizations, and a small number of nutritionally useful same-restaurant additions that could cross an offer threshold. Price reductions only count if the final payable total is lower; optional additions also have a nutrition/leftover effect.

Hypothetical arithmetic, **not a real Swiggy offer**: items ₹280 + charges ₹40 = ₹320. A useful ₹30 side might cross a ₹300 item threshold and unlock ₹100 off, giving ₹250 if charges remain ₹40 and the offer actually applies. The ₹70 difference is a saving only after a live cart confirms it. Buying more merely to claim a bigger nominal discount is not an economic recommendation. A second restaurant order incurs its own charges and is not the default optimization.

Swiggy's coupon schema provides applicability and textual terms, not guaranteed structured numeric thresholds/caps. Parsed conditions are hypotheses until validated. Live payable totals require changes to the shared Food cart; there is no documented isolated quote endpoint. Therefore both selection rounds use labeled estimated prices until the chosen winner is validated through controlled, consented cart changes. Do not overwrite the person's existing Swiggy cart to test every swipe card or finalist. Recheck cart state because another device can change it; automatic rollback is not assumed safe.

See [API feasibility](research/meal-planner-api-feasibility.md) for verified schema limits and source links. These limits make exact global “cheapest possible” claims inappropriate. Optimize a bounded set of found candidates and explain what has been verified.

## Go-tos and history

With explicit consent and permitted retention, offer order-history-assisted suggestions for saving meals and restaurants. Frequency is evidence of familiarity, not necessarily preference or value; the user decides what becomes a go-to. Preserve user-owned notes and portion preferences. Refresh current IDs, prices, availability, customizations, and nutrition matches before reuse. Expensive historical habits must not dominate an economic planner. Include manual favorites, dislike history, and an easy deletion/reset path.

## First release and learning goals

First release: Food discovery; user-set daily nutrition/food targets; meal opportunity updates; voice/text check-in; transparent nutrition estimates; bounded swipe pool; trade-off cards; top-three shortlisting followed by a face-down shuffled lucky draw; controlled live cart verification; explicit checkout; consumed-portion tracking; and saved go-tos.

Defer autonomous ordering, precise day-long global optimization, a full cross-provider catalogue, social competition, and treating delivery as automatically eaten. Estimate quality and truthful pricing are release gates, not cosmetic badges.

Validate with real students/freshers and irregular-schedule gym users: can they choose faster, stay within their chosen spend, understand estimates, and record food with little effort? Measure time to confident choice, corrections/logging friction, estimated versus verified checkout gaps, actual day spend, and appropriately evaluated nutrition estimate quality. Session length and swipe count are not success metrics. Research/analytics use of Swiggy-originated data needs the consent and contractual handling described in the provider docs.

## Questions before stack selection

1. Can we obtain sufficiently reliable portion-level nutrition evidence for a useful initial set of meals?
2. Does Swiggy permit the intended meal-level browsing and controlled cart-comparison experience under its documented user-selection guidance?
3. What initial city, address cohort, food allowance, and preparation tolerance provide a realistic affordable pool?
4. What is the simplest interface that users can complete while hungry: voice-first, card-first, or a hybrid?

These determine the required integration, nutrition, ranking, state, and voice capabilities. Choose the stack after these boundaries are understood; no stack can create missing provider nutrition or isolated quote APIs.
