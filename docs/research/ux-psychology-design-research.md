# UX psychology: useful choices without pressure

Checked 3 October 2026 against the user-supplied transcript of [The UX Psychology Behind Apps People Can’t Stop Using](https://www.youtube.com/watch?v=2TlIg3VokY8). This note paraphrases that supplied material; the video was not reopened. The transcript skips from 3:11 to 3:49, so no recommendation is inferred from the missing section.

The transcript’s study descriptions, behavioral percentages, conversion figures and universal claims were not independently verified. They are not product evidence, expected results or established facts in Hungii’s documentation. These design decisions aim to reduce effort and improve understanding; any effect on usability needs testing with Hungii users.

## Timestamped ideas and application

| Supplied section | Constructive Hungii decision |
| --- | --- |
| [0:20–1:43 Defaults](https://www.youtube.com/watch?v=2TlIg3VokY8&t=20s) | Offer editable starting values for the food allowance, remaining meals and craving. Label them as starting values, not medically appropriate goals or the choices of most users. Keep actual current values when reopening forms. |
| [1:45–3:11 Progress](https://www.youtube.com/watch?v=2TlIg3VokY8&t=105s) | Show the shortlist’s real number of selected meals out of three, then the remaining step. Empty means zero; only completed user actions advance it. Counts of available meals appear only after a successful result. |
| [3:49–5:36 Value before sign-up](https://www.youtube.com/watch?v=2TlIg3VokY8&t=229s) | Let people inspect their daily allowance, edit a local plan and understand remaining nutrition ranges before Hungii sign-in. Present sign-in for optional profile sync; explain separately when provider access is needed to discover live meals. |
| [5:38–7:01 Personalization](https://www.youtube.com/watch?v=2TlIg3VokY8&t=338s) | Let users choose a craving and adjust their plan through the existing controls. Persist personal choices only under existing local-storage and cloud-sync consent. Avoid requiring identity, unnecessary setup or a cloud Assistant to use manual planning. |
| [8:20–9:34 Loss framing](https://www.youtube.com/watch?v=2TlIg3VokY8&t=500s) | Explain real consequences neutrally: changing a goal updates a plan; an order does not establish consumption. Keep clear decline and back controls. Do not turn this section’s threatening example into Hungii copy. |
| [9:36–11:01 Price context](https://www.youtube.com/watch?v=2TlIg3VokY8&t=576s) | Keep the rupee amount prominent and compare it with the user’s remaining allowance. Distinguish menu price from estimated basket cost and verified cart total. Calculate relative cost only with known cost and a positive remaining allowance; explain overspend or unknown totals instead of misleading percentages. |

## Implementation boundaries

Use progress to explain where the user is, not to fabricate momentum. Do not preselect saving, cloud sync, microphone access or Assistant consent as a “smart default.” Optional personalization must remain editable and removable through existing controls.

Do not introduce invented stock shortages, countdowns, expiring offers, social proof, savings or completion counts. Do not threaten lost meals or data to encourage sign-in, use guilt-laden opt-outs, or disguise account creation as a generic continuation. Price comparisons must not hide fees or imply that an unaffordable meal is inexpensive.

These choices complement [the mobile UI research](mobile-ui-video-research.md): readable scale, four main destinations, focused flows, contextual sheets and explicit recovery states stay the foundation.
