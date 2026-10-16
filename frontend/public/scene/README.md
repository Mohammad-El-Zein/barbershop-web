# Scene assets

The images in this folder are **interim assets**: stills cut from an AI-generated collage
(`assets-raw/collage.jpg`, 1408×768) and a chair turntable from a 640×360 GIF
(`assets-raw/chair-turn.gif`). They work for demos, but both sources are low-resolution:
everything was 4× upscaled with Real-ESRGAN, so fine detail is partly synthesized. Replace
them with real photography / renders before a client launch (sizes below).

> ⚠️ **Backdrop: replace later.** `backdrop.webp` still contains barber chairs (blurred and
> darkened, see below). A **chair-free, high-resolution interior** (≥ 2560×1440, ideally
> 3840×2160) should replace it.

## File map

| File | Source | Size | Notes |
|---|---|---|---|
| `chair-turntable/` | `assets-raw/chair-turn.gif` (30 frames) | 88 frames × 2 sets | scroll-driven rotation, see **Chair turntable** below |
| `chair.webp` | frame 0 of the turntable | 900×960, alpha | poster / fallback / reduced-motion; written by `turntable.py --poster` |
| `backdrop.webp` | collage interior `(0,0)-(797,542)` | 2560×1440 | 16:9 crop, treated (see below) |
| `clipper.webp` | collage clipper tile `(1040,36)-(1120,240)` | 278×704, alpha | machine only: guards excluded by the crop, cord/boot/loop cut below the body |
| `comb.webp` | collage top-right comb `(1220,27)-(1396,248)` | 650×839, alpha | blacks lifted slightly so it reads on the dark scene |
| `scissors.webp` | collage gold scissors `(1056,302)-(1164,536)` | 372×877, alpha | |
| `scissors-large.webp` | same scissors, centred on a square canvas | 901×901, alpha | used by "The Craft" |
| `bottle.webp` | collage amber bottle `(1260,310)-(1352,526)` | 320×801, alpha | |
| `service-haircut.webp` | collage bottom row #1 (scissors) `(0,549)-(348,768)` | 1280×800 | |
| `service-beard.webp` | collage bottom row #2 (razor) `(353,549)-(703,768)` | 1280×800 | |
| `service-styling.webp` | collage bottom row #3 (comb/pomade) `(708,549)-(1059,768)` | 1280×800 | |
| — | collage bottom row #4 (shaving brush) | — | not used yet |

Originals of every crop: `assets-raw/crops/` (+ `crop-boxes.json`).
RGBA cut-out masters before grading/WebP: `assets-raw/cutouts/` (the earlier gold collage chair is
kept there as `chair.png` / `chair-collage-gold.webp`; the scene now uses the turntable chair so the
poster and the rotating sequence are the same chair).

## Chair turntable (scroll rotation)

The chair rotates while you scroll: a transparent WebP image sequence drawn on a `<canvas>`
(`src/components/scene/ChairSequence.tsx`). Which part of the scroll plays which part of the
rotation is configured in `src/config/site.config.ts → scene.turntable.segments`
(positions 0..1 of the sequence, so the config survives a new frame count):

- chapter 1 → 2: frames 0 → last (the chair turns away while the products fly in)
- chapter 5 → 6: frames last → 0 (it turns back and ends facing you inside the gold ring)

Runtime: frame = scroll position mapped through the segments, eased with ScrollTrigger's scrub
plus a per-frame lerp (no stepping); the canvas is sized to CSS size × min(DPR, 2); the smaller
set is used when it covers the needed pixels (phones); the first 12 frames load behind the
loading screen in coarse-to-fine order, the rest stream in when the browser is idle; frames that
fail to load are never retried and the poster stays visible.

**Current sequence (from the 640×360 GIF):** 88 frames (30 source + RIFE in-betweens),
master 1273×1358, sets `960/` (900×960, **4.14 MB**) and `540/` (506×540, **2.13 MB**).
Measured on screen: 610×650 CSS px at 1440×900, 743×793 at 1920×1080 → the 960 set covers both
at DPR 1 (1.48× / 1.21×); at DPR 2 (Retina) the canvas needs 1300 px and the frames are upscaled
(0.74×). The real source detail is ~337 px chair height, so on screen it is a ~2× upscale.

### Regenerating / replacing it (no code changes)

```bash
# venv with scripts/assets/requirements.txt; Real-ESRGAN + RIFE ncnn-vulkan binaries
python scripts/assets/turntable.py assets-raw/chair-turn.mp4 \
  --out public/scene/chair-turntable --poster public/scene/chair.webp \
  --esrgan path/to/realesrgan-ncnn-vulkan.exe --rife path/to/rife-ncnn-vulkan.exe
```

Input can be a GIF / APNG / animated WebP or any video ffmpeg reads. Steps:
1. extract all frames losslessly (PNG)
2. measure each frame on a rembg mask: floor line, base-disc centre and width (the round base =
   the rotation axis; footrest feet are ignored)
3. stabilise: fit those over time with a line and normalise **scale by disc width** (rotation-invariant)
   and **position by disc centre + floor line**. Chair *height* is deliberately not used: it changes
   with perspective during a turn. (GIF: scale correction ±0.8 %, shift ±0.2 px.)
4. crop to the union box, Real-ESRGAN ×4
5. interpolate to `--target-frames` (default 88; rounded so source frames stay exact keyframes) with
   **RIFE v4.6**; fallbacks `--interp minterpolate` (ffmpeg) or `--interp none` → the manifest then sets
   `crossfade: true` and the canvas blends the two nearest frames instead
6. matte every frame (rembg BiRefNet) with the same edge clean-up as the stills (`cutout.py`),
   cutting everything below the floor line (floor shadow); no grey rim, checked at 200 % on dark
