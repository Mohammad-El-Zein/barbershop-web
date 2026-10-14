# Barbershop Web — premium landing page + reservation system (template)

Monorepo:

| Folder | What | Status |
|---|---|---|
| `frontend/` | Next.js 16 (App Router) + TypeScript + Tailwind 4 + GSAP/ScrollTrigger + Lenis | Phase 1 ✅ |
| `backend/` | Java 21 + Spring Boot 3 REST API, PostgreSQL, Flyway | Phase 2 |
| `infra/` | Docker, docker-compose, CI, deployment | Phase 4 |

## Frontend quick start

```bash
cd frontend
npm install
npm run dev          # http://localhost:3000
npm run build        # production build
npm run lint && npm run typecheck
npm run placeholders # regenerate labeled placeholder assets
```

## Rebranding for a new client

Everything lives in **`frontend/src/config/site.config.ts`**: brand, colors, nav, chapter
texts/CTAs, scroll ranges, scene assets, optional frame sequence, services, contact, footer.
Asset specs: `frontend/public/scene/README.md`.

## Scroll scene architecture

- **Technique:** layered transparent objects animated by one GSAP master timeline
  (transform/opacity only), plus an optional canvas image-sequence background
  (`scene.frames.enabled`) for a "video scrubbing" look.
- The pinned section (`components/scene/PinnedScene.tsx`) scrubs a timeline whose duration
  is normalized to 1, so timeline time == chapter `range` values in the config.
- Choreography per stage preset (`hero`, `products`, `services`, `feature`, `orbit`, `cta`)
  is in `components/scene/timeline.ts`.
- Lenis drives smooth scrolling from `gsap.ticker`; `lenis.on('scroll', ScrollTrigger.update)`.
- `prefers-reduced-motion` → static stacked sections (`StaticScene.tsx`), no Lenis.
