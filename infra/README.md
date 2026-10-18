# Infra

Containers, compose stacks, CI/CD and infrastructure as code. The step-by-step production guide is
[`docs/DEPLOYMENT.md`](../docs/DEPLOYMENT.md).

| File | Purpose |
|---|---|
| `docker-compose.yml` + `.env.example` | **Local development**: Postgres, Mailpit and the backend (demo shop). `cp .env.example .env` |
| `docker-compose.e2e.yml` | Override for **end-to-end tests / CI**: raised rate limits plus the production frontend image, built against the running backend |
| `docker-compose.prod.example.yml` + `.env.prod.example` | **Production on one server (VPS)**: Caddy with automatic HTTPS, frontend, backend, Postgres (internal network), optional Redis |
| `caddy/Caddyfile` | Reverse proxy for `SITE_DOMAIN` → frontend and `API_DOMAIN` → backend (only `/actuator/health*` of the actuator is public, API docs blocked) |
| `azure/` | Terraform for Azure Container Apps + Postgres Flexible Server + Key Vault + ACR + monitoring (**not applied**) |
| `smoke-test.sh` | Full booking flow against a running stack (request → accept → demo payment → cancel, mails in Mailpit) |
| `demo-*.ps1` | The same flows for Windows PowerShell |

## Images

Both Dockerfiles build multi-stage, run as the non-root user `10001` and have a `HEALTHCHECK`.

| | Base | Contents | Health |
|---|---|---|---|
| `backend/Dockerfile` | `eclipse-temurin:21-jre-alpine-3.22` | Spring Boot layers (dependencies / loader / snapshots / app → small app layer, good cache reuse), `SPRING_PROFILES_ACTIVE=prod`, container-aware heap, `ExitOnOutOfMemoryError`, UTC | `GET /actuator/health/readiness` |
| `frontend/Dockerfile` | `node:24-alpine3.22` | Next.js `standalone` server (traced `node_modules`, OG fonts), static assets, no npm at runtime | `GET /robots.txt` |

The frontend's `NEXT_PUBLIC_*` values are compiled into the browser bundle, so build **one frontend image per
environment / domain** (build args). Pass `--build-arg API_URL_INTERNAL=…` with `--network host` while a backend
is reachable, and the pages are prerendered with the real shop data. Without it they are prerendered with defaults
and refreshed about a minute after start.

```bash
docker build -t barbershop-backend backend
docker build -t barbershop-frontend \
  --build-arg NEXT_PUBLIC_SITE_URL=https://www.example-barber.de \
  --build-arg NEXT_PUBLIC_API_URL=https://api.example-barber.de frontend
```

## E2E stack (what CI runs)

```bash
C="docker compose -f infra/docker-compose.yml -f infra/docker-compose.e2e.yml --env-file infra/.env"
$C up -d --build --wait backend mailpit
$C build frontend && $C up -d --wait frontend
cd frontend && E2E_TRACKING_BUILD=1 npx playwright test
$C down   # afterwards (the dev backend keeps its normal rate limits on the next `up`)
```

## CI/CD

`.github/workflows/ci.yml` and `.gitlab-ci.yml` run the same pipeline:

1. **Lint and test.** Backend `mvnw verify` with Testcontainers (runner Docker / docker-in-docker); frontend lint,
   typecheck, message parity and build.
2. **Dependency scan.** Trivy filesystem scan (npm lockfile, Maven pom, secrets) and `npm audit --omit=dev`.
   Fixable HIGH/CRITICAL findings fail the build.
3. **Images.** Build with registry/GHA layer cache, Trivy image scan, push. Tags: `sha-<short sha>`, the branch name,
   and `X.Y.Z` + `X.Y` for a `vX.Y.Z` git tag.
4. **E2E.** Playwright against the compose stack above.
5. **Deploy (manual).** OIDC login to Azure (no stored secret), copy the images into ACR, `az containerapp update`
   backend then frontend, readiness check.

The pipelines contain no secrets: everything comes from CI variables (see `docs/DEPLOYMENT.md` → CI/CD).
