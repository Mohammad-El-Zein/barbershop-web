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

## Changes in this round (kept)

| Change | Why |
|---|---|
| Landing on phones: no turntable frames in the preloader; frames, product and object images load on the first interaction (touch / scroll / key) | 15 images / 520 KB were loaded during the first seconds; the first screen needs 2. Desktop unchanged |
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
