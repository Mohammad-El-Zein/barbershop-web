# Performance

How the website's speed is measured, what was changed, and what is still open. Every claim below comes from
the same measurement method.

## Method (use it for every before/after claim)

```bash
# A = previous build, B = new build — both as production containers on the same machine
docker run -d --name perf-a -p 3101:3000 --add-host host.docker.internal:host-gateway \
  -e API_URL_INTERNAL=http://host.docker.internal:8080 barbershop-frontend:<previous>
docker run -d --name perf-b -p 3102:3000 --add-host host.docker.internal:host-gateway \
  -e API_URL_INTERNAL=http://host.docker.internal:8080 barbershop-frontend:<new>
cd frontend
npm run perf:ab -- --a http://localhost:3101 --b http://localhost:3102 --pages lp,landing,booking --runs 5 --form mobile
```

`scripts/perf/lighthouse-ab.mjs` works as follows:

- **Lighthouse 13.5.0** (pinned devDependency, the engine Lighthouse CI uses).
- **Simulated throttling with the values written out:**
  - mobile: Moto G Power emulation (412 × 823, DPR 1.75), RTT 150 ms, 1.6 Mbit/s, 4× CPU slowdown;
  - desktop: 40 ms, 10 Mbit/s, 1×.
- **5 runs per variant, alternating** A B / B A / …, so the machine's drift hits both equally.
- **Output:** the median of each variant plus every single score.

On the same machine, the spread between runs of the *same* build is about ±5 points on mobile. A difference only
counts when the B runs are consistently above the A runs. Absolute scores depend on the machine; this one is
slower than the one used for the Phase 4 numbers in step 3, where the landing page scored 80. Compare A and B
from the same session, never numbers from different days.

## Results (A = build before this round, B = this round)

**Mobile** (median of 5; single runs in brackets):

| Page | Score A → B | LCP A → B | TBT A → B | CLS | FCP A → B |
|---|---|---|---|---|---|
| Landing `/de` | **57 → 65** (54/58/54/61/57 → 62/65/66/68/64) | 4.88 → **4.35 s** | 991 → **718 ms** | 0 → 0 | 1.11 → 1.10 s |
| Ad page `/de/lp/herbst-fade` | 79 → 74 (80/84/75/79/69 → 67/69/78/74/77) | 3.82 → **3.45 s** | 403 → 677 ms | 0 → 0 | 1.07 → 1.15 s |
| Booking `/de/booking` | 71 → 70 | 3.91 → 3.98 s | 656 → 712 ms | 0 → 0 | 1.07 → 1.13 s |

**Desktop** (median of 3): ad page 99 → 98, landing 87 → 89 (CLS 0.022 → 0); no regression.

How to read the mobile results:

- **Landing:** the only robust gain. Every B run is above every A run.
- **Ad page:** LCP was a bit lower in four of five rounds (−0.1 … −0.4 s; round 4: +0.03 s; build B changed between
  rounds). The score moves within the noise, driven by the TBT spread.
- **Booking:** unchanged.

### Targets

| Target | Result |
|---|---|
| Ad page mobile LCP < 2.5 s | **Not met** (3.45 s) |
| Landing mobile score > 75 | **Not met** (65) |

The reason is measured, see below.

## What limits the mobile numbers (floor experiments)

The same pages were measured with the app's JavaScript chunks blocked (Lighthouse
`--blocked-url-patterns=*/_next/static/chunks/*.js`):

| Page | With JS | Without app JS |
|---|---|---|
| Ad page | LCP 3.45–3.8 s, score 74–80 | **LCP 2.1 s, score 99** |
| Landing | LCP 4.35 s, score 65 | **LCP 2.3–2.4 s, score 96–97** |

- **About 1.5–2 s of LCP is JavaScript.** In the observed trace the framework chunks run before the first paint.
  Lighthouse's simulation then counts their transfer over 1.6 Mbit/s and their execution at 4× CPU into the LCP.
- **What the JS is (ad page, 216 KB compressed):**
  - react-dom (≈ 73 KB) and the Next App Router runtime (≈ 46 KB): framework, shipped with every App Router page;
  - next-intl/formatjs with the session provider (≈ 16 KB);
  - the consent manager (≈ 13 KB);
  - the rest is small.

  The site's own code is the minor part.
- **Images, fonts and CSS are not the bottleneck:** the hero image is 17 KB (AVIF), the fonts 61 KB, the CSS
  14 KB.

### Options to reach the targets (decisions, not done)

