# Azure infrastructure (Terraform) — not applied

Everything the production setup needs on Azure, as code. **Nothing here has been applied to a real subscription.**
It is formatted, validated and tested offline (`terraform test` with mocked providers). The step-by-step rollout is
in [`docs/DEPLOYMENT.md`](../../docs/DEPLOYMENT.md).

```
Internet ──HTTPS──▶ Container Apps environment (VNet subnet, managed certificates)
                     ├─ ca-<prefix>-<env>-web   Next.js       :3000  (min 0-1, max 3)
                     └─ ca-<prefix>-<env>-api   Spring Boot   :8080  (min 1, max 1 — more needs Redis)
                              │  user-assigned identity: AcrPull + Key Vault Secrets User
                              ├──▶ Postgres Flexible Server (delegated subnet, private DNS, TLS ≥ 1.2, PITR backups)
                              ├──▶ Key Vault (RBAC): DB / JWT / link-token / SMTP / CAPTCHA / admin secrets
                              └──▶ SMTP (any provider, or Azure Communication Services Email)
Logs: Log Analytics (+ Application Insights, availability test, alerts)   Images: Azure Container Registry
```

| File | Contents |
|---|---|
| `versions.tf` | Terraform ≥ 1.9, azurerm `~> 4.50` (locked: 4.81.0), random |
| `variables.tf` | All inputs with descriptions and validation |
| `main.tf` | Resource group, naming, managed identity, ACR, Log Analytics, App Insights, availability test, alerts |
| `network.tf` | VNet, Container Apps subnet, delegated Postgres subnet, private DNS zone |
| `postgres.tf` | Flexible Server (size / zone / HA / backups as variables), database, TLS setting, optional firewall rules |
| `keyvault.tf` | Key Vault (RBAC, purge protection in prod), generated and optional secrets |
| `email.tf` | Optional Azure Communication Services Email (`email_mode = "acs"`) |
| `containerapps.tf` | Environment, backend + frontend apps (Key Vault secret references, probes, scaling), custom domains |
| `outputs.tf` | URLs, names for CI variables, DNS verification id, … |
| `env/dev.tfvars`, `env/prod.tfvars` | Environment sizes (no secrets) |
| `tests/*.tftest.hcl` | Offline tests with mocked providers |
| `backend.tf.example`, `backend.hcl.example` | Remote state in Azure Storage |

## Offline checks (no Azure account needed)

```bash
cd infra/azure
terraform fmt -recursive -check
terraform init -backend=false
terraform validate
terraform test                                    # 6 scenario runs (dev, prod, public demo, guard rails)
terraform test -filter=tests/tfvars.tftest.hcl -var-file=env/prod.tfvars \
  -var subscription_id=00000000-0000-4000-8000-000000000004 -var captcha_secret=x
```

The tests "apply" the whole configuration against mocked providers and check, among other things:

- Postgres is VNet-private and the ACR admin user is off.
- Both apps are HTTPS-only, and the DB password is a Key Vault reference.
- prod has zone-redundant HA with 35-day PITR plus geo backups, and purge protection.
- CORS is exactly the site origin.
- A public demo runs `prod,demo` with demo payments allowed explicitly.
- These are refused: more than one backend replica without Redis, prod without CAPTCHA, a backend that scales to zero.

The same commands run in CI (`terraform` job in both pipelines). A real `terraform plan` needs an Azure login.

## Remote state

Terraform state contains the generated secrets, so keep it in a locked-down storage account, never in git:

```bash
az group create -n rg-barbershop-tfstate -l germanywestcentral
az storage account create -n stbarbershoptfstate -g rg-barbershop-tfstate -l germanywestcentral \
  --sku Standard_ZRS --min-tls-version TLS1_2 --allow-blob-public-access false --allow-shared-key-access false
az storage container create -n tfstate --account-name stbarbershoptfstate --auth-mode login
az role assignment create --role "Storage Blob Data Contributor" --assignee <your object id> \
  --scope $(az storage account show -n stbarbershoptfstate -g rg-barbershop-tfstate --query id -o tsv)
# versioning = state history / undo
az storage account blob-service-properties update -n stbarbershoptfstate -g rg-barbershop-tfstate --enable-versioning true

cp backend.tf.example backend.tf
cp backend.hcl.example backend.hcl        # one key per environment, e.g. barbershop-dev.tfstate
terraform init -backend-config=backend.hcl
```

The blob lease locks the state, so two `apply` runs never interleave. For several environments, use one `key` each
(or one `backend.hcl` per environment).

## Secrets

Never put secrets in `.tfvars`; pass them as environment variables:

```bash
export TF_VAR_smtp_password=…        # SMTP / ACS
export TF_VAR_captcha_secret=…       # Cloudflare Turnstile secret key (prod)
export TF_VAR_admin_password=…       # first start only; unset afterwards and apply again
export TF_VAR_rate_limit_redis_url=… # only with backend_max_replicas > 1
```

The three DB logins (admin, owner `barbershop_migrator`, application `barbershop_app`) and the JWT and link-token secrets are generated (`random_password`) and stored in Key Vault. The backend app receives only the application login; the owner login goes only to the `migrate` job, the admin only to `db-init` (`dbjobs.tf`). The
apps read them through Key Vault references with their managed identity. To rotate one, taint it (e.g.
`terraform apply -replace=random_password.jwt`) and restart the backend. A new JWT secret logs everyone out, and a
new link-token secret invalidates the email links that are still open.

## Custom domains

1. `terraform output custom_domain_verification_id` and `terraform output frontend_default_fqdn` / `backend_default_fqdn`.
2. At the DNS provider:
   - `CNAME www → <frontend_default_fqdn>` and `TXT asuid.www → <verification id>`;
   - the same for `api` with the backend FQDN.
3. Set `site_domain` / `api_domain` and run `terraform apply`. This attaches the host names and switches CORS and
   the email link base URL.
4. Free managed certificate (not supported by azurerm yet):
   ```bash
   az containerapp hostname bind -g <rg> -n <frontend app> --hostname www.example-barber.de \
     --environment <container_app_environment> --validation-method CNAME
   az containerapp hostname bind -g <rg> -n <backend app> --hostname api.example-barber.de \
     --environment <container_app_environment> --validation-method CNAME
   ```
   Azure renews the certificates automatically.
5. Rebuild the frontend image with the new `NEXT_PUBLIC_SITE_URL` / `NEXT_PUBLIC_API_URL` and deploy it.
