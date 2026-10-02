# Hungii assistant orb: modern visual references and interaction

Checked 2 October 2026 using first-party visuals. The founder wants the assistant in its own tab, able to operate the rest of Hungii through natural-language requests. The recommendations below are Hungii design decisions, not claims that the reference products use the same rendering code or behavior.

## Primary visual references

**Apple's current Siri hero** shows a dark, nearly transparent glass sphere with a thin chromatic rim and bright horizontal white/cyan light suspended inside. At about one second in the official animation, the sphere has a clear circular silhouette, a shaded interior, warm/cool edge reflections and a soft outer halo. The end frame becomes a luminous curved ribbon. These cues establish depth without a solid painted blob. [Official product page](https://www.apple.com/apple-intelligence/), [official hero video](https://www.apple.com/105/media/us/apple-intelligence/2026/841ecfb6-701e-49f5-9586-6d9d3e48d67c/anim/highlights-siri/large.mp4), [official end frame](https://www.apple.com/v/apple-intelligence/i/images/overview/highlights/highlights_siri_endframe__bd109g1f3l4y_large.jpg)

**ChatGPT's current public Voice hero** shows a soft lavender/white swirling circular visual. Its first-party page describes tapping the orb to reveal the transcript and tapping again to simplify the view. The transferable idea is a calm central presence with an accessible transcript, rather than decorative particles competing with the conversation. [Official Voice page and embedded demo](https://chatgpt.com/features/voice/)

**Google's Neural Expressive announcement** describes fluid animations, vibrant colors, typography and haptics, with typing and Live conversation integrated. The first-party hero contains a luminous blue pill within a dark control dock with distinct microphone and end buttons. This is a useful compact treatment when the conversation or action result needs the space. [Official announcement](https://blog.google/innovation-and-ai/products/gemini-app/next-evolution-gemini-app/), [official hero](https://storage.googleapis.com/gweb-uniblog-publish-prod/images/Geminiapp_Bento_hero.width-1200.format-webp.webp)

These references were inspected visually; they do not disclose private shader implementations, exact motion curves or how every signed-in version behaves. Reference artwork stays outside the APK. Local inspection copies are in `/tmp/hungii-orb-reference/`.

## Chosen Hungii treatment

Use a **luminous glass sphere** on the charcoal canvas: 220–260 dp diameter on the idle Assistant tab, shrinking to 100–140 dp when conversation/results occupy the screen. Keep its silhouette almost perfectly circular. Layer cyan and lavender internal ribbons over a dark center, with a restrained lime edge glint connecting it to Hungii's brand. Suggested palette: ice `#DFFAFF`, cyan `#8EEAF3`, lavender `#B4A1FF`, lime `#CAFF48`, glass shadow `#111C21`.

The orb should have six visual layers, each cheap enough for native drawing:

1. Broad low-opacity radial halo, softly fading into the app background.
2. Circular dark radial gradient with a lighter upper-left shoulder and darker lower-right depth.
3. Two or three smooth clipped translucent ribbon paths inside the sphere. Mix broad dim bands with one slender luminous band.
4. Thin sweep-gradient rim, with discontinuous bright arcs and mostly quiet edges.
5. Small offset specular highlight and one fine reflective crescent.
6. Gentle contact shadow beneath the orb to separate it from its backdrop.

The strong contrast between bright thin light and dark interior produces the glass effect. Avoid a fully filled neon-green disk, a thick uniformly bright ring or large irregular outer waves. A small amount of ribbon motion and restrained halo change gives a richer impression than dozens of random particles.

## Native rendering plan

