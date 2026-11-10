# Barbershop Web — landing page + online booking system (template)

A complete website for a barbershop: an animated landing page, online booking with barber approval and a
deposit, customer accounts with a loyalty programme, a staff area for barbers and admins, and the
infrastructure to run it (Docker, CI/CD, Azure). Built as a **template**: one config file rebrands it for
another shop, and every database table is scoped to a tenant.

Demo shop: "THE BARBER NEWS", German and English, dark and light theme. Payments are **demo only**.

| Folder | What |
|---|---|
| `frontend/` | Next.js 16 (App Router), React 19, TypeScript, Tailwind 4, GSAP/ScrollTrigger + Lenis, next-intl (DE/EN), typed API client generated from the backend's OpenAPI schema, Playwright e2e tests |
| `backend/` | Java 21, Spring Boot 3.5 REST API, PostgreSQL 17, Flyway, Spring Security (JWT + rotating refresh cookie), mail outbox, demo payments — details: [`backend/README.md`](backend/README.md) |
| `infra/` | Production images, docker-compose (local, e2e, VPS with Caddy), Azure Terraform (`infra/azure`) — details: [`infra/README.md`](infra/README.md) |
| `docs/` | [`DEPLOYMENT.md`](docs/DEPLOYMENT.md) (production guide), [`CD-SETUP.md`](docs/CD-SETUP.md) (automatic deploy), [`DOMAIN.md`](docs/DOMAIN.md) (own domains), [`PERFORMANCE.md`](docs/PERFORMANCE.md) (measurements) |
| `.github/workflows/`, `.gitlab-ci.yml` | CI (tests, scans, images, e2e) and CD (deploy to Azure) |

> **Deadline: upgrade to Spring Boot 4 before 2027-01-31.** Spring Boot 3.5 is out of open-source support; two
> Spring MVC CVEs (not reachable in this API) are accepted in `.trivyignore` only until then. After that date
> the image scan in CI fails and no release can be built. Details: `docs/DEPLOYMENT.md` → Security notes.

## What it does

**Customers**

- Landing page with a scroll-driven scene (chair, scissors, products, services), also on phones.
- Booking in four steps: services → barber and time → contact details → summary. As a guest or with an account.
- Status page right after booking; emails with links to pay, manage or cancel; calendar file (`.ics`).
- Account: register with email verification, upcoming and past bookings, profile, password reset,
  data export and account deletion (GDPR).
- Loyalty: after 10 completed visits the next booking is 50 % off (both numbers are per shop).
- Ad landing pages (`/lp/<campaign>`) with campaign attribution, cookie consent, legal pages.

**Barbers**

- Accept or decline a request straight from the email, or in the staff area.
- Calendar (day / week), own requests and bookings, complete / no-show / cancel.

**Admins**

- All bookings with filters, manual bookings (walk-in, phone), refunds.
- Services, barbers and their logins, working hours and time off, customers, booking rules.
- Statistics (including campaigns) and the email delivery list with resend.
- Delete bookings, barbers, services and a customer's history without losing the money records.

### Booking lifecycle

```
online request ─► PENDING_BARBER ─► barber accepts ─► AWAITING_PAYMENT ─► deposit paid ─► CONFIRMED ─► COMPLETED
                        │                                    │                                │
                        └─ declined / expired / cancelled    └─ expired / cancelled           └─ cancelled / no-show
```

