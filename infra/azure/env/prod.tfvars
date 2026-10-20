# prod: zone-redundant environment, HA Postgres with 35-day PITR and geo-redundant backups.
# Secrets via environment variables (never in this file):
#   TF_VAR_smtp_password, TF_VAR_admin_password (first start only), TF_VAR_captcha_secret
# terraform plan -var-file=env/prod.tfvars -var subscription_id=<id>

environment = "prod"
location    = "germanywestcentral"

tenant_slug     = "the-barber-news"
tenant_name     = "The Barber News"
tenant_timezone = "Europe/Berlin"

web_domain = "www.example-barber.de"
api_domain = "api.example-barber.de"

backend_image  = "acrbarbershopprodxxxx.azurecr.io/barbershop-backend:1.0.0"
frontend_image = "acrbarbershopprodxxxx.azurecr.io/barbershop-frontend:1.0.0"

backend_cpu           = 1
backend_memory        = "2Gi"
backend_min_replicas  = 1
backend_max_replicas  = 1 # > 1 needs rate_limit_redis_url (TF_VAR_rate_limit_redis_url)
frontend_cpu          = 0.5
frontend_memory       = "1Gi"
frontend_min_replicas = 1
frontend_max_replicas = 3
zone_redundant        = true

postgres_sku                   = "GP_Standard_D2ds_v5"
postgres_storage_mb            = 65536
postgres_backup_retention_days = 35
postgres_geo_redundant_backup  = true
postgres_high_availability     = true
postgres_zone                  = "1"
postgres_standby_zone          = "2"

email_mode = "smtp"
smtp_host  = "smtp.example-mail-provider.com"
mail_from  = "The Barber News <termine@example-barber.de>"

admin_email = "owner@example-barber.de"

log_retention_days = 90
alert_email        = "it@example-barber.de"
