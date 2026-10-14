# Scene assets — what you must supply

Every file in this folder is currently a **generated PLACEHOLDER** (each image is stamped
"PLACEHOLDER · <filename>"). Regenerate them with `npm run placeholders`.
Replace each file with final artwork **using the same file name**, or point
`src/config/site.config.ts → scene / services` at new paths.

All objects are animated as separate layers, so they must be **cut out on a transparent
background** (WebP with alpha). WebP alpha is typically 3–5× smaller than PNG; PNG also
works if you change the extension in the config.

| File | Size (px) | Format | Content |
|---|---|---|---|
| `backdrop.webp` | 2560×1440 | WebP, opaque, q≈70, ≤ 350 KB | Dark barbershop interior, empty, subject area on the right ⅔, warm practical lights. Slightly out of focus works best. |
| `chair.webp` | 1200×1400 (portrait) | WebP + alpha, ≤ 250 KB | Barber chair, ¾ view, **cut out**, with a warm gold rim light on the right edge. Centered, ~5% padding. |
| `clipper.webp` | 800×800 | WebP + alpha, ≤ 80 KB | Hair clipper, cut out, centered |
| `comb.webp` | 800×800 | WebP + alpha, ≤ 80 KB | Comb, cut out, centered |
| `scissors.webp` | 800×800 | WebP + alpha, ≤ 80 KB | Small scissors, cut out, centered |
| `bottle.webp` | 800×800 | WebP + alpha, ≤ 80 KB | Product bottle (oil / pomade), cut out, centered |
| `scissors-large.webp` | 1600×1600 | WebP + alpha, ≤ 250 KB | Hero scissors for "The Craft", high detail (it is scaled up to ~110%) |
| `service-haircut.webp` | 800×600 (4:3) | WebP, opaque, ≤ 90 KB | Photo for the Haircut card |
| `service-beard.webp` | 800×600 (4:3) | WebP, opaque, ≤ 90 KB | Photo for the Beard Sculpting card |
| `service-styling.webp` | 800×600 (4:3) | WebP, opaque, ≤ 90 KB | Photo for the Premium Styling card |

Tips
- Light everything from the **upper right** with a warm (≈3200K) rim light so the layers match.
- Keep the products the same visual weight; per-object size/position/rotation/depth is in
  `site.config.ts → scene.products`.
- Converting: `npx sharp-cli -i in.png -o chair.webp --format webp --quality 82` or Squoosh.

## Optional: image sequence (canvas scrubbing)

For the "video scrubbing" look behind the layers, render a short camera move (5–8 s)
and extract frames:

| Path | Count | Size | Format |
|---|---|---|---|
| `/public/frames/frame_0001.webp` … `frame_0150.webp` | 120–200 | 1920×1080 (1600×900 for lighter) | WebP q≈60, ≈ 40–80 KB each |

```bash
# macOS / Linux / Git Bash
./scripts/extract-frames.sh input.mp4 150
# Windows PowerShell
./scripts/extract-frames.ps1 -Video input.mp4 -Count 150
```

Then in `site.config.ts` set `scene.frames.enabled = true` and `count` to the number of
frames. The first `eagerCount` (20) load behind the loading screen; the rest stream in
lazily. Total sequence budget: aim for < 8 MB.

When the sequence already contains the shop and chair, you can hide `backdrop` or
the hero chair by swapping them for transparent 1×1 images.