1. **Ad page without the React runtime (expected LCP ≈ 2.1 s).** Serve `/lp/<campaign>` as static HTML from a
   route handler, with its own small CSS and no client JS.
   - Behaviour changes:
     - the CTA links to `/booking?services=…&utm_…` instead of opening the booking dialog;
     - the consent banner and UTM capture happen on the booking page;
     - no theme toggle on the ad page.
   - The e2e test for ad attribution moves to the booking page.
2. **Landing on phones without the pinned GSAP scene (expected score ≈ 80–90).** Phones get the existing
   static variant (`StaticScene`, today used for `prefers-reduced-motion`), and the scene code (GSAP, Lenis) is
   loaded only on larger screens.
   - Behaviour change: no scroll animation on phones.
   - A smaller step without behaviour change: load GSAP / Lenis dynamically once the preloader is done. This
     needs a refactor of the scene's `useGSAP` context; the expected gain is ≈ 5 points.
   - **Decision (2026-10):** phones keep the scroll animation, as a light variant (README → scroll scene
     architecture); the static scene is the automatic fallback. The smaller step is done: GSAP and the
     timeline load once the scene is ready (phones: after the first screen). Landing, mobile, median of 5:
     score 65, LCP 4.7 s, TBT 650 ms, CLS 0. It includes ≈ 750 KB of scene images and chair frames that are
     now loaded right after the first screen, so the first swipe never waits for them.

## Landing on phones: tempo, smoothing, fallbacks

The knobs are three constants at the top of the scene settings in
`frontend/src/config/site.config.ts` (a new frontend build is needed after a change).

### `MOBILE_PIN_SCREENS` — how fast the animation runs (default 4)

The animated part of the landing page on phones and touch devices is this many screen heights of
scrolling. The whole story (chair turns, products, service cards, scissors, orbit, gold ring with
the book button) plays over that distance, so the speed is inversely proportional to the number.
"Speed 100" is the desktop scene's 6 screens:

| `MOBILE_PIN_SCREENS` | Speed | Feel (feedback) |
|---|---|---|
| 6 | 100 | too slow on phones (the first version) |
| 5 | 120 | |
| **4** | **150** | **chosen** |
| 3 | 200 | |
| 2.2 | ≈ 270 | far too fast (October 2026, first light variant) |

Speed = 600 / (`MOBILE_PIN_SCREENS` × 100). The page below the scene (footer) follows right after.

