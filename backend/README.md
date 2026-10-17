# Backend — booking API

The REST API for the barbershop template, built with:

- Java 21, Spring Boot 3.5 and Maven.
- PostgreSQL 17 with Flyway migrations.
- Spring Security with JWT for the admin area.
- Spring Mail with Thymeleaf templates for customer emails.

## Start

**Recommended: the whole stack via docker compose.** This starts Postgres, Mailpit and the backend.

```bash
cp infra/.env.example infra/.env          # then set the secrets (see below)
docker compose -f infra/docker-compose.yml up -d --build
./infra/smoke-test.sh                     # end-to-end check with curl
```

| What | URL |
|---|---|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html (OpenAPI JSON: `/v3/api-docs`) |
| Mailpit (all outgoing mail) | http://localhost:8025 |
| Health | http://localhost:8080/actuator/health |
| Postgres | `localhost:5432` (change it with `POSTGRES_HOST_PORT` if 5432 is taken) |

**Run from the IDE or Maven.** Postgres and Mailpit come from compose, the app runs locally:

```bash
docker compose -f infra/docker-compose.yml up -d postgres mailpit
# Use the same POSTGRES_PASSWORD as infra/.env:
DB_PASSWORD=... SMTP_HOST=localhost APP_JWT_SECRET=$(openssl rand -base64 48) \
APP_ADMIN_EMAIL=admin@example.com APP_ADMIN_PASSWORD=change-me-12chars \
SPRING_PROFILES_ACTIVE=demo ./mvnw spring-boot:run
```

**Tests:** these need Docker for Testcontainers.

```bash
./mvnw test      # unit tests (availability calculation, incl. DST)
./mvnw verify    # + integration tests against a real Postgres (incl. concurrent double booking)
```

## Configuration

All configuration is set through environment variables. `infra/.env.example` documents every one of them.

| Variable | Meaning |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Database connection |
| `APP_JWT_SECRET` | HS256 key for admin tokens. **Required.** At least 32 bytes. |
| `APP_ADMIN_EMAIL`, `APP_ADMIN_PASSWORD`, `APP_ADMIN_TENANT` | Creates the first admin on startup if it doesn't exist yet. The password must be at least 12 characters. An existing admin is never overwritten. |
| `APP_CORS_ALLOWED_ORIGINS` | Comma-separated browser origins, e.g. the frontend |
| `APP_FRONTEND_BASE_URL` | Base for the cancel links in emails: `{base}/{lang}/booking/cancel?token=…` |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_STARTTLS` | Mail server (Mailpit locally) |
| `APP_MAIL_FROM` | Sender address |
| `SPRING_PROFILES_ACTIVE=demo` | Also loads the demo shop "The Barber News" |

Booking rules are set under `app.booking.*` in `application.yml`:

| Setting | Value |
|---|---|
| Slot grid | 15 min |
| Minimum notice | 1 h |
| Booking horizon | 60 days |
| Cancellation cutoff | 2 h before the appointment |
| Auto-confirm | on |

Rate limits are per IP: 10 booking POSTs per minute and 5 logins per minute.

## Code structure (package by feature, `com.barbershop.*`)

| Package | Contents |
|---|---|
| `tenant` | Tenant (slug, domain, theme as jsonb, time zone) |
| `service` | Services with DE/EN fields, price formatting ("ab 35 €" / "from €35") |
| `staff` | Barbers, the services they offer, working hours, time off |
| `availability` | `AvailabilityCalculator` (pure logic, unit-tested) and the service around it |
| `booking` | Bookings, cancel tokens, admin list/filter, stats |
| `auth` | Admin login, JWT, security config, bootstrap admin |
| `mail` | Confirmation and cancellation emails (sent after the commit, async) |
| `common` | Config, i18n (locale resolver, messages), error handling (RFC 9457), rate limiting |

## API overview

`{tenant}` is the slug, e.g. `the-barber-news`.

**Public**

| | |
|---|---|
| `GET /api/public/{tenant}` | Shop info |
| `GET /api/public/{tenant}/services` | Active services |
| `GET /api/public/{tenant}/staff?service=` | Barbers, optionally filtered by service |
| `GET /api/public/{tenant}/availability?service=&staff=&date=` | Free slots of one day. Without `staff` it covers any barber. |
| `GET /api/public/{tenant}/availability/days?service=&staff=&from=&to=` | Days that have free slots |
| `POST /api/public/{tenant}/bookings` | Book: 201, 409 if the slot is taken, 422 if it isn't a valid slot |
| `GET /api/public/bookings/{token}` | View a booking via its cancel token |
| `DELETE /api/public/bookings/{token}` | Cancel (idempotent). 409 after the cutoff. |

**Admin** (`Authorization: Bearer <token>`)

| | |
|---|---|
| `POST /api/admin/auth/login`, `GET /api/admin/me` | Login and current user |
| `/api/admin/services` | CRUD (DELETE deactivates) |
| `/api/admin/staff` | CRUD, plus `PUT /{id}/services` and `GET`/`PUT /{id}/working-hours` |
| `/api/admin/time-off` | CRUD for time off. With no `staffId` it closes the whole shop. |
| `GET /api/admin/bookings` | List with `from`, `to`, `staffId`, `status`, `q`, `page`, `size`, `sort` |
| `GET /api/admin/bookings/{id}`, `POST /{id}/cancel`, `POST /{id}/confirm` | Single booking actions |
| `GET /api/admin/stats?from=&to=` | Booked and cancelled counts per local day and per service |

## Languages (DE/EN)

- **Response language:** `?lang=de|en`, then `Accept-Language`, then German as the default.
- **Translated fields:** services have `name_de/en` and `description_de/en`; staff have `bio_de/en`.
- **Errors:** all errors are localized RFC 9457 problem details. Each has a stable `code` field, e.g. `booking.slot_taken`, so the frontend can rely on it. Texts are in `messages.properties` (DE) and `messages_en.properties`.
- **Bookings:** each booking stores the customer's language (`lang` in the request body, otherwise the request locale). Emails always use that language.

## Double booking protection

There are three layers:

1. **Check against the schedule.** The requested start must be a real slot (working hours, time off, notice, horizon), otherwise the API returns **422**.
2. **Overlap check before the insert.** An overlap returns **409**.
3. **Database constraint.** This one also holds under concurrency:
   `EXCLUDE USING gist (staff_id WITH =, time_range WITH &&) WHERE (status <> 'CANCELLED')` on a generated `tstzrange` column (`btree_gist`). A violation (SQLSTATE 23P01) also becomes a **409**.

With "any barber", the next free barber is tried instead. `BookingConcurrencyIT` fires 16 parallel requests at the same slot:

- For one barber, exactly 1 request succeeds.
- For any barber, exactly 2 succeed (one per barber).

## Times

All timestamps are stored as `timestamptz` (UTC). Working hours are local times in the tenant's time zone (`Europe/Berlin`).

The API returns times with the local offset, e.g. `2026-10-20T10:00:00+02:00`. DST changes are covered by unit tests.

## Security notes

- Passwords use BCrypt with cost 12. Login runs in constant time, with a dummy hash for unknown emails.
- Cancel tokens are 256-bit random values. Only their SHA-256 hash is stored.
- Admin tokens are JWTs (HS256, 8 h) and are scoped to one tenant.
- The Docker image runs as a non-root user.