7. trim to one common box, grade like the stills, export WebP sets (`--sizes 960,540`, q 80) +
   `manifest.json` (+ poster = frame 0)

`--from-work` re-runs only step 7 from an earlier run's work dir (e.g. other `--sizes`/`--quality`)
in about a minute instead of ~35 min. The site imports `manifest.json` at build time, so rebuild
after regenerating.

RIFE quality check (this GIF): no ghosting or warping on chrome and armrests; the footplate tread
texture is slightly softer in the in-between frames.

**For a better result supply:** a 4–6 s turntable video, ≥ 1920 px tall (chair ≥ 1400 px), 30–60 fps,
locked-off camera, plain light background, same warm key light from the upper right, ~140° turn
from front ¾ to rear ¾, full chair always in frame with some floor margin. With ≥ 88 source frames
no interpolation is needed.

## How the stills were made (reproducible)

1. **Crop** the collage with the boxes above (`assets-raw/crops/`). Tile gutters were detected from pixel data.
2. **Upscale ×4** with Real-ESRGAN (`realesrgan-ncnn-vulkan -n realesrgan-x4plus`, GPU). Compared
   against Lanczos: clearly sharper on leather, metal and hair.
3. **Background removal** with rembg using **BiRefNet-general-lite** (ISNet left grey between the comb teeth).
4. **Edge clean-up** — `scripts/assets/matte.py` (shared logic in `cutout.py`): colour-key of
   background-like soft pixels, choke + 1 px erosion (removes the light rim / halo), un-mixing of the
   grey background from edge pixels (`F = (I − (1−a)·B) / a`), removal of loose specks, trim to the
   silhouette. No shadows are kept; the scene draws its own contact shadows. Checked at 200 % on `#0a0a0a`.
5. **Backdrop treatment** — `scripts/assets/backdrop.py` (below).
6. **Grade + WebP** — `scripts/assets/export-webp.mjs` (sharp, quality 85, alpha kept): slightly darker,
   −10 % saturation, warm (less blue) so studio shots sit in the warm, low-key scene.

Python deps: `scripts/assets/requirements.txt` (rembg, OpenCV, imageio-ffmpeg). Binaries: the portable
`realesrgan-ncnn-vulkan` (github.com/xinntao/Real-ESRGAN) and `rife-ncnn-vulkan`
(github.com/nihui/rife-ncnn-vulkan) releases; both need a Vulkan GPU.

`npm run placeholders` only creates files that are **missing**; it never overwrites these assets
unless you pass `--force`.

## Backdrop treatment (interim)

The collage interior has a large chair on the left and two far chairs in the middle, which
compete with the scene's foreground chair. Applied in `backdrop.py`:

- cropped to 16:9 (source rows 30–478), keeping the lit shelves and the marble floor
- **left ~55 %** (the big chair): strong blur + darkened to ~32 % — it sits behind the text column,
  which the page darkens further, and reads as a dark out-of-focus shape
- **far chairs** (middle): moderate blur + darkened to ~48 % — reads as background depth behind
  the foreground chair
- **right side** (lit shelves): untouched

## Resolution check (stills, measured in the browser at the largest on-screen size)

"Ratio" = file pixels ÷ CSS pixels shown (≥ 1.0 is enough at DPR 1). "Real detail" = the
collage pixels the image came from ÷ CSS pixels shown — below 1.0 the extra detail is upscaler-made.

| Asset | 1920×1080 ratio | 2560×1440 ratio | Real detail at 1920 | Verdict |
|---|---|---|---|---|
| backdrop | 1.10 | **0.83** | 0.34 | too small for 1440p; soft on every screen — replace |
| chair turntable | 1.15 | **0.88** | 0.40 | too small for 1440p; see Chair turntable above |
| clipper | 1.18 | **0.89** | 0.34 | too small for 1440p |
| scissors-large | 1.06 | **0.79** | 0.13 | too small for 1440p; weakest asset (shown very large) |
| scissors | 1.73 | 1.30 | 0.46 | ok in pixels, soft in detail |
| comb | 2.37 | 1.78 | 0.62 | ok |
| bottle | 2.51 | 1.88 | 0.68 | ok |
| service photos | 4.30 | 4.30 | 1.17 | ok (cards are small) |

On high-DPI screens (DPR 2, e.g. MacBooks) every asset except the service photos is below
native sharpness. **Targets for final assets:** backdrop 3840×2160; chair sequence ≥ 1400 px chair
height; clipper/scissors/comb/bottle ≥ 1200 px on the long side; scissors-large ≥ 2400 px; service
photos 1280×800 (as now).

## Replacing an asset

Keep the file name (or change the path in `src/config/site.config.ts`), and update its
`width`/`height` in the config (used for the aspect ratio; the chair takes its size from the
turntable manifest). Cut-outs must be **trimmed tight** to the silhouette (no empty margin), WebP
with alpha, no text/logos/watermarks, lit from the upper right with a warm key. Product layout
(position, size, rotation, depth, orbit slot) is in `site.config.ts → scene.products`. To see which
file is which in the page, set `debug.showPlaceholderLabels = true` (development only).

## Optional: full-frame image sequence (canvas scrubbing)

For a "video scrubbing" look behind the layers, render a short camera move (5–8 s) and extract
frames to `/public/frames/frame_0001.webp …` (120–200 frames, 1920×1080, WebP q≈60):

```bash
./scripts/extract-frames.sh input.mp4 150          # macOS / Linux / Git Bash
./scripts/extract-frames.ps1 -Video input.mp4 -Count 150   # Windows PowerShell
```

Then set `scene.frames.enabled = true` and `count` in `site.config.ts`.
