# dev: smallest sizes, no HA, no CAPTCHA required. Secrets via environment variables:
#   export TF_VAR_smtp_password=… TF_VAR_admin_password=… (TF_VAR_captcha_secret optional)
# terraform plan -var-file=env/dev.tfvars -var subscription_id=<id>

environment = "dev"
location    = "germanywestcentral"

tenant_slug     = "the-barber-news"
tenant_name     = "The Barber News"
tenant_timezone = "Europe/Berlin"

# empty = *.azurecontainerapps.io default domains
site_domain = ""
api_domain  = ""

# initial images (README.md → First deployment); CI rolls out later tags
backend_image  = "acrbarbershopdevxxxx.azurecr.io/barbershop-backend:sha-0000000"
frontend_image = "acrbarbershopdevxxxx.azurecr.io/barbershop-frontend:sha-0000000"

backend_cpu           = 0.5
backend_memory        = "1Gi"
frontend_min_replicas = 0 # scale to zero when idle (slow first request)
frontend_max_replicas = 2

postgres_sku                   = "B_Standard_B1ms"
postgres_storage_mb            = 32768
postgres_backup_retention_days = 7

email_mode = "smtp"
smtp_host  = "smtp.example-mail-provider.com"
mail_from  = "The Barber News (dev) <dev@example-barber.de>"

admin_email           = "owner@example-barber.de"
allow_missing_captcha = true

# dev runs the prod profile with the DEMO payment provider: simulated payments only, no real money
payment_demo_allow_in_prod = true

log_retention_days = 30
alert_email        = ""
# creating the mail log alert fails here with "insufficient access" (docs/DEPLOYMENT.md → Monitoring)
mail_dead_alert_enabled = false
