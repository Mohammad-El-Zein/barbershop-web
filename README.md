# Barbershop Web — premium landing page + reservation system (template)

Monorepo:

| Folder | What | Status |
|---|---|---|
| `frontend/` | Next.js 16 (App Router) + TypeScript + Tailwind 4 + GSAP/ScrollTrigger + Lenis | Phase 1 ✅ |
| `backend/` | Java 21 + Spring Boot 3 REST API, PostgreSQL, Flyway — booking lifecycle (barber approval → deposit → confirmed), accounts, loyalty, DEMO payments | Phase 2b ✅ |
| `infra/` | Production images, docker-compose (local, e2e, VPS with Caddy), GitHub Actions + GitLab CI, Azure Terraform (`infra/azure`, not applied) | Phase 4 ✅ — guide: [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md) |

> **Deadline: upgrade to Spring Boot 4 before 2027-01-31.** Spring Boot 3.5 is out of open-source support; two
> Spring MVC CVEs (not reachable in this API) are accepted in `.trivyignore` only until then. After that date
> the image scan in CI fails and no release can be built. Details: `docs/DEPLOYMENT.md` → Security notes.

## Backend quick start

Needs Docker. Details are in [`backend/README.md`](backend/README.md).

```bash
cp infra/.env.example infra/.env                         # set DB password, JWT secret, admin login
docker compose -f infra/docker-compose.yml up -d --build # Postgres + API + Mailpit
./infra/smoke-test.sh                                    # curl walkthrough of the main flow
cd backend && ./mvnw verify                              # unit + Testcontainers integration tests
```

Windows (PowerShell, no bash): see [`backend/TESTING-WINDOWS.md`](backend/TESTING-WINDOWS.md) —
`powershell -ExecutionPolicy Bypass -File infra\demo-flow.ps1` runs the whole flow
(request → barber accepts via email link → demo payment → confirmed → cancel with refund).

> Payments are **demo only** (no real provider, no card data). How to plug in Stripe later:
> [`backend/README.md`](backend/README.md#adding-a-real-provider-later-eg-stripe).

| What | URL |
|---|---|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Mailpit | http://localhost:8025 |

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