Use Compose `Canvas` with `DrawScope` paths/circles/arcs. Built-in radial, linear and sweep gradient `Brush` types supply the interior, glow and rim; `clipPath` keeps the internal ribbon bands inside the sphere. All coordinates should be fractions of canvas size or converted from dp. These drawing and gradient capabilities are documented, so the effect need not depend on downloaded raster assets, a paid animation service or an Android-13-only custom shader. [Compose drawing guide](https://developer.android.com/develop/ui/compose/graphics/draw/overview), [gradient brushes](https://developer.android.com/develop/ui/compose/graphics/draw/brush)

An internal ribbon can follow a smooth path across the sphere at roughly 45–60% of its height, with opposing cubic bends and thickness varying across its length. Animate its phase slowly while preserving the circular boundary. Use three low-opacity passes of increasing stroke width to suggest bloom, followed by one crisp narrow pass. Clamp brightness and amplitude; never flash the whole screen. Keep phase updates in the drawing layer and cache geometry/brushes where their inputs are stable. Use a static version when animations are disabled. [Compose animation guide](https://developer.android.com/develop/ui/compose/animation/quick-guide)

## Interaction states

Durations and amplitudes here are proposed values, not measured reference-product timings.

| State | Visual | Visible status and controls |
| --- | --- | --- |
| Idle | Very slow 6–10 s internal drift; scale within 1–2% | `What can I help with?`; mic + text input |
| Listening | Halo brightens; ribbon and small scale changes follow smoothed real mic level, capped near 8% | `Listening…`; stop mic; transcript |
| Interpreting | More focused central light, 1.5–2 s restrained rotation/drift | `Working on that…`; cancel if work is asynchronous |
| Responding | Gentle ribbon pulses only while response/TTS is active | Readable response; optional stop speech |
| Awaiting confirmation | Motion settles; one quiet lime rim highlight | Exact proposed changes and explicit buttons |
| Error/unavailable | Calm static orb with small coral indicator | Specific explanation, retry and typed fallback |

The current free local implementation may use a deterministic intent parser. Calling that state `Interpreting` does not imply a remote LLM. If no speech response is playing, do not animate as though the assistant is talking. Transition promptly to the result rather than forcing artificial thinking delays.

## Standalone Assistant tab

The dedicated tab should contain a short title, the orb, one readable status line, concise conversation history, context/action result cards and a sticky text/mic composer above the bottom dock. Offer useful prompts such as `What's left today?`, `Find spicy protein under ₹250`, `Show my saved meals`, `Take me to my cart` and `I have two meals left`. Keep the other tabs visible; navigating through the assistant should produce the same state changes as their normal controls.

Commands should call the app's shared action methods, not mutate a separate assistant-only copy of state. The reply should explain what happened: updated allowance, applied filters, opened cart, chosen coupon, or saved preference. Ambiguous entries such as `I had a shake` need quantity/nutrition clarification or a visibly labeled estimate; the orb must not invent an accurate macro total. Reversible changes should offer Undo. A compound request should report which actions were understood, avoiding a blanket success message when only one matched.

For checkout, show the final cart and amount before a simulated payment confirmation. A request like `Pay for this` should open that confirmation; it must not silently submit a payment or live order. The mock receipt remains explicit about simulated payment. Nutrition logging is separate from ordering: an order event does not establish that the meal was eaten.

## Voice privacy and accessibility

Opening the Assistant tab must not start recording. Begin only after a microphone tap and permission; expose a stop control and listening label. Stop recognition when leaving the tab or backgrounding the app. Ordinary Android `SpeechRecognizer` implementations may stream audio to remote servers and are not intended for continuous recognition. The on-device recognizer is available from API 31 only when the device reports support; retain Hungii's existing on-device-only check and a useful typed fallback. Destroy unused recognizers. [Android speech reference](https://developer.android.com/reference/android/speech/SpeechRecognizer)

Do not promise that every phone supports offline voice. In local-only mode, the local parser, transcript and app actions should remain local; any future model/service needs its own explicit data-flow decision. The appearance of an orb is independent of whether there is an AI API behind it.

Use normal composable text for status/transcript rather than drawing essential text into Canvas. Make mic/stop actions at least 48 dp, give them clear accessibility labels and do not encode listening/errors by colour alone. Decorative internal layers should not create separate TalkBack focus nodes. When device animations are disabled, use a static glass orb and text state; keep all interaction features available. [Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)

## Review checklist

Inspect idle/listening/interpreting/confirmation/error states on a narrow Android display, with keyboard open, large font scale and disabled animations. Confirm the circle reads as glass at small size; text and payment totals remain higher priority than decoration. Verify every example command through the same domain actions as the manual UI, and confirm that tab changes stop the microphone. Verify local-only operation with networking disabled and no model keys configured.
