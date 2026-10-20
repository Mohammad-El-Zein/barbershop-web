# ── general ──────────────────────────────────────────────────────────────────

variable "subscription_id" {
  description = "Azure subscription id."
  type        = string
}

variable "environment" {
  description = "Environment name: dev or prod (part of every resource name)."
  type        = string
  validation {
    condition     = contains(["dev", "prod"], var.environment)
    error_message = "environment must be \"dev\" or \"prod\"."
  }
}

variable "location" {
  description = "Azure region. germanywestcentral (Frankfurt) keeps data in Germany; westeurope is an alternative."
  type        = string
  default     = "germanywestcentral"
}

variable "name_prefix" {
  description = "Short prefix for resource names (lowercase letters, digits, dashes)."
  type        = string
  default     = "barbershop"
  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,13}[a-z0-9]$", var.name_prefix))
    error_message = "name_prefix: 3-15 characters, lowercase letters, digits and dashes, starting with a letter."
  }
}

variable "tags" {
  description = "Extra tags for all resources."
  type        = map(string)
  default     = {}
}

# ── shop / domains ───────────────────────────────────────────────────────────

variable "tenant_slug" {
  description = "Shop slug (APP_ADMIN_TENANT; also NEXT_PUBLIC_TENANT of the frontend image)."
  type        = string
  default     = "the-barber-news"
}

variable "tenant_name" {
  description = "Shop name, used to create the shop on the first start of an empty database (no demo seed in production)."
  type        = string
}

variable "tenant_timezone" {
  description = "IANA time zone of the shop."
  type        = string
  default     = "Europe/Berlin"
}

variable "site_domain" {
  description = "Custom domain of the website, e.g. www.example-barber.de. Empty = the Container Apps default domain (README.md → Custom domains)."
  type        = string
  default     = ""
}

variable "api_domain" {
  description = "Custom domain of the API, e.g. api.example-barber.de. Empty = the Container Apps default domain."
  type        = string
  default     = ""
}

variable "public_demo" {
  description = "Public DEMO deployment: demo shop seed + demo payments allowed in prod (DEMO bar on every page). Never for a real shop."
  type        = bool
  default     = false
}

variable "payment_demo_allow_in_prod" {
  description = "Let the backend (prod profile) start with the demo payment provider (PAYMENT_DEMO_ALLOW_IN_PROD): simulated payments only. For dev; never in the prod environment."
  type        = bool
  default     = false
  validation {
    condition     = !(var.payment_demo_allow_in_prod && var.environment == "prod")
    error_message = "payment_demo_allow_in_prod must not be true in the prod environment: a real shop needs a real payment provider."
  }
}

# ── container apps ───────────────────────────────────────────────────────────

variable "backend_image" {
  description = "Initial backend image (<acr>.azurecr.io/barbershop-backend:<tag>). Later releases are rolled out by CI; Terraform ignores image changes."
  type        = string
}

variable "frontend_image" {
  description = "Initial frontend image (built with this environment's NEXT_PUBLIC_* values)."
  type        = string
}

variable "backend_cpu" {
  description = "vCPU per backend replica (0.5 → 1Gi, 1 → 2Gi memory)."
  type        = number
  default     = 0.5
}

variable "backend_memory" {
  description = "Memory per backend replica, must match the CPU (e.g. 1Gi for 0.5 vCPU)."
  type        = string
  default     = "1Gi"
}

variable "backend_min_replicas" {
  description = "At least 1: the backend runs the scheduled jobs (expiry, reminders, mail outbox, retention)."
  type        = number
  default     = 1
  validation {
    condition     = var.backend_min_replicas >= 1
    error_message = "backend_min_replicas must be >= 1 (scheduled jobs and the mail outbox run in the backend)."
  }
}

variable "backend_max_replicas" {
  description = "More than 1 needs rate_limit_redis_url (shared rate limits)."
  type        = number
  default     = 1
}

variable "frontend_cpu" {
  type    = number
  default = 0.5
}

variable "frontend_memory" {
  type    = string
  default = "1Gi"
}

variable "frontend_min_replicas" {
  description = "0 allows scale to zero (cheaper, but the first request after idle is slow)."
  type        = number
  default     = 1
}

variable "frontend_max_replicas" {
  type    = number
  default = 3
}

variable "zone_redundant" {
  description = "Spread the Container Apps environment over availability zones (prod)."
  type        = bool
  default     = false
}

# ── network ──────────────────────────────────────────────────────────────────

variable "vnet_cidr" {
  type    = string
  default = "10.40.0.0/16"
}

variable "aca_subnet_cidr" {
  description = "Container Apps environment subnet (workload profiles: at least /27)."
  type        = string
  default     = "10.40.0.0/23"
}

