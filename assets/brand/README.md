# Hungii identity

![Hungii fork-h brand and themed icon treatments](preview.png)

A lowercase **h** with a plastic fork flowing into its upright. Three rounded tines, a tapered neck and a broad, open arch form one continuous silhouette. The wordmark uses original outlined lowercase letters with the rounded, substantial proportions requested from the Hinge reference. No external font file is needed.

The supplied image guided the fork and letter relationship. Its ribbon, hinge seams, lighting and dimensional effects are removed so the result stays clear as a flat app icon and a single-color themed icon.

## Assets

- [Mark](mark.svg): transparent crimson fork-h.
- [Wordmark](wordmark.svg): transparent, outlined `hungii` lettering.
- [Site/repository icon](../icon.svg): crimson on near-black, with a square canvas.
- [Editable geometry](geometry.json): canonical mark and letter paths.
- [Preview board](preview.svg): color, circle/squircle and representative themed treatments. Theme colors here are illustrative; the launcher chooses actual wallpaper colors.

Crimson `#FF526F`; near-black `#09090C`. Keep the fork and body as one fill. Do not add seams, strokes, gradients, drop shadows or separate utensil pieces. Use the wordmark as a whole rather than substituting a system font. Leave the existing SVG clear space around each asset.

## Android

The launcher uses `@mipmap/ic_launcher`: separate full-bleed background and transparent foreground layers on Android 8+, plus a monochrome silhouette on Android 13+. The same silhouette supports launcher masks and wallpaper-derived tinting. Enable **Themed icons** in the phone's wallpaper/style settings on a supporting launcher; otherwise the normal crimson icon appears. This setting belongs to the launcher, not Hungii's in-app theme.

The 108dp foreground positions the complete mark inside the central safe area. The standalone in-app wordmark is a vector with an accessible Hungii description. Both app flavors share this identity. Android 8 is still the minimum supported OS.

[Android adaptive-icon specification](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive) describes masking and monochrome layers.

## Updating

From the repository root:

```sh
python3 scripts/generate-brand.py
python3 scripts/generate-brand.py --check
rsvg-convert assets/brand/preview.svg -o assets/brand/preview.png
```

Edit `geometry.json`, then regenerate. SVGs, Android drawables and broker HTML branding share those paths. CI checks that generated assets match their source. Historical screenshots and published APKs retain their original appearance.
