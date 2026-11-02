# PYTE · Violet identity

Native Compose / Material 3. The visual direction is near-black with violet-tinted elevation,
soft lavender text accents, and saturated purple gradients for primary Play/import actions.
There is no web UI or runtime CSS layer.

## Shared tokens

`app/src/main/java/dev/pyte/ui/theme/BrandTokens.kt` owns the semantic palette:

| Role | Color |
| --- | --- |
| Canvas | `#09070F` |
| Surface | `#14101E` |
| Elevated surface | `#21182F` |
| Interactive text/accent | `#C4A0FF` |
| Main text | `#F4EFFB` |
| Muted text | `#B7ACC8` |
| Primary gradient | `#7423D5` → `#A02ADC` |

Lavender solid controls use a dark foreground; gradient buttons use white. Unit tests check
normal text contrast against all surface tokens and both gradient endpoints. Do not use
the darker decorative purples as small text colors.

## Original logo

The supplied source is retained unmodified at:

`app/src/img/e36b1910-1e8f-45fd-bd5e-ff088e2a22c4.png`

Rebuild Android assets from the repository root with JDK 17:

```bash
java tools/PrepareBrandAssets.java
```

The generator crops the original mark/wordmark, removes the near-black backdrop from derived
copies, and scales with bicubic interpolation. It does not redraw the logo. The launcher
places the headphones/play mark inside the adaptive-icon safe area. Generated PNGs live in
`app/src/main/res/drawable-nodpi/`; the full original is not packaged as an Android resource.

## Components and layout

- `ScreenHeading`: original wordmark, on-device badge, clear title/subtitle hierarchy.
- `BrandButton`: accessible native button semantics with a shared violet gradient.
- Welcome: original logo, simple introduction, one prominent import action.
- Library: bordered rounded cards and a local Recently played rail.
- Player: framed artwork, violet ambient gradient, high-contrast controls, queue access.
- Mini-player: rounded floating surface with a hairline progress indicator.
- Queue: explicit move buttons rather than drag-only interactions.

Playlist headers and full player content scroll on short screens. System bar icons stay light
because the app deliberately uses a dark-only theme. A captured Android welcome screen is in
`docs/screenshots/welcome.png`; it is not a browser mockup.