variable "postgres_subnet_cidr" {
  description = "Delegated subnet of the Postgres Flexible Server (private access)."
  type        = string
  default     = "10.40.2.0/24"
}

# ── postgres ─────────────────────────────────────────────────────────────────

variable "postgres_version" {
  type    = string
  default = "17"
}

variable "postgres_sku" {
  description = "B_Standard_B1ms (dev, ~13 €/month) … GP_Standard_D2ds_v5 (prod with HA)."
  type        = string
  default     = "B_Standard_B1ms"
}

variable "postgres_storage_mb" {
  type    = number
  default = 32768
}

variable "postgres_backup_retention_days" {
  description = "Point-in-time restore window, 7-35 days."
  type        = number
  default     = 7
  validation {
    condition     = var.postgres_backup_retention_days >= 7 && var.postgres_backup_retention_days <= 35
    error_message = "postgres_backup_retention_days must be between 7 and 35."
  }
}

variable "postgres_geo_redundant_backup" {
  description = "Copy backups to the paired region (can only be set when the server is created)."
  type        = bool
  default     = false
}

variable "postgres_zone" {
  type    = string
  default = "1"
}

variable "postgres_high_availability" {
  description = "Zone-redundant standby (not available for Burstable SKUs; doubles the server cost)."
  type        = bool
  default     = false
}

variable "postgres_standby_zone" {
  type    = string
  default = "2"
}

variable "postgres_public_access" {
  description = "false (recommended) = private VNet access only. true = public endpoint with the firewall rules below (e.g. no VNet allowed)."
  type        = bool
  default     = false
}

variable "postgres_firewall_ips" {
  description = "Public mode only: name → IPv4 allowed to connect (admin workstation, CI runner)."
  type        = map(string)
  default     = {}
}

# ── email ────────────────────────────────────────────────────────────────────

variable "email_mode" {
  description = "smtp = any SMTP provider (smtp_* variables); acs = also create Azure Communication Services Email with an Azure-managed sender domain (SMTP credentials still come from smtp_username / smtp_password, README.md → Email)."
  type        = string
  default     = "smtp"
  validation {
    condition     = contains(["smtp", "acs"], var.email_mode)
    error_message = "email_mode must be \"smtp\" or \"acs\"."
  }
}

variable "smtp_host" {
  description = "SMTP server (ACS: smtp.azurecomm.net)."
  type        = string
}

variable "smtp_port" {
  type    = number
  default = 587
}

variable "smtp_username" {
  type    = string
  default = ""
}

variable "smtp_password" {
  description = "Set via TF_VAR_smtp_password — never in a .tfvars file."
  type        = string
  default     = ""
  sensitive   = true
}

variable "mail_from" {
  description = "Sender, e.g. \"The Barber News <termine@example-barber.de>\" (ACS: DoNotReply@<azure managed domain>)."
  type        = string
}

# ── secrets / security ───────────────────────────────────────────────────────

variable "admin_email" {
  description = "First admin, created on first start. Empty = none."
  type        = string
  default     = ""
}

variable "admin_password" {
  description = "First admin's password (>= 12 chars). Set via TF_VAR_admin_password; remove it after the first start."
  type        = string
  default     = ""
  sensitive   = true
}

variable "captcha_secret" {
  description = "Cloudflare Turnstile secret key (TF_VAR_captcha_secret). Empty = no CAPTCHA, only allowed with allow_missing_captcha."
  type        = string
  default     = ""
  sensitive   = true
}

variable "allow_missing_captcha" {
  description = "Start the prod profile without CAPTCHA (dev environments)."
  type        = bool
  default     = false
}

variable "rate_limit_redis_url" {
  description = "redis(s):// URL for shared rate limits (needed with backend_max_replicas > 1). TF_VAR_rate_limit_redis_url."
  type        = string
  default     = ""
  sensitive   = true
}

variable "ci_principal_id" {
  description = "Object id of the CI service principal (OIDC). Gets AcrPush on the registry and Contributor on the resource group. Empty = none."
  type        = string
  default     = ""
}

# ── monitoring ───────────────────────────────────────────────────────────────

variable "log_retention_days" {
  type    = number
  default = 30
}

variable "alert_email" {
  description = "Receives availability and mail-delivery alerts. Empty = no alerts."
  type        = string
  default     = ""
}

variable "mail_dead_alert_enabled" {
  description = "Create the log alert for undeliverable emails (needs alert_email). false when creating it fails with \"insufficient access\" (docs/DEPLOYMENT.md → Monitoring)."
  type        = bool
  default     = true
}
