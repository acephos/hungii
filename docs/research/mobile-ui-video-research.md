# Mobile UI video: Hungii design decisions

Checked 3 October 2026. Primary source: Kole Jain, [Everything you need to know about Mobile App UI’s in 8 minutes (beginner friendly)](https://www.youtube.com/watch?v=Gfsd8NNuD9g), 7:36.

## Access and scope

Read the complete first-party English auto-generated captions using the installed `yt-dlp` client. YouTube’s browser player independently confirmed creator, title, duration and chapters. Direct browser timedtext returned an empty response; the caption download succeeded. The temporary JSON stays outside the repository at `/tmp/hungii-ui-video.en.json3`. Automatic captions can contain recognition errors. No full transcript or creator artwork is redistributed.

## Timestamped findings

These are brief paraphrases of the video, not quotations.

| Video section | Principle |
| --- | --- |
| [0:20 Navigation](https://www.youtube.com/watch?v=Gfsd8NNuD9g&t=20s) | Prefer three or four primary destinations; five is the limit. Keep touch targets generous. |
| [1:32 Scale](https://www.youtube.com/watch?v=Gfsd8NNuD9g&t=92s) | Preserve readable type and spacing instead of squeezing a desktop dashboard onto mobile. |
| [2:13 Content](https://www.youtube.com/watch?v=Gfsd8NNuD9g&t=133s) | Give each section one scrolling direction. Avoid redundant card nesting. |
| [3:30 One screen, one job](https://www.youtube.com/watch?v=Gfsd8NNuD9g&t=210s) | Separate tasks; use bottom sheets for choices that should retain context. |
| [5:11 Gestures](https://www.youtube.com/watch?v=Gfsd8NNuD9g&t=311s) | Teach swipes rather than assuming users discover them. |
| [6:02 Dynamism](https://www.youtube.com/watch?v=Gfsd8NNuD9g&t=362s) | Replace persistent navigation with relevant actions during focused tasks. |
| [6:27 Empty states](https://www.youtube.com/watch?v=Gfsd8NNuD9g&t=387s) | Distinguish first use from unsuccessful search; explain what happened and offer a way forward. |

## Hungii application

The following are our implementation choices, inferred from those principles and the current native Compose interface:

- Reduce the dock to Home, Meals, Assistant and My day. Keep Saved discoverable from Meals; retain its existing data and permission controls.
- Use 48 dp minimum action targets and larger supporting text. Allow important labels to wrap at larger font scales.
- Keep Home a compact overview with one clear meal-discovery action. Present filters and goal edits in sheets; keep payment and meal selection focused.
- Retain Pass, Keep and Undo buttons alongside swiping. Label the gestures near the meal card.
- Provide distinct loading, disconnected, no-results and exhausted-batch messages with suitable retry, filter-reset or shortlist actions. A service failure must not look like an empty catalogue.
- Make the empty Saved screen explain how to save a meal and offer discovery. Keep a calm first-use Assistant prompt and readable context when messages appear.

The video’s iOS-style transitions are visual examples, not a requirement to override Android back navigation. Its sponsor segment does not require a new dependency or paid service.