Chapters are fractions of the distance (`chapters[].range`), so they all scale along. At 4 screens the
chapter text stands fully visible for ≈ 0.26 screens each (≈ 220 px at 390 × 844); the first screen
(chair) and the last (gold ring, book button) a bit longer. The ranges were evened out for this:
scissors and orbit 0.18 each, the last chapter 0.14 (before, the scissors' text stood only ≈ 150 px).

### Chapter lengths: `CHAPTER_WEIGHTS` and `MOBILE_SCISSORS`

`CHAPTER_WEIGHTS` (same file) sets how much of the scroll each chapter gets; the ranges are
computed from it, and the chair's two turns stay tied to the chapter boundaries. It applies to
desktop and phones alike. With the defaults (14 / 18 / 18 / 18 / 18 / 14) and 4 screens:

| Chapter | Weight | Screens on phones | ≈ seconds at 1 screen / s |
|---|---|---|---|
| hero (chair) | 14 | 0.56 | 0.6 |
| signature (products) | 18 | 0.72 | 0.7 |
| services (cards) | 18 | 0.72 | 0.7 |
| craft (scissors, "about us") | 18 | 0.72 | 0.7 |
| premium (orbit) | 18 | 0.72 | 0.7 |
| cta (gold ring) | 14 | 0.56 | 0.6 |

Screens of a chapter = weight / sum of weights × `MOBILE_PIN_SCREENS`. To make one chapter longer,
raise its weight (it takes the share from the others; the total distance stays
`MOBILE_PIN_SCREENS`). The text of each middle chapter stands fully readable for ≈ 0.4 screens.

`MOBILE_SCISSORS` (phones and touch devices only): the scissors of the "about us" chapter appear at
`startScale` (default 0.7; 1 = 80 % of the screen width) and reach their full size (1.12) within
`growShare` of the chapter (default 0.3), strongly eased; afterwards they only turn a little.
Before (and still on desktop), they started at 0.35 and grew across the whole chapter, finishing
only as the text left — it felt like scrolling on just to wait for them. Measured at 4 screens
(Chromium, 360–430 px wide and 390 × 640, all the same): growing to 95 % of full size took
0.52 screens before (of the chapter's 0.72) and takes ≈ 0.08 screens now; the text stands fully
readable for ≈ 0.4 screens, before and after.

### `MOBILE_SCRUB_SECONDS` — how long the animation trails the finger (default 0.4)

ScrollTrigger scrub on phones and touch devices: 0 = locked to the finger (felt hectic at
2.2 screens), 1 = the desktop's value (felt sluggish). 0.3–0.5 is the sensible range. Desktops keep
`scene.scrub` (1 s, with Lenis).

### `SCENE_AUTO_FALLBACK` — automatic switches to the static page (default: all off)

| Switch | Turns the static page on when … |
|---|---|
| `weakDevice` | the device has 2 cores and ≤ 2 GB memory, or the data saver is on (from the start) |
| `startFrameRate` | the median frame time in the first seconds is above 40 ms |
| `watchdog` | 12 of the last 60 frames while scrolling take longer than 50 ms (switches without a reload and continues at the same chapter) |

They are off because they switched the animation off unexpectedly. Set a switch to `true` to use
it. Independent of them:

- `prefers-reduced-motion` always gets the static page;
- `?scene=static` / `?scene=pinned` force a mode for one visit (no fallback then);
- `?fallback=on` switches all three on for one visit (testing; the e2e test of the watchdog uses it).

### Measured (October 2026)

Chromium, phone emulation, 4× CPU throttling, scrolled from the top to the footer by setting the
scroll position (40 px per frame, and 120 px per frame for a fast swipe), at 360 × 740, 390 × 844,
412 × 915, 430 × 932 and 390 × 640 (smoothing 0.4 s in both cases):

| Distance | Frames over 50 ms | p95 frame time (median of the 10 runs) |
|---|---|---|
| 6 screens | 27 of 1049 (2.6 %) | 41 ms (28–66 ms) |
| 4 screens (two runs) | 5 of 778 (0.6 %), 9 of 778 (1.2 %) | 37 ms / 35 ms (27–45 ms) |

The shorter distance does not stutter more; the differences are within the run-to-run noise.

- **Firefox** (no CPU throttling possible): all six chapters correct, footer reached; 0–27 frames over
  50 ms per run (the most at 430 × 932), p95 40–72 ms.
- **WebKit** (Safari's engine, iPhone 13 / SE / 14 Pro Max emulation): all six chapters correct, the
  sticky scene ends, footer reached. Its frame times are not meaningful on Windows: Playwright's
  WebKit renders without a GPU there — even the plain imprint page takes ≈ 130 ms per frame while
  idle at the iPhone 13's 3× density. Real iPhones have to be checked on the device.

## Changes in this round (kept)

| Change | Why |
|---|---|
| Landing on phones: no turntable frames in the preloader; frames, product and object images load after the first screen (load event + idle, or the first touch / scroll) | 15 images / 520 KB were loaded during the first seconds; the first screen needs 2. Desktop unchanged |
| Translation payload: the staff area's texts (largest namespace) only under `/admin` | Every page carried all 532 keys inline in the HTML |
| `next/image` serves AVIF (WebP fallback); the ad page's hero is sized to the real rendered width | Hero 26 → 17 KB |
| Favicon 15 KB (3 BMP icons) → 1.4 KB (PNG entries), `scripts/assets/favicon-compact.mjs` | It is fetched early with high priority |
| Fonts declared once (`src/app/fonts.ts`) | Declared in two root layouts, the build split off a second render-blocking stylesheet |
| `Cache-Control: public, max-age=2592000, stale-while-revalidate=86400` for `/scene/*`; optimized images cached 30 days (`images.minimumCacheTTL`) | Repeat visits; `_next/static` is immutable already, prerendered pages are served from the Next cache |

## Tried and rejected (measured)

| Experiment | Result |
|---|---|
| `experimental.inlineCss` | The document grew 23 → 49 KB (CSS also in the RSC payload); FCP worse, no LCP gain over 1.6 Mbit/s |
| Hero image with `preload` instead of `fetchPriority="high"` | The image lost its high fetch priority |
| Inter without preload (only the display font preloaded) | FCP +0.2–0.3 s: the font request moved behind the CSS onto the critical chain |

## Open points

- The favicon is still the default Next.js icon; a shop logo icon should replace it (run
  `scripts/assets/favicon-compact.mjs` after replacing `src/app/favicon.ico`).
- Field data (Chrome UX Report / Search Console Core Web Vitals) will be the real measure once the site is live;
  lab scores on one machine are only for comparing builds.
