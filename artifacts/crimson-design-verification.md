# Crimson design verification

4 October 2026 · local Android preview 0.7.6 (version code 14).

## Changes

- Shared search and form fields use filled rounded surfaces, integrated labels and animated focus colors. The label no longer masks a rectangular section of the border.
- Near-black surfaces, crimson accents and rose supporting colors replace green throughout both builds, system bars, launcher artwork, Assistant orb, filters and cards. Buttons and panel surfaces use restrained gradients; tabs, chips, focused fields and pressed buttons transition between shades.
- My day uses a centered responsive calorie ring. Its stroke is inset into the canvas; measured numeric text fits the available width, including nutrition ranges. Supporting facts sit below the ring. Macro rows wrap independently; numeric tiles fit their width.
- Lucky draw finishes turning before a finite 1.8-second mix. The path returns displacement, tilt and blur to the resting state with zero endpoint velocity. Selection unlocks after settling, not after an independent timer. Stable two-line heading space prevents the stage moving when the instruction changes. Card reveal lift/fade also animate. Android 12+ adds subtle render-effect motion blur; older Android keeps the same path without blur. Reduced motion skips the mix.
- Tab labels fit their available width on one line; narrow screens with enlarged text no longer wrap “Assistant” into three lines.

## Evidence

Emulator: Android 15, Hungii_Pixel. Default viewport 1080 × 2400 at density 480 (360 dp wide). Additional layout check: 900 × 2000 (300 dp wide) with font scale 1.3. Device settings restored afterward.

| Before | After |
| --- | --- |
| [Home field border cutout](crimson-before-home.png) | [Integrated Home label](crimson-home.png) |
| [Small side-by-side calorie ring](crimson-before-day.png) | [Centered calorie ring](crimson-day.png) |
| Shared outlined-field style | [Day-edit field labels](crimson-fields.png) |
| Three-line tab label under enlarged text | [300 dp screen with 1.3 font scale](crimson-day-large-text.png) |
| Arbitrary shuffle cutoff | [Settled cards](crimson-cards.png) |

The local `crimson-shuffle-final.mp4` recording captures turn, mix, settle and reveal and is excluded from Git. This is synthetic Simulator data. No live provider order or account data was used.

## Checks

- Both Android variants: unit tests, debug assembly and lint.
- `ShuffleMotionTest`: resting endpoint equality, approach continuity, zero final blur, bounded trajectories and actual card crossing.
- Repository hygiene and local document links.
- Deno type checks and 34 backend/Simulator tests; 7 no-network Assistant regressions.

Screenshots and emulator behavior establish these local layout checks, not every Android screen size or font setting. The earlier web prototype is unchanged. Published releases remain separate checkpoints.
