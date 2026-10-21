# Deployment guide

How to take the barbershop website and booking system to production. There are two routes with the same images:

| | **Azure Container Apps** (`infra/azure`, Terraform) | **One VPS** (`infra/docker-compose.prod.example.yml`) |
|---|---|---|
| For | Agencies, several shops, managed services, SLAs | One shop, smallest budget |
| HTTPS | Container Apps managed certificates | Caddy + Let's Encrypt |
| Database | Postgres Flexible Server, PITR backups, optional HA | Postgres container + nightly `pg_dump` |
| Effort | Medium (one-time Terraform + DNS) | Small (one server, one compose file) |
| Cost | ~50–60 € / month small, ~450–600 € / month HA (see [Costs](#costs)) | ~10–20 € / month |

> **Deadline — Spring Boot 4 upgrade by 2027-01-31.** Spring Boot 3.5 gets no more open-source releases. Two
> Spring MVC CVEs are accepted in `.trivyignore` only until that date; after it the image scan fails and CI
> stops building releases. Plan the migration before then ([section 10](#10-security-notes-and-open-points)).

> **Status:** written and validated locally only. Nothing has been deployed to a cloud account. Terraform is
> formatted, validated and tested with mocked providers. The images, the compose stack and the e2e suite run locally.

---

## Contents

1. [Before go-live (checklist)](#1-before-go-live-checklist)
2. [Configuration reference](#2-configuration-reference)
3. [Azure: first deployment](#3-azure-first-deployment)
4. [CI/CD setup](#4-cicd-setup)
5. [Database, Flyway, first admin and shop data](#5-database-flyway-first-admin-and-shop-data) (incl. [Database roles](#database-roles))
6. [DNS and email](#6-dns-and-email)
7. [Releases and rollback](#7-releases-and-rollback)
8. [Backup and restore](#8-backup-and-restore)
9. [Monitoring](#9-monitoring)
10. [Security notes and open points](#10-security-notes-and-open-points)
11. [Public demo deployment](#11-public-demo-deployment)
12. [Onboarding a second shop (tenant)](#12-onboarding-a-second-shop-tenant)
13. [Alternative: one VPS with docker compose](#13-alternative-one-vps-with-docker-compose)
14. [Costs](#costs)

---

## 1. Before go-live (checklist)

- [ ] **Legal texts reviewed by a lawyer.** Impressum, Datenschutz and AGB are templates with `[…]` placeholders
      (`frontend/src/config/legal.config.ts`, `frontend/src/content/legal/README.md`). Set `reviewed: true` only
      after the review.
- [ ] **Retention periods** in `legal.config.ts` match the backend (`APP_RETENTION_*`, default 6 months / 30 days)
      and the tax adviser's rule for completed bookings.
- [ ] **Payments:** only the DEMO provider exists. A real shop must not go live with deposits until a real provider
      is integrated (`backend/README.md` → "Adding a real provider"). The prod profile refuses to start with the
      demo provider unless `PAYMENT_DEMO_ALLOW_IN_PROD=true`, which is meant for a [public demo](#11-public-demo-deployment) only.
- [ ] **Domains** chosen (`www.` for the site, `api.` for the backend) and DNS access available.
- [ ] **SMTP** account with SPF/DKIM for the sender domain ([section 6](#6-dns-and-email)).
- [ ] **Cloudflare Turnstile** site key + secret (free). The prod profile refuses to start without a CAPTCHA unless
      `APP_CAPTCHA_ALLOW_NONE_IN_PROD=true`.
- [ ] **Analytics / ads** IDs (optional). They load only after consent; the privacy policy names them.
- [ ] **Brand and content** in `frontend/src/config/site.config.ts` (root README → "Rebranding").
- [ ] **Shop data** ready: services with prices and durations, barbers with working hours, address, phone.

## 2. Configuration reference

### Backend (container environment)

| Variable | Required | Meaning |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | ✔ | `prod` (image default). `prod,demo` only for a public demo (loads the demo shop seed) |
| `DB_URL` | ✔ | JDBC URL, e.g. `jdbc:postgresql://host:5432/barbershop?sslmode=require` |
| `DB_USERNAME`, `DB_PASSWORD` | ✔ | The **application role** (`barbershop_app`, DML only). With the prod profile the backend refuses a superuser, owner or DDL-capable login ([Database roles](#database-roles)) |
| `DB_MIGRATION_USERNAME`, `DB_MIGRATION_PASSWORD` | migration run | The **owner role** (`barbershop_migrator`), used only by Flyway. In production only the migration job / `migrate` service gets it |
| `APP_MIGRATE_ONLY` | migration run | `true` = run Flyway with the owner login, then exit (no web server, no jobs) |
| `APP_DB_MIGRATE` | – | `true` (default, local) = migrate on start; `false` (production) = only validate, refuse to start while migrations are pending |
| `APP_JWT_SECRET` | ✔ | ≥ 32 random characters (`openssl rand -base64 48`); weak or placeholder values are refused |
| `APP_TOKEN_SECRET` | – | Key for email-link tokens (default: derived from the JWT secret) |
| `APP_FRONTEND_BASE_URL` | ✔ | `https://www.example-barber.de` — base of every link in emails |
| `APP_CORS_ALLOWED_ORIGINS` | ✔ | Exactly the site origin(s), `https://` only, no `*` |
| `APP_COOKIE_SECURE` | ✔ | `true` (prod default) |
| `APP_ADMIN_TENANT` | ✔ | Shop slug, e.g. `the-barber-news` (= `NEXT_PUBLIC_TENANT`) |
| `APP_TENANT_NAME`, `APP_TENANT_TIMEZONE` | first start | Creates the shop on an empty database ([section 5](#5-database-flyway-first-admin-and-shop-data)) |
| `APP_ADMIN_EMAIL`, `APP_ADMIN_PASSWORD` | first start | First admin (≥ 12 chars); remove the password afterwards |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_STARTTLS` | ✔ | Mail server (port 587 + STARTTLS) |
| `APP_MAIL_FROM` | ✔ | `The Barber News <termine@example-barber.de>` |
| `APP_CAPTCHA_PROVIDER`, `APP_CAPTCHA_SECRET` | ✔ prod | `turnstile` + secret key |
| `APP_CAPTCHA_ALLOW_NONE_IN_PROD` | – | `true` = start without CAPTCHA anyway (not recommended) |
| `APP_RATE_LIMIT_STORE`, `APP_RATE_LIMIT_REDIS_URL` | – | `memory` (one instance) or `redis` + URL (several instances) |
| `APP_RATE_LIMIT_BOOKINGS` / `_LOGIN` / `_AUTH_MAIL` / `_TOKEN_ACTIONS` / `_REFRESH` | – | Per-IP limits (defaults are production values) |
| `PAYMENT_PROVIDER`, `PAYMENT_DEMO_ALLOW_IN_PROD` | – | `demo`; `true` only for a public demo |
| `APP_MAIL_MAX_ATTEMPTS`, `APP_MAIL_POLL_INTERVAL` | – | Email outbox: 8 attempts (~2 h), poll every 15 s |
| `APP_RETENTION_*` | – | Retention: cancelled 6 months, completed 0 (= keep), unverified accounts 30 days, outbox 30 days, cron 03:30 |
| `APP_JOBS_ENABLED`, `APP_JOBS_INTERVAL` | – | Expiry, reminders, auto-complete (every minute) |
| `LOG_FORMAT` | – | `ecs` (JSON, default in prod), `logstash`, `gelf` |
| `SPRINGDOC_API_DOCS_ENABLED`, `SPRINGDOC_SWAGGER_UI_ENABLED` | – | `false` in prod (default) |
| `APP_SHUTDOWN_TIMEOUT` | – | Graceful shutdown, `20s` |
| `APPLICATIONINSIGHTS_CONNECTION_STRING` | – | Set by Terraform; used once the Application Insights Java agent is added ([section 9](#9-monitoring)) |

Details: `backend/README.md` → Configuration and Production hardening.

### Frontend

| Variable | When | Meaning |
|---|---|---|
| `NEXT_PUBLIC_SITE_URL` | **build** | `https://www.example-barber.de` (canonical, hreflang, sitemap, OG, JSON-LD) |
| `NEXT_PUBLIC_API_URL` | **build** | `https://api.example-barber.de` |
| `NEXT_PUBLIC_TENANT` | **build** | Shop slug |
| `NEXT_PUBLIC_TURNSTILE_SITE_KEY` | **build** | Turnstile site key (also widens the CSP) |
| `NEXT_PUBLIC_SHOW_DEMO_BANNER` | **build** | Site-wide DEMO strip while the backend runs demo payments. Only `true` shows it — a [public demo](#11-public-demo-deployment) (`terraform output ci_variables` sets it there); unset = hidden (local, e2e, dev, prod). The payment page always shows its own demo notice |
| `NEXT_PUBLIC_GA4_ID`, `NEXT_PUBLIC_GOOGLE_ADS_ID`, `NEXT_PUBLIC_GOOGLE_ADS_CONVERSION_LABEL`, `NEXT_PUBLIC_META_PIXEL_ID` | **build** | Optional; loaded only after consent |
| `API_URL_INTERNAL` | runtime (and optionally build) | Backend address for server-side requests (structured data, legal pages) |

`NEXT_PUBLIC_*` values are compiled into the JavaScript, so build **one frontend image per environment / domain**.

## 3. Azure: first deployment

Prerequisites: Azure subscription (Owner or Contributor + User Access Administrator), `az` CLI, Terraform ≥ 1.9,
Docker.

```bash
az login
az account set --subscription <subscription id>
for p in Microsoft.App Microsoft.OperationalInsights Microsoft.Insights Microsoft.DBforPostgreSQL \
         Microsoft.ContainerRegistry Microsoft.KeyVault Microsoft.Network Microsoft.Communication; do
  az provider register --namespace $p
done
```

**Step 1. Remote state** (once): see `infra/azure/README.md` → Remote state.

**Step 2. Variables.** Adjust `infra/azure/env/prod.tfvars` (domains, sizes, sender, admin email). Secrets go in
the environment:

```bash
cd infra/azure
export TF_VAR_subscription_id=<subscription id>
export TF_VAR_smtp_password=…  TF_VAR_captcha_secret=…  TF_VAR_admin_password=…
terraform init -backend-config=backend.hcl
```

**Step 3. Registry and environment first.** The frontend image needs the final URLs at build time, and the apps
need an image to start:

```bash
terraform apply -var-file=env/prod.tfvars \
  -target=azurerm_container_registry.this -target=azurerm_container_app_environment.this
terraform output -raw acr_name; terraform output site_url; terraform output api_url
```

**Step 4. Build and push the first images.** Either run CI ([section 4](#4-cicd-setup)), or build by hand:

```bash
ACR=$(terraform output -raw acr_name); TAG=1.0.0
az acr login -n $ACR
docker build -t $ACR.azurecr.io/barbershop-backend:$TAG ../../backend
docker build -t $ACR.azurecr.io/barbershop-frontend:$TAG \
  --build-arg NEXT_PUBLIC_SITE_URL=$(terraform output -raw site_url) \
  --build-arg NEXT_PUBLIC_API_URL=$(terraform output -raw api_url) \
  --build-arg NEXT_PUBLIC_TENANT=the-barber-news \
  --build-arg NEXT_PUBLIC_TURNSTILE_SITE_KEY=<site key> \
  --build-arg NEXT_PUBLIC_SHOW_DEMO_BANNER=false \
  ../../frontend
docker push $ACR.azurecr.io/barbershop-backend:$TAG
docker push $ACR.azurecr.io/barbershop-frontend:$TAG
```

Set `backend_image` / `frontend_image` in the tfvars to these tags.

**Step 5. Everything else.**

```bash
terraform plan -var-file=env/prod.tfvars -out=prod.tfplan   # review: ~37 resources
terraform apply prod.tfplan
```

The backend app can't become ready yet: the database has no roles and no schema.

**Step 5b. Database roles and the first migration** ([Database roles](#database-roles)):

```bash
RG=$(terraform output -raw resource_group)
JOB_INIT=$(terraform output -raw db_init_job_name)
az containerapp job start -g $RG -n $JOB_INIT                      # once: creates the two roles
az containerapp job execution list -g $RG -n $JOB_INIT -o table    # wait for "Succeeded"
sh scripts/run-migrate-job.sh $RG $(terraform output -raw migrate_job_name) \
  $(terraform output -raw acr_login_server)/barbershop-backend:$TAG
```

The migration job:

1. runs the Flyway migrations with the owner login;
2. creates the shop (`tenant_name`) and the first admin.

The backend, which has only the application login, then validates the schema and becomes *ready* on its next
probe. If it gave up while waiting, restart it with `az containerapp revision restart`.

Check it:

```bash
curl -fsS "$(terraform output -raw api_url)/actuator/health/readiness"   # {"status":"UP"}
```

**Step 6. Custom domains and certificates.** Set `web_domain` / `api_domain`; see [DOMAIN.md](DOMAIN.md) (DNS
records, apply, one `az containerapp hostname bind`, importing domains bound by hand). Then rebuild the frontend
image for the final domain.

**Step 7. Remove the admin password** from the deployment once you have logged in:
`unset TF_VAR_admin_password && terraform apply -var-file=env/prod.tfvars`. The account stays; change its password
in the staff area.

**Step 8. Fill in the shop** ([section 5](#5-database-flyway-first-admin-and-shop-data)), then run a test booking
end to end.

## 4. CI/CD setup

Both pipelines (`.github/workflows/ci.yml`, `.gitlab-ci.yml`) run:

1. lint and test;
2. Trivy and `npm audit`;
3. image build, scan and push;
4. e2e against docker compose;
5. a deploy: GitHub deploys every green `main` run to `dev` automatically (`.github/workflows/cd.yml` →
   `deploy-azure.yml`); otherwise manually.

They contain no secrets: the deploy logs in to Azure with OIDC (federated credentials). Step-by-step setup of the
GitHub deploy (in German, Windows cmd, incl. a managed identity instead of an app registration):
[CD-SETUP.md](CD-SETUP.md).

**Azure side (once per environment):**

```bash
az ad app create --display-name barbershop-ci-prod
APP_ID=$(az ad app list --display-name barbershop-ci-prod --query "[0].appId" -o tsv)
az ad sp create --id $APP_ID
SP_OBJECT_ID=$(az ad sp show --id $APP_ID --query id -o tsv)

# GitHub: deploy jobs run in the GitHub environment "prod"
az ad app federated-credential create --id $APP_ID --parameters '{
  "name": "github-prod", "issuer": "https://token.actions.githubusercontent.com",
  "subject": "repo:<owner>/<repo>:environment:prod", "audiences": ["api://AzureADTokenExchange"] }'

# GitLab (gitlab.com): subject = project + ref, e.g. the default branch for dev.
# Exact match only; for tag-based prod deploys use a flexible federated credential
# (claims matching "project_path:<group>/<project>:ref_type:tag:ref:v*").
az ad app federated-credential create --id $APP_ID --parameters '{
  "name": "gitlab-main", "issuer": "https://gitlab.com",
  "subject": "project_path:<group>/<project>:ref_type:branch:ref:main", "audiences": ["api://AzureADTokenExchange"] }'
```

Set `ci_principal_id = "<SP_OBJECT_ID>"` in the tfvars and apply. The CI identity then gets **AcrPush** on the
registry and **Contributor** on the resource group, nothing more.

**CI variables** (GitHub: environment variables of `dev` / `prod`; GitLab: CI/CD variables scoped to the
environment):

- `AZURE_CLIENT_ID` (= `APP_ID`), `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID`;
- everything in `terraform output ci_variables`: `AZURE_RESOURCE_GROUP`, `ACR_NAME`, `BACKEND_APP_NAME`,
  `MIGRATE_JOB_NAME`, `FRONTEND_APP_NAME`, `API_URL`, `NEXT_PUBLIC_SITE_URL`, `NEXT_PUBLIC_API_URL`,
  `NEXT_PUBLIC_TENANT`, `NEXT_PUBLIC_SHOW_DEMO_BANNER`;
- optional `NEXT_PUBLIC_TURNSTILE_SITE_KEY`, `NEXT_PUBLIC_GA4_ID`, …

In GitHub, add **required reviewers** to the `prod` environment. That makes the deploy a two-person action.

**Flow:**

- A merge to `main` builds and pushes `sha-<short>` and `main`.
- GitHub: when CI is green, `cd.yml` deploys the commit to `dev` (one deploy per environment at a time).
  Manual (re)deploy: *Run workflow → deploy: dev*. GitLab: play `deploy:dev`.
- A release tag `v1.2.3` additionally pushes `1.2.3` and `1.2`. Deploy it to prod (GitHub: run the workflow on the
  tag with `prod`, or set the repository variable `PROD_DEPLOY_ENABLED=true` so `cd.yml` does it after the
  approval in the `prod` environment; GitLab: play `deploy:prod` in the tag pipeline).
- The deploy (`infra/azure/scripts/rollout.sh`):
  1. copies the scanned images into ACR (GitHub builds the frontend there with the environment's `NEXT_PUBLIC_*`);
  2. runs the **migrate job** with the release image (owner login, `infra/azure/scripts/run-migrate-job.sh`)
     and stops the deploy if a migration fails;
  3. updates the backend (application login, validates the schema), then the frontend;
  4. waits for `/actuator/health/readiness`.

## 5. Database, Flyway, first admin and shop data

- **Migrations** (`backend/src/main/resources/db/migration/V1…V8`):
  - Production runs them as a separate step before the rollout: the Azure migrate job, or the `migrate`
    service of the VPS compose.
  - Locally the backend runs them on start.
  - Flyway takes a database lock, so parallel runs are safe.
  - A failing migration stops the deploy; the running backend keeps serving the old version.
- **Writing migrations:** additive and backward compatible ("expand, then contract"), so the previous backend
  version still works against the new schema. That keeps a rollback possible without restoring the database. Never
  edit a migration that already ran.
- **No demo data in production.** The demo shop (`db/demo/R__demo_tenant.sql`) is loaded only with the `demo`
  profile. A production database starts empty. On the first start:
  1. `APP_TENANT_NAME` / `APP_TENANT_TIMEZONE` create the shop `APP_ADMIN_TENANT` with the default booking rules;
  2. `APP_ADMIN_EMAIL` / `APP_ADMIN_PASSWORD` create its first admin (email already verified).

  Both are idempotent and never overwrite anything. Admin and barber accounts never come from the public
  registration: it always creates a customer, and the client cannot choose a role.
- **Real shop data** (log in at `https://<site>/de/account/login` → tab "Barber"; admins land in the staff area
  `/de/admin`):
  1. **Barbers:** create every barber and their working hours. The opening hours on the website and in the search
     data are derived from them, and active barbers appear in the booking automatically. "Set up login" with an
     email only sends the barber an invite link; with a start password (typed or generated) the login works at
     once. A start password is shown exactly once and only its hash is stored; the barber has to replace it at the
     first sign-in. "Reset password" generates a new one and ends all of the barber's sessions. The admin sees the
     login's email and state (no login / invite pending / active) and can correct the email, but never a
     password. Unticking "Active" removes the barber from the booking and blocks their login. "Delete" removes a
     barber for good only while they have no bookings at all. Booking emails go to the barber's login email;
     for a barber without a login, all admins get them instead. Every admin account of the shop (plus the
     `APP_ADMIN_EMAIL` account) also gets a short notice on request (with Accept/Decline), acceptance, payment or
     confirmation, decline, expiry and cancellation: customer name, barber, service and time only, each address
     once per event. Manual bookings by the staff only mail the customer.
  2. **Services:** name DE/EN, description, duration, exact price. The "ab"/"from" flag is for the landing-page
     cards only.
  3. **Stats → Booking rules:** deposit, response and payment deadlines, cancellation cut-offs, lead time, loyalty.
  4. **Stats → Shop profile:** address, phone, email, price range. This feeds the website's structured data and the
     legal pages.
  5. Do a test booking with your own email address: request → accept → (demo) payment → confirmation → cancel via
     the manage link. A test account can delete itself (My account → Delete account). Cancelled test bookings are
     anonymised by the retention job after 6 months.

### Database roles

The backend never connects with an admin or owner login. Three logins, three jobs:

| Login | Rights | Used by |
|---|---|---|
| Server admin (local/VPS: the `POSTGRES_USER` superuser; Azure: `barbershop_admin`) | everything | Only `infra/postgres/init-roles.sql` (once) and backups |
| `barbershop_migrator` (`DB_MIGRATION_USERNAME`) | owns the database, schema `public` and all tables; no superuser / `CREATEROLE` / `CREATEDB` | Flyway only: migrate job (Azure), `migrate` service (VPS), backend start (local dev) |
| `barbershop_app` (`DB_USERNAME`) | `SELECT`/`INSERT`/`UPDATE`/`DELETE` on the tables, `SELECT` on the Flyway history; no DDL, owns nothing | The backend's connection pool |

- **Creating the roles:** `infra/postgres/init-roles.sql` is idempotent; running it again updates
  passwords and ownership. It is run by:
  - Docker compose: `infra/postgres/10-init-roles.sh`, automatically when the volume is created;
  - Azure: the `db-init` job.

  It is plain psql (15+), with no `DO $$` block and no `$` at all. Container Apps rewrites `$$` and `$(…)`
  in job commands and env values, and the job gets the script as an env value. The passwords come
  from the environment (`MIGRATOR_PASSWORD`, `APP_PASSWORD`), not from the command line, and are never
  printed. `tests/plan.tftest.hcl` and `DatabaseRolesIT` check both.
- **Schema `public` on Azure:** it belongs to `azure_pg_admin`, not to the database owner, and the
  admin is only a member. So the script grants `USAGE, CREATE` on it to the migrator as the admin
  (recorded as by `azure_pg_admin`). Locally the migrator itself grants, as the database owner.
  At the end, the script checks that the migrator may create in `public` and the app may not.
  Otherwise it stops with an error instead of "roles ready". `AzureDatabaseRolesIT` recreates this
  setup and runs the real migrations as the migrator. `btree_gist` (used by `V1__init.sql`) is
  allow-listed in `azure.extensions` (`postgres.tf`).
- **Rotating the passwords (Azure):** `terraform apply -replace=random_password.db_migrator
  -replace=random_password.db_app` writes new Key Vault versions. Every run of the script sets both
  passwords to exactly the values it receives, also for existing roles (`DatabaseRolesIT` checks
  this). The jobs and apps reference the secrets without a version, so Container Apps fetches the
  new version only after a while (up to about 30 minutes). Until then, db-init can still set the
  old password while migrate already has the new one, or the other way round (`28P01 password
  authentication failed`). So wait, then run db-init, then migrate, then restart the backend.
- **Grants:** after every migration, the Flyway callback `db/migration/afterMigrate.sql` gives the app
  role its DML rights on all tables, including tables added by new migrations.
- **Start-up check:** with the `prod` profile, the backend refuses to start if `DB_USERNAME` can do
  more than that: superuser, `CREATEROLE`/`CREATEDB`/`BYPASSRLS`/replication, database or
  table owner (or member of the owner role), or `CREATE` in schema `public`. Outside prod it only
  logs a warning.
- **Validate, don't migrate:** in production the backend runs with `APP_DB_MIGRATE=false`. It checks that
  the schema matches its migrations and refuses to start while one is pending.
- **Existing database from before the roles** (e.g. a local dev volume): run the script once, then restart the
  backend:
  `docker compose -f infra/docker-compose.yml exec postgres sh /docker-entrypoint-initdb.d/10-init-roles.sh`.
  It hands the existing tables to the migrator.
- **Tests:** the integration tests use the same script and both roles, and `DatabaseRolesIT` checks the
  rules. A missing grant therefore fails the tests, not production.

## 6. DNS and email

| Record | Value | For |
|---|---|---|
| `CNAME www` | frontend FQDN (Azure) / server IP as `A`/`AAAA` (VPS) | Website |
| `CNAME api` | backend FQDN (Azure) / server IP (VPS) | API |
| `TXT asuid.www`, `TXT asuid.api` | `custom_domain_verification_id` (Azure only) | Domain ownership |
| Apex `example-barber.de` | Redirect to `https://www.…` (DNS provider redirect, or the `APEX_DOMAIN` block in the Caddyfile) | |
| `TXT @` SPF | e.g. `v=spf1 include:<your mail provider> -all` | Sender domain |
| DKIM | `CNAME`/`TXT` records from the mail provider (ACS shows them for custom domains) | Signed mails |
| `TXT _dmarc` | `v=DMARC1; p=quarantine; rua=mailto:dmarc@example-barber.de` | Spoofing protection |

Azure custom domains: Cloudflare and similar must serve the records as "DNS only" (no proxy), details in
[DOMAIN.md](DOMAIN.md).

Without SPF, DKIM and DMARC the booking emails land in spam. Test them with a mail-tester service before go-live.

**Email via ACS** (`email_mode = "acs"`):

- Terraform creates the service and an Azure-managed sender domain; see `terraform output acs_sender_domain`.
- SMTP login:
  1. Register an Entra app with a client secret.
  2. Grant it the "Communication and Email Service Owner" role on the ACS resource.
  3. Use `SMTP_HOST=smtp.azurecomm.net`, `SMTP_PORT=587`, username `<acs resource name>.<app id>.<tenant id>`,
     password = client secret (`TF_VAR_smtp_password`).
- For your own domain, add it in the ACS portal and create the shown SPF/DKIM records.

## 7. Releases and rollback

- **Release:** tag `vX.Y.Z` → CI builds, scans and pushes → manual deploy. Every image tag stays in the registry.
- **Rollback of the app** (Container Apps, single revision mode). Deploy the previous tag again; it takes about a
  minute:
  ```bash
  az containerapp update -g <rg> -n <backend app>  --image <acr>.azurecr.io/barbershop-backend:<previous tag>
  az containerapp update -g <rg> -n <frontend app> --image <acr>.azurecr.io/barbershop-frontend:<previous tag>
  az containerapp revision list -g <rg> -n <backend app> -o table   # history
  ```
  Because migrations are backward compatible ([section 5](#5-database-flyway-first-admin-and-shop-data)), the
  older backend runs on the newer schema. Flyway does not roll schemas back. If a migration damaged data, restore
  the database ([section 8](#8-backup-and-restore)).
- **VPS:** set the previous tags in `infra/.env.prod`, then
  `docker compose -f infra/docker-compose.prod.example.yml --env-file infra/.env.prod up -d`.
- **Infrastructure:** Terraform changes go through `plan` review. The state history (blob versioning) allows going
  back to an earlier state file.

## 8. Backup and restore

**Azure (automatic):**

- Postgres Flexible Server makes daily snapshots plus transaction-log backups. Point-in-time restore covers
  `postgres_backup_retention_days`: 7 days in dev, 35 in prod.
- With `postgres_geo_redundant_backup = true`, the backups are also copied to the paired region.

Restore = a **new** server. Then point the backend at it:

```bash
az postgres flexible-server restore -g <rg> --name psql-barbershop-prod-restore \
  --source-server <postgres_server_name> --restore-time "2026-10-18T09:30:00Z"
# region outage: az postgres flexible-server geo-restore … --location <paired region>
```

Then:

1. set `DB_URL` of the backend app to the new server (or import it into Terraform);
2. restart the app;
3. delete the broken server only after checking the data.

Note: a server restored with private access needs its own delegated subnet / DNS settings (`az postgres
flexible-server restore --help`).

**Logical exports** (extra copies, migration to another host). Run `pg_dump` from a container inside the VNet, e.g.
a one-off Container Apps job with the `postgres:17-alpine` image:

```bash
pg_dump "host=<fqdn> dbname=barbershop user=barbershop_admin sslmode=require" -Fc -f /backup/barbershop.dump
```

Write the dump to an Azure Files mount or upload it to a storage account. Restore with
`pg_restore --clean --if-exists --no-owner -d …`.

**Test a restore** at least once before go-live and then every quarter. A backup that was never restored is a
hope, not a backup.

**VPS:** see [section 13](#13-alternative-one-vps-with-docker-compose) (nightly `pg_dump` + off-site copy).

## 9. Monitoring

| What | Where |
|---|---|
| Health | `GET /actuator/health/liveness` (process), `/readiness` (DB reachable). Container Apps probes use them |
| Availability | Application Insights standard test on `/actuator/health/readiness` from 3 EU locations; alert to `alert_email` when 2 fail |
| Logs | Log Analytics, table `ContainerAppConsoleLogs_CL`. The backend writes JSON (ECS) with `requestId`; there is no personal data in the logs |
| Mail delivery | Staff area → Stats → Email delivery (`RETRY`/`DEAD`, resend). Alert when a mail is `DEAD` |
| Campaigns | Staff area → Stats → Campaigns (UTM / gclid / fbclid, only with marketing consent) |
| Costs | Azure Cost Management: set a budget with an email alert on the resource group |

**Mail alert switched off in dev (`mail_dead_alert_enabled = false`):** The mail alert is a log query
alert (`azurerm_monitor_scheduled_query_rules_alert_v2`). When Azure creates it, it checks the query
against the Log Analytics workspace. In the dev subscription this failed with "insufficient access".
Likely causes: the table `ContainerAppConsoleLogs_CL` does not exist until the first logs arrive, or
the deploying account may not query the workspace. The variable switches off only this alert. The
availability alert stays, and prod keeps both. To turn it back on later, set `true` and run apply
again once the backend has written logs.

Useful queries (Log Analytics):

```kusto
// backend errors in the last 24 h
ContainerAppConsoleLogs_CL
| where ContainerAppName_s endswith "-api" and TimeGenerated > ago(24h)
| extend j = parse_json(Log_s)
| where tostring(j.log.level) in ("ERROR", "WARN")
| project TimeGenerated, level = tostring(j.log.level), logger = tostring(j.log.logger),
          message = tostring(j.message), requestId = tostring(j.requestId)

// everything of one request (the id the client got in X-Request-Id)
ContainerAppConsoleLogs_CL | where Log_s has "<request id>" | project TimeGenerated, Log_s
```

**Traces and metrics in Application Insights (optional):** the connection string is already set
(`APPLICATIONINSIGHTS_CONNECTION_STRING`). To add the Java agent:

1. Add the agent jar to the backend image, e.g. `ADD https://github.com/microsoft/ApplicationInsights-Java/releases/download/<version>/applicationinsights-agent-<version>.jar /app/ai.jar`.
2. Append `-javaagent:/app/ai.jar` to `JAVA_TOOL_OPTIONS`.

Check the privacy policy before you enable request telemetry.

## 10. Security notes and open points

Implemented:

- **Start-up guards:** the prod profile refuses weak secrets, insecure cookies, wildcard or http CORS, demo payments
  and a missing CAPTCHA.
- **Headers and endpoints:** strict security headers (backend, plus a CSP on the frontend); actuator health only;
  Swagger off.
- **Isolation:** non-root images; Postgres private in a VNet; secrets only in Key Vault, read through a managed
  identity.
- **CI:** OIDC deploys without stored cloud credentials; Trivy scans (filesystem and images) and `npm audit` in
  CI; actions pinned to SHAs.

Open, in order of importance:

1. **Spring Boot 4 upgrade — deadline 2027-01-31.** Spring Boot 3.5.16 is the last open-source 3.5 release. Its security fixes are
   pinned in `backend/pom.xml` (Tomcat, Netty, Jackson, pgjdbc). Two Spring MVC CVEs are fixed only in Spring 7.
   They are not reachable in this API (no XsltView, SSE or views) and are accepted in `.trivyignore` until
   2027-01-31; after that the scan fails again. Plan the Boot 4 migration before that date.
2. **CSP keeps `'unsafe-inline'` for scripts:** Next.js inline bootstrap scripts; nonces would make every page
   dynamic. Third-party hosts are pinned per configured integration.
3. **Rate limits are per instance** unless Redis is configured. Terraform refuses `backend_max_replicas > 1`
   without it.
4. **Payments are demo only** (see the checklist). With the `prod` profile the backend refuses to start with the
   demo provider unless `PAYMENT_DEMO_ALLOW_IN_PROD=true`. Azure dev sets it with `payment_demo_allow_in_prod = true`
   in `env/dev.tfvars`. Terraform refuses that variable in the prod environment, so a prod deployment only starts
   once a real payment provider exists (or as an explicit `public_demo`).
5. **Legal texts are templates** (see the checklist).

## 11. Public demo deployment

A public demo (portfolio, sales) shows the full flow with simulated payments:

- Azure: `public_demo = true` (plus a Turnstile secret, or `allow_missing_captcha = true`). The backend then runs
  `SPRING_PROFILES_ACTIVE=prod,demo` with the demo shop seed and `PAYMENT_DEMO_ALLOW_IN_PROD=true`, and
  `ci_variables` sets `NEXT_PUBLIC_SHOW_DEMO_BANNER=true`.
- VPS: `SPRING_PROFILES_ACTIVE=prod,demo`, `PAYMENT_DEMO_ALLOW_IN_PROD=true` and `NEXT_PUBLIC_SHOW_DEMO_BANNER=true`
  in `infra/.env.prod`.
- CI builds hide the strip by default: set the variable `NEXT_PUBLIC_SHOW_DEMO_BANNER=true`.

Every page then shows a striped **DEMO bar** (server-rendered, above everything). The payment page has its own
banner, and payment emails say "DEMO MODE". No card fields exist, and no money moves.

Use a separate database and a separate domain (e.g. `demo.example-barber.de`). Demo emails go to whatever address
a visitor types in, so use a sandbox SMTP (e.g. Mailtrap) or a sender domain you control, and keep the rate limits
and the CAPTCHA on.

## 12. Onboarding a second shop (tenant)

The database is multi-tenant (`tenant_id` everywhere). A deployment currently serves **one** shop, though. The
frontend is built for one `NEXT_PUBLIC_TENANT` with its own `site.config.ts`, and the backend has one
`APP_FRONTEND_BASE_URL` for all email links.

**Recommended today: one stack per shop.** Clean isolation (data, costs, domains, deletion):

1. Create a branch or folder of the site configuration with the shop's brand, texts and images (`site.config.ts`,
   `public/scene`, legal config).
2. Copy `infra/azure/env/prod.tfvars` → `prod-<shop>.tfvars` with its own `name_prefix` (e.g. `"salonnord"`),
   `tenant_slug`, `tenant_name`, domains and sender. Use its own state `key` in `backend.hcl`.
3. Follow [section 3](#3-azure-first-deployment) with these files. CI needs one set of environment variables per
   shop (e.g. GitHub environments `prod-salonnord`).

Small shops with a VPS: one compose stack per shop on the same server works too. Use a separate project name
(`docker compose -p salonnord …`) and add the domains to the Caddyfile.

**Shared backend for several shops** (cheaper at scale) needs code changes first:

- per-tenant email link base URLs: `tenant.domain` exists in the schema, but links use the global
  `APP_FRONTEND_BASE_URL`;
- CORS for all shop domains;
- one frontend image per shop pointing at the shared API.

## 13. Alternative: one VPS with docker compose

For a single shop: a VPS with 2 vCPU / 4 GB RAM (e.g. Hetzner CX22/CPX21, netcup, IONOS; Germany for data
residency), Ubuntu 24.04, Docker Engine + compose plugin.

```bash
# server hardening (minimum)
adduser deploy && usermod -aG docker deploy           # no root logins, SSH keys only
ufw allow OpenSSH && ufw allow 80 && ufw allow 443 && ufw allow 443/udp && ufw enable
apt install unattended-upgrades

# app
git clone <repo> barbershop && cd barbershop
cp infra/.env.prod.example infra/.env.prod && chmod 600 infra/.env.prod
#   fill in domains, secrets (openssl rand -base64 48), the three DB passwords, SMTP, Turnstile, TENANT_NAME, admin
# first start: Postgres creates the roles, "migrate" runs Flyway (owner login) and exits,
# then the backend starts with the application login
docker compose -f infra/docker-compose.prod.example.yml --env-file infra/.env.prod up -d --build
docker compose -f infra/docker-compose.prod.example.yml --env-file infra/.env.prod ps   # all healthy
curl -fsS https://api.example-barber.de/actuator/health/readiness
```

- Caddy gets the certificates automatically once DNS points to the server.
- Only ports 80/443 are open. Postgres and Redis sit on an internal network.
- After the first login, remove `APP_ADMIN_PASSWORD` from `.env.prod` and run `up -d` again.

**Images from CI instead of building on the server:** set `BACKEND_IMAGE` / `FRONTEND_IMAGE` to the registry tags.
Then `docker login <registry>` and
`docker compose -f infra/docker-compose.prod.example.yml --env-file infra/.env.prod pull && … up -d`.

**Backups (cron, nightly):**

```bash
# /etc/cron.d/barbershop-backup
15 2 * * * deploy cd /home/deploy/barbershop && docker compose -f infra/docker-compose.prod.example.yml --env-file infra/.env.prod exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -Fc "$POSTGRES_DB"' > infra/backups/barbershop-$(date +\%F).dump && find infra/backups -name '*.dump' -mtime +14 -delete
```

Copy `infra/backups` off the server every night (e.g. `restic` or `rclone` to a storage box / S3, encrypted). The
provider's server snapshots are a useful extra but not a database backup.

Restore:

```bash
docker compose … exec -T postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists' < infra/backups/barbershop-2026-10-18.dump
```

**Updates:** `git pull && docker compose … up -d --build`, or new image tags + `pull` + `up -d`. Refresh the base
images monthly (`--pull`).

**Monitoring:** an external uptime check (e.g. UptimeRobot, Better Stack) on `/actuator/health/readiness`, plus
`docker compose … logs -f backend` (JSON lines).

## Costs

Rough monthly estimates, pay-as-you-go, Germany West Central, without VAT, as of 2026. Check the
[Azure pricing calculator](https://azure.microsoft.com/pricing/calculator/) before deciding: prices change and the
free grants depend on the subscription.

**Small: one shop, `env/dev.tfvars`-sized production**

| Item | Size | ≈ € / month |
|---|---|---|
| Container Apps | backend 0.5 vCPU / 1 GiB always on, frontend 0.5 / 1 GiB, mostly idle (monthly free grant included) | 10–25 |
| Postgres Flexible Server | Burstable B1ms, 32 GiB, 7-day PITR | 15–20 |
| Container Registry | Basic | ~5 |
| Log Analytics | < 5 GB/month (daily cap 0.5–2 GB) | 0–10 |
| Application Insights availability test | 3 locations every 15 min (≈ 8,600 runs) | ~10 |
| Key Vault, private DNS, VNet, ACS Email | | 1–3 |
| **Total** | | **≈ 45–70** |

**Larger: `env/prod.tfvars` (HA)**

| Item | Size | ≈ € / month |
|---|---|---|
| Container Apps | backend 1 vCPU / 2 GiB, frontend up to 3 replicas, zone-redundant | 60–120 |
| Postgres Flexible Server | General Purpose D2ds_v5 + zone-redundant standby, 64 GiB, 35-day PITR, geo backups | 300–380 |
| Redis (only for > 1 backend replica) | Azure Cache for Redis Basic C0 → Standard C1 | 15–100 |
| Log Analytics + App Insights (test every 5 min) | | 40–70 |
| ACR, Key Vault, DNS, ACS | | 6–10 |
| **Total** | | **≈ 420–680** |

Most of the larger setup is the HA database. Without HA (one D2ds_v5) it is about 150 € / month less.

**VPS:** server 5–15 € + backups / storage box 3–5 € + domain ~1 € → **≈ 10–20 € / month**. You run the updates,
backups and monitoring yourself.
