# Hungii UI prototype

Throwaway review artifact on branch `prototype/meal-lucky-draw`.

**Question:** which layout makes the orb → top-three shortlist → face-down lucky draw → meal review journey easiest to complete?

Run from the project root:

```sh
python3 prototype/serve.py
```

Open http://localhost:5173/prototype/. The page also works by opening `prototype/index.html` directly; browser speech support can depend on the origin.

## Layouts

- `?variant=a`: Companion — conversation, central swipe deck, and a persistent daily tracker/finalist rail.
- `?variant=b`: Pocket — a compact vertical flow with budget counters, a single meal deck, and expandable tracker.
- `?variant=c`: Daybook — a daily summary above a wider image-and-stats card, with finalists alongside.

Use the floating arrows or keyboard left/right to switch layouts. In-memory decisions carry across layout changes. Reload or Restart resets the sample day. No local storage or database is used.

The layout switcher appears on localhost/file previews or with an explicit `?review=1` flag; it is hidden on other hosts by default.

## Try the journey

1. Type a craving or tracker update to the orb. The example buttons show the supported scripted phrases. Edit daily targets directly if preferred.
2. Drag a meal card left/right, or use pass/shortlist buttons. Save a go-to using its heart.
3. Collect three finalists. Turn them face down with **Shuffle my finalists**, then pick a card after the shuffle finishes.
4. Review the reveal. Continue to a simulated bill or revisit your finalists. Each card position has an equal chance; the shuffle never replaces finalists.
5. Confirm a **demo meal** to record sample spending and reserve nutrition. Log all or half as eaten to update intake separately.
6. Try the cheesy flatbread finalist for the sample coupon-threshold side option. Adding its curd side recomputes both the sample bill and nutrition.

## Boundaries

All restaurant names, dish metadata, nutrition ranges, timing and offers are fictional fixtures. Stock food photographs illustrate visual hierarchy and are not photos of the named meals. The conversation uses a small local phrase parser, not an LLM. There are no Swiggy requests, real quotes, real orders or payments. Voice recognition and speech playback use browser capabilities only when requested, with text input always available; microphone input was not exercised during automated browser review.

The prototype implements the user-approved two-stage journey and tests its usability question. No layout has been selected or validated by users yet. The prototype is not a production stack choice and should be rewritten when the design is accepted. There is no connected implementation issue; this file is the context pointer to the review branch.

## Verification record

Browser interaction review checked: three distinct finalists; disabled picking while shuffling; generic face-down labels without meal-photo clues; one reveal without spend/intake changes; separate sample spending and eating logs; partial-portion reservations; natural-language edits and undo; direct budget edits; desktop and mobile layout widths; and browser runtime errors. Additional checks cover the threshold side, insufficient budget, swipe gesture, fewer finalists, and reduced motion. No permanent test suite was added.

The T3 shared preview opened and supported initial evaluation, but its click/snapshot functions failed and it later reported the host unavailable. Remaining checks used local headless Chromium. The development server can still be opened from the link above.

## Photo sources

Stock images downloaded from Unsplash for this throwaway prototype:

- `meal-1.jpg`: https://images.unsplash.com/photo-1512621776951-a57141f2eefd
- `meal-2.jpg`: https://images.unsplash.com/photo-1546069901-ba9599a7e63c
- `meal-3.jpg`: https://images.unsplash.com/photo-1511690743698-d9d85f2fbf38
- `meal-4.jpg`: https://images.unsplash.com/photo-1565299624946-b28f40a0ae38
- `meal-5.jpg`: https://images.unsplash.com/photo-1547592180-85f173990554
- `meal-6.jpg`: https://images.unsplash.com/photo-1540189549336-e6e99c3679fe

Google Fonts supplies Manrope and DM Sans; local system fonts are the fallback.
