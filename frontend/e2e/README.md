# End-to-end tests (Playwright)

The suite runs against the **running local stack** — real backend, Postgres, Mailpit
and the production build of the frontend. Nothing is mocked.

## Start the stack

```bash
# backend + Postgres + Mailpit, with raised per-IP rate limits
# (one test run makes far more logins/bookings than the production limits allow)
APP_RATE_LIMIT_BOOKINGS=1000 APP_RATE_LIMIT_LOGIN=1000 APP_RATE_LIMIT_AUTH_MAIL=1000 \
APP_RATE_LIMIT_TOKEN_ACTIONS=1000 APP_RATE_LIMIT_REFRESH=1000 \
  docker compose -f infra/docker-compose.yml up -d --build

# frontend (production build) on http://localhost:3000
cd frontend
npm run build && npm run start
```

Afterwards restart the backend with the normal limits:
`docker compose -f infra/docker-compose.yml up -d backend`.

## Run

```bash
cd frontend
npm run e2e:install   # once: Playwright's Chromium
npm run e2e           # all specs × 4 variants
npx playwright test flow --project=mobile-de-dark   # one spec, one variant
```

Every spec runs in four projects: desktop 1440×900 and phone 390×844, each as
**DE / dark** and **EN / light**. Tests run one at a time (`workers: 1`): there is one
shop and one calendar, and the tests compete for the same free slots.

## What it needs and changes

- `infra/.env` must contain `APP_ADMIN_EMAIL` / `APP_ADMIN_PASSWORD` (the global setup logs in as admin).
- The global setup cancels active bookings of earlier runs (customer name `E2E …`),
  replaces Luca's barber login with a fresh one (German emails, read from Mailpit)
  and stores the logins in `e2e/.state.json` (git-ignored).
- Tests create guests and accounts with unique `@example.com` addresses and use
  `docker exec barbershop-postgres-1 psql` for things the UI can't do on purpose:
  seeding 10 loyalty visits, expiring a link.

## Specs

| Spec | Covers |
| --- | --- |
| `flow.spec.ts` | Book button → services → date/barber → details (guest) → summary → status page; barber accepts via the Mailpit link; Pay → demo payment page → simulate; both confirmation emails in the right language; register + verify → My bookings; barber calendar; cancel with refund info |
| `special.spec.ts` | 11th visit gets the loyalty discount (seeded account); expired and already-used links; double booking of one slot by two browsers |
| `pages.spec.ts` | Screenshots of every other page: landing, booking page, login, register, forgot/reset password, privacy, invalid link, guest manage/cancel, profile, staff login and every admin tab |

## Screenshots

Written to `screenshots/phase3/<project>/` in the repo root (git-ignored). Full-page
captures; while a modal is open only the viewport. Sticky bars (the booking flow's
step footer) appear where they sat in the viewport, which in a full-page capture can
be mid-page — that is the capture, not the layout.