One state machine holds every allowed transition. Unanswered requests and unpaid deposits expire by a job.
The deposit (10 € by default) is refunded when the customer cancels at least 24 h before, and always when the
shop cancels. Full rules: [`backend/README.md`](backend/README.md#booking-lifecycle).

## Pages

All pages exist under `/de/…` and `/en/…`.

| Path | Page |
|---|---|
| `/` | Landing page |
| `/booking` | Booking flow |
| `/booking/status/[token]`, `/booking/manage/[token]`, `/pay/[token]` | Status, manage / cancel, demo payment (links from the emails) |
| `/barber/requests/[token]` | Barber's accept / decline page |
| `/account`, `/account/login`, `/register`, `/profile`, `/forgot-password`, `/reset-password/[token]`, `/verify/[token]` | Customer account |
| `/admin` + `/bookings`, `/services`, `/staff`, `/time-off`, `/customers`, `/stats` | Staff area (barbers see the calendar and their bookings; the rest is admin only) |
| `/lp/[campaign]` | Ad landing pages (`src/config/landing-pages.ts`) |
| `/impressum`, `/datenschutz`, `/agb` | Legal pages |

## Quick start

Needs Docker and Node.js.

```bash
cp infra/.env.example infra/.env                         # set DB password, JWT secret, admin login
docker compose -f infra/docker-compose.yml up -d --build # Postgres + API + Mailpit
```

```bash
cd frontend
cp .env.example .env.local
npm install
npm run dev                                              # http://localhost:3000
```

| What | URL |
|---|---|
| Website | http://localhost:3000 |
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Mailpit (all outgoing mail) | http://localhost:8025 |

`./infra/smoke-test.sh` walks through the main flow with curl. Windows (PowerShell, no bash): see
[`backend/TESTING-WINDOWS.md`](backend/TESTING-WINDOWS.md) —
`powershell -ExecutionPolicy Bypass -File infra\demo-flow.ps1` runs the whole flow
(request → barber accepts via email link → demo payment → confirmed → cancel with refund).

> Payments are **demo only** (no real provider, no card data). The payment page has a "Simulate payment"
> button. How to plug in Stripe later:
> [`backend/README.md`](backend/README.md#adding-a-real-provider-later-eg-stripe).

## Tests

| Where | Command | What |
|---|---|---|
| Backend | `cd backend && ./mvnw test` | Unit tests: state machine, pricing, loyalty, refund rule, deadlines, availability, `.ics` |
| Backend | `cd backend && ./mvnw verify` | + integration tests against a real Postgres (Testcontainers) and GreenMail: full flows, parallel bookings, jobs, accounts, rate limits, security headers, retention, deletion |
| Frontend | `cd frontend && npm run lint && npm run typecheck && npm run check:messages` | ESLint, TypeScript, DE/EN message parity |
| Frontend | `cd frontend && npm run e2e` | Playwright against the running stack, nothing mocked; every spec on desktop and phone, DE/dark and EN/light; includes accessibility checks (axe) — [`frontend/e2e/README.md`](frontend/e2e/README.md) |
| Infra | `terraform test` in `infra/azure` | Offline tests with mocked providers |
| Speed | `cd frontend && npm run perf:ab` | Lighthouse A/B between two builds — [`docs/PERFORMANCE.md`](docs/PERFORMANCE.md) |

Other frontend scripts: `npm run build`, `npm run api:generate` (regenerate the typed API client from the
backend's OpenAPI schema), `npm run placeholders` (regenerate labeled placeholder assets).

## CI/CD and deployment

```
push / pull request ─► CI: backend tests · frontend lint, types, build · terraform test · dependency scan
                           · images (build, Trivy scan, push to ghcr.io) · e2e (Playwright on docker compose)
push to main, CI green ─► CD: deploy to Azure "dev" (automatic)
release tag v*          ─► CD: deploy to "prod" — prepared, off until PROD_DEPLOY_ENABLED=true, with manual approval
```

- The deploy logs in to Azure with OIDC (no stored secret), runs the database migration job first, then
  updates the backend, then the frontend, and waits for the readiness check. Setup: [`docs/CD-SETUP.md`](docs/CD-SETUP.md).
- Azure (`infra/azure`, Terraform): Container Apps, Postgres Flexible Server in a private network, Key Vault,
  Container Registry, logs and alerts. Own domains with managed certificates: [`docs/DOMAIN.md`](docs/DOMAIN.md)
  (dev: `barbershop.elzein.dev`, API `barbershop-api.elzein.dev`).
- Alternative on one server: `infra/docker-compose.prod.example.yml` with Caddy (automatic HTTPS).
- `.gitlab-ci.yml` mirrors the CI pipeline for GitLab.

## Security and privacy

- Sessions: 15-minute JWT access token plus a rotating refresh token in an httpOnly, `SameSite=Strict` cookie.
  Reusing an old refresh token ends the whole session family.
- Email links: random 256-bit tokens, stored only as HMAC, bound to one purpose, single use, expiring.
  A GET never changes anything, so email scanners can't accept or cancel a booking.
- Double bookings are impossible: a PostgreSQL exclusion constraint blocks overlapping slots per barber.
- Emails go through a transactional outbox with retries: no booking without its mail, no mail for a
  rolled-back booking.
- Rate limits per IP (Bucket4j; memory or Redis), login lockout, optional Cloudflare Turnstile CAPTCHA,
  security headers and CSP, no user enumeration.
- In the `prod` profile the app refuses to start with weak secrets, demo payments, no CAPTCHA or insecure
  cookie / CORS settings (unless a demo is allowed explicitly).
- GDPR: consent before analytics / ads scripts, data export, account deletion, automatic anonymisation of
  old bookings, logs without personal data.

Details: [`backend/README.md`](backend/README.md#production-hardening).

## Rebranding for a new client

Everything lives in **`frontend/src/config/site.config.ts`**: brand, colors, nav, chapter
texts/CTAs, scroll ranges, scene assets, optional frame sequence, services, contact, footer.
Texts: `frontend/messages/*.json`. Legal data: `frontend/src/config/legal.config.ts`. Ad pages:
`frontend/src/config/landing-pages.ts`. Asset specs: `frontend/public/scene/README.md`.
Services, barbers, opening hours and booking rules come from the backend (admin area).

## Scroll scene architecture

- **Technique:** layered transparent objects animated by one GSAP master timeline
  (transform/opacity only), plus an optional canvas image-sequence background
  (`scene.frames.enabled`) for a "video scrubbing" look.
- The pinned section (`components/scene/PinnedScene.tsx`) scrubs a timeline whose duration
  is normalized to 1, so timeline time == chapter `range` values in the config.
- Choreography per stage preset (`hero`, `products`, `services`, `feature`, `orbit`, `cta`)
  is in `components/scene/timeline.ts`.
- Desktops: ScrollTrigger pin, scrub smoothing; Lenis drives smooth scrolling from `gsap.ticker`
  (`lenis.on('scroll', ScrollTrigger.update)`).
- Phones and touch devices (`lib/sceneMode`): a light variant of the same scene — a sticky track of
  `MOBILE_PIN_SCREENS` screens (4) instead of a pin, a short trail behind the finger
  (`MOBILE_SCRUB_SECONDS`, 0.4 s), native scrolling (no Lenis), no filters on moving layers, a small
  chair frame set kept as decoded bitmaps. The knobs: `site.config.ts`, docs/PERFORMANCE.md.
- Static stacked sections (`StaticScene.tsx`, no GSAP / Lenis): `prefers-reduced-motion`. Automatic
  fallbacks (weak device, start frame rate, scroll watchdog) exist but are off by default
  (`SCENE_AUTO_FALLBACK`). `?scene=static` / `?scene=pinned` force a mode.
