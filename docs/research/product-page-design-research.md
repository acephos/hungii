# Product-page redesign: meal and basket hierarchy

Checked 3 October 2026 against the user-supplied transcript of [This UI/UX Redesign Will Teach You More Than 100 Tutorials Combined](https://www.youtube.com/watch?v=GGg61sdEjeI). The timestamped text supplied in this thread is the source; the video was not reopened. The recommendations below are paraphrased design ideas, not verified conversion claims. Sponsor offers and commercial design tools are outside this implementation.

## Timestamped findings and Hungii choices

| Supplied section | Hungii application |
| --- | --- |
| [0:38–1:26 Image controls](https://www.youtube.com/watch?v=GGg61sdEjeI&t=38s) | Place image-overlay buttons on a contrasting surface with a thin outline, retaining generous touch targets and accessible labels. Test against bright, dark and busy meal photos. |
| [2:00–3:32 Product imagery](https://www.youtube.com/watch?v=GGg61sdEjeI&t=120s) | Keep supplied meal photos and their association with the actual item. Use consistent image frames and clearly identified placeholders when imagery is absent; do not replace provider photos with invented dishes, altered portions or new artwork. |
| [3:35–4:14 Alignment](https://www.youtube.com/watch?v=GGg61sdEjeI&t=215s) | Use a shared 24 dp content margin for the relevant native screens. Align titles, prices, details and actions on the same grid rather than stacking unrelated padding. |
| [4:17–6:49 Color and type](https://www.youtube.com/watch?v=GGg61sdEjeI&t=257s) | Keep Hungii’s charcoal and lime identity. Reserve stronger emphasis for primary actions and selected states; use readable secondary text, consistent type hierarchy and comfortable paragraph line height. Keep badges short and factual. |
| [6:51–7:26 Trust information](https://www.youtube.com/watch?v=GGg61sdEjeI&t=411s) | Group the meal title, restaurant and price so item identity and cost are easy to scan. Show a rating or review count only if the actual provider result supplies it; do not manufacture trust signals to fill the example layout. |
| [8:20–9:44 Icons and separation](https://www.youtube.com/watch?v=GGg61sdEjeI&t=500s) | Use the existing outlined icon family and restrained secondary colors. Keep divider lines quiet; use spacing to distinguish sections without unnecessary nested containers. |
| [9:47–10:55 Price and quantity](https://www.youtube.com/watch?v=GGg61sdEjeI&t=587s) | Present the rupee amount early. Keep essential qualifiers such as menu price, estimated fees and nutrition uncertainty. Separate selected order quantity from the item title, while preserving meaningful serving information in the provider’s original name. |
| [10:58–12:41 Purchase actions](https://www.youtube.com/watch?v=GGg61sdEjeI&t=658s) | Keep relevant actions reachable as details scroll. Place quantity controls near basket actions. Display a verified cart total beside checkout when available; distinguish estimates from confirmed totals, and do not label a menu-price sum as the final payable amount. |
| [12:44–13:09 Quick quantities](https://www.youtube.com/watch?v=GGg61sdEjeI&t=764s) | Offer supported quantities of one, two or three as convenient shortcuts beside the existing adjustment controls. Retain the actual API-supported limits and custom adjustment. Do not claim these are popular purchases without evidence. |

## Implementation constraints

Meal discovery keeps Pass, Keep and Undo separate from purchasing. A discovery decision must not add to a cart or place an order. A sticky action area follows the screen’s job: meal selection on discovery, basket review or payment initiation during checkout. Preserve Android back navigation, safe-area insets and readable content when the keyboard or larger font sizes reduce space.

Loading or failed cart updates must not leave an old amount presented as newly confirmed. Totals derive from actual cart state and remain visibly unavailable when a current total cannot be verified. Simulator totals, photos and order flows retain their synthetic labels; real provider results retain their source fidelity.

These refinements complement [mobile layout research](mobile-ui-video-research.md) and [the psychology design decisions](ux-psychology-design-research.md). The goal is understandable choices and lower interaction effort, not unverified claims of increased sales or product trust.
