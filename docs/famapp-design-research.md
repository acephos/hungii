# FamApp reference and Hungii Android design direction

Checked 2 October 2026. Research uses the official FamApp website, developer-published Google Play screenshots and Android documentation. The app formerly called FamPay is listed as **FamApp by Trio**. These references establish a visual direction, not access to its source code, interaction timings or private design system. [Official site](https://www.famapp.in/), [official Android listing](https://play.google.com/store/apps/details?id=com.fampay.in)

## What the reference actually shows

The official website uses black backgrounds, white headings and yellow/amber filled pill buttons. Browser-computed website styles name Metropolis, with 32 px button radii and an amber `rgb(245,166,35)` download button. Font requests returned 404 in this browser, so the CSS family name does not prove which font rendered or which font the native app uses. [Website](https://www.famapp.in/)

Developer-published screenshots provide more useful mobile references:

- **Payment:** large amber amount above the recipient, quiet dark backdrop, round avatar, grouped charcoal payment-source rows with radio selection, then a wide amber `Pay ₹500` pill. The amount is the primary information; the CTA repeats it. [Payment screenshot](https://play-lh.googleusercontent.com/SFmCQLHFt3caXM5zgb8yiWD1nI0GmBAxvbZRydQHoFYKW9kIq4X_kwgMofJSt-uo_-55b9xcco08o1LgGWyKdPI=w1000)
- **Home:** charcoal canvas, dark filled capsule search, circular profile/actions, lightly outlined capsule shortcuts and a row of circular recent contacts. The prominent scanner is surrounded by quiet controls, with muted labels and generous separation between groups. [Home screenshot](https://play-lh.googleusercontent.com/hDsI5kM78Wq4xyWepKUNbMZ5IeI4uM-Ip43q72viPPFahyZLhe2aWoX-H_rWttGl-UuyVJUneKslUzkxixKFAA=w1000)
- **History:** a rounded floating favourites group with circular portraits, then spacious rows pairing a large avatar with a name and muted amount/time. Borders are subtle rather than bright separators around every item. [History screenshot](https://play-lh.googleusercontent.com/3zxnwpDojYq2B4dFo2s__m4mxfRlQvBOWSCusugPhXaAS0jjRWI9DEpmBue2xJV0p3El7Pq93KNj0UA5OKyi1Q=w1000)
- **Search:** a filled rounded search bar with inline back/control icons, a rounded contact grid and a quick-action row. Bold white geometric-looking type contrasts with muted secondary labels. This screenshot advertises a swipe-down quick-action gesture; it does not expose its easing or duration. [Search screenshot](https://play-lh.googleusercontent.com/0LpZCMpv4wlOgmdvJGF4DQmMAUiwa6D33n_r_xoz9FpvalBkXRqN50oNPuY_ThW_TFDAi1JyrDFb4uQOZfcbgw=w1000)

FamApp's engineering team describes its home as contextual groups containing personalised cards, and its latency work stresses avoiding redundant feed reconstruction. This supports a responsive grouped home rather than a wall of equally weighted panels. The proposed Hungii UI below does not require copying FamApp's server-driven architecture. [First-party engineering article](https://blog.famapp.in/blog/building-a-50-ms-home-feed-part-1-design/)

## Hungii design tokens

The following values are **Hungii recommendations**, not extracted FamApp mobile tokens. Preserve the founder's charcoal/electric-lime preference while adopting the reference's hierarchy, rounded forms and quiet surfaces.

| Token | Suggested value | Purpose |
| --- | --- | --- |
| Background | `#0B0D0C` | Near-black app canvas |
| Surface | `#171A18` | Main panels and meal card body |
| Raised | `#232824` | Input, selected payment row, bottom dock |
| Border | `#323A33` | Quiet 1 dp separator |
| Primary | `#CAFF48` | Main action, key budget number, selected state |
| Text | `#F5F7EE` | Heading and body |
| Secondary | `#AFB7B0` | Readable supporting copy |
| Cyan / Coral | `#90DAD7` / `#FF9A82` | Macro accents and trade-offs, accompanied by labels |
| Spacing | 4, 8, 12, 16, 20, 24, 32 dp | Shared layout rhythm |
| Screen inset | 20 dp; 24 dp where space allows | Consistent alignment |
| Radius | 20 dp fields, 24 dp cards, 28 dp sheets, fully rounded CTA | Soft coherent shapes |
| CTA | 56–60 dp tall | One prominent action per step |

Replace the current condensed all-capital headline treatment with uncondensed `FontFamily.SansSerif` initially; avoid adding a paid font or downloading an unverified reference asset. Use bold 28–34 sp sentence-case titles, 40–48 sp hero numbers, 18–20 sp section headings, 14–16 sp body and 12–13 sp secondary labels. Line height should be around 1.15–1.4 times text size. Existing 7–10 sp macro badges and budget caveats need enlargement. Reserve capitals for short optional category labels. These are design choices inferred from visual inspection, not a claim about FamApp's exact typeface.

## Native layouts

**Home / orb.** Start with a friendly short greeting and one large remaining-budget figure. Put the voice-reactive orb beside or below that anchor rather than a giant slogan. A compact tracker card shows calories, protein, money and opportunities, with an edit action. Follow with a filled capsule composer: leading sparkle/search icon, `What sounds good?`, trailing mic and send. Tapping mic preserves the typed fallback. Mood choices are horizontally scrolling pill chips. A single lime `Find my meals` CTA opens discovery. Saved meals use circular thumbnails in a compact favourites row, following the reference's recents pattern.

**Discovery.** A small `Pick your 3` title, shortlist progress and filter control precede a single dominant meal card. Use a broad food image, gradient scrim, readable name and restaurant, price/ETA pills, then four evenly spaced macro values. Show at most one benefit and one compromise initially; expand nutrition uncertainty, offer conditions and details on demand. Price explicitly distinguishes menu price from quoted cart total. Swipe remains available with visible skip/save buttons so gestures are optional. The saved count responds immediately.

**Draw.** Show the three finalists and their differences before turning them over. A brief shuffle ends with three large independent selectable cards. Keep a `Choose a card` prompt and optional `Skip animation` action; lock repeated selection during the reveal. After reveal, winner details lead directly to `Review cart` rather than another choice maze.

**Cart.** Give restaurant/address and quantity controls their own compact groups. Coupon candidates are collapsed by default, with the currently cheapest verified mock quote selected visibly. Item subtotal, delivery, packaging, taxes and discount produce one large final amount. Show macro/budget impact beside the total. A sticky `Choose payment · ₹…` CTA advances to a rounded sheet/screen with clear simulated payment methods and radio rows inspired by the reference payment-source selector.

**Mock payment and receipt.** Header and persistent badge say `Simulated checkout`; no real UPI PIN, card number or payment credential field. The confirmation repeats the final amount and chosen method. Processing explains its stage. Success shows the local order ID, receipt, delivery timeline and `Log when eaten`; placing an order must not automatically claim the meal was consumed. Failure preserves cart and offers retry/change method. These checkout specifics are Hungii product recommendations rather than FamApp behavior claims.

**Navigation and account.** Use a quiet floating rounded bottom dock with icon + text, an obvious selected lime state and consistent insets. Focused discovery/draw/checkout steps can hide the dock. Keep local-only access, optional cloud sync, account controls and clear separation between the account and provider connection; the visual redesign must not turn login into a prerequisite for mock meals.

## Motion and loaders

These durations are implementation recommendations; the primary references above are static images and do not establish FamApp's native motion system.

| Interaction | Suggested treatment |
| --- | --- |
| Button/chip press | Scale to 0.97 over 100–140 ms; preserve Material ripple |
| Selection / field focus | Colour/border interpolation over 150–200 ms |
| Screen transition | Fade + 12–24 dp slide over 220–280 ms |
| Meal swipe return | Damped spring; drag translation and rotation on a graphics layer |
| Save / shortlist count | Small 180–240 ms scale and count crossfade |
| Expanded bill/details | 220 ms size animation; no sudden jumps |
| Draw | 700–1000 ms shuffle, 300–400 ms flip; finite and skippable |
| Orb idle / voice | Gentle breathing only while visible; voice amplitude drives scale/rings |
| Search loader | Stable meal-card skeleton with subtle 900–1200 ms shimmer; status `Finding meals…` |
| Payment loader | Small ring or three-dot pulse plus concrete stage; no fake progress percentage |
| Success | Brief check stroke/scale animation, then static receipt |

Compose supports `AnimatedVisibility`, `AnimatedContent`/`Crossfade`, `animateContentSize`, `animate*AsState` and repeating transitions. Prefer `graphicsLayer` for swipe/flip/scale so visual motion does not force repeated layout, and remove completed loading content from the composition. Use bounded state transitions rather than letting an infinite loader represent errors. [Official animation guide](https://developer.android.com/develop/ui/compose/animation/quick-guide)

Use themed filled `TextField` surfaces for the composer and goal inputs; maintain clear labels, sensible keyboard/IME actions and an inline error/help area. Avoid placeholder-only account forms. [Official text-input guidance](https://developer.android.com/develop/ui/compose/text/user-input)

Preserve at least 48 dp touch targets, content descriptions for icon controls, visible text alongside colour-only status and a readable order of accessibility focus. Use the device animation-scale setting to skip shuffle/flip/shimmer when animations are disabled, and keep explicit buttons for swipe alternatives. [Official accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)

## Scope and verification

Observed sources support the visual characteristics above. They do not establish FamApp's exact native font, spacing, easing, loaders, accessibility or current signed-in user experience. Implement shared Hungii components and retain Hungii branding, fictional meal imagery and mock labels; the research images are references, not app assets. Review on a narrow Android screen with the keyboard open, large font scale, reduced motion, long meal names, payment failure and an over-budget cart. The result should feel responsive and visually coherent while preserving readable calorie estimates and cart totals.
