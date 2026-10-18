# Offline checks with mocked providers — no Azure account, nothing is created:
#   terraform init -backend=false && terraform test
# Each run "applies" the whole configuration against mocks (computed values are fake)
# and asserts the security-relevant settings and the guard rails.

mock_provider "azurerm" {
  source = "./tests/mocks"
}

mock_provider "random" {
  source = "./tests/mocks"
}

variables {
  subscription_id = "00000000-0000-4000-8000-000000000004"
  environment     = "dev"
  tenant_name     = "The Barber News"
  backend_image   = "acrbarbershopdev0000.azurecr.io/barbershop-backend:sha-0000000"
  frontend_image  = "acrbarbershopdev0000.azurecr.io/barbershop-frontend:sha-0000000"
  smtp_host       = "smtp.example.test"
  mail_from       = "Shop <shop@example.test>"
}

run "dev_defaults_are_private_and_safe" {
  command = apply

  variables {
    allow_missing_captcha = true
    smtp_password         = "smtp-secret"
  }

  assert {
    condition     = azurerm_postgresql_flexible_server.this.public_network_access_enabled == false && length(azurerm_subnet.postgres) == 1
    error_message = "Postgres must be VNet-private by default."
  }
  assert {
    condition     = azurerm_container_registry.this.admin_enabled == false
    error_message = "ACR admin user must stay disabled (managed identity pull)."
  }
  assert {
    condition     = azurerm_key_vault.this.rbac_authorization_enabled && azurerm_key_vault.this.purge_protection_enabled == false
    error_message = "Key Vault: RBAC on; purge protection only in prod."
  }
  assert {
    condition     = azurerm_container_app.backend.ingress[0].allow_insecure_connections == false && azurerm_container_app.frontend.ingress[0].allow_insecure_connections == false
    error_message = "Both apps must be HTTPS-only."
  }
  assert {
    condition     = [for e in azurerm_container_app.backend.template[0].container[0].env : e.value if e.name == "SPRING_PROFILES_ACTIVE"][0] == "prod"
    error_message = "A non-demo deployment runs the prod profile only (no demo seed)."
  }
  assert {
    condition     = [for e in azurerm_container_app.backend.template[0].container[0].env : e.value if e.name == "PAYMENT_DEMO_ALLOW_IN_PROD"][0] == "false"
    error_message = "Demo payments must not be allowed in prod by default."
  }
  assert {
    condition     = [for e in azurerm_container_app.backend.template[0].container[0].env : e.value if e.name == "APP_RATE_LIMIT_STORE"][0] == "memory"
    error_message = "One replica without Redis → in-memory rate limits."
  }
  assert {
    condition     = length([for e in azurerm_container_app.backend.template[0].container[0].env : e if e.name == "DB_PASSWORD" && e.secret_name == "db-password" && e.value == null]) == 1
    error_message = "The DB password must come from a Key Vault-backed secret, never a plain value."
  }
  assert {
    condition     = contains(keys(azurerm_key_vault_secret.optional), "smtp-password") && !contains(keys(azurerm_key_vault_secret.optional), "captcha-secret")
    error_message = "Optional secrets are created only when set."
  }
  assert {
    condition     = length(azurerm_container_app_custom_domain.frontend) == 0 && length(azurerm_monitor_metric_alert.api_down) == 0
    error_message = "No custom domains / alerts unless configured."
  }
}

run "prod_is_highly_available_with_domains_and_alerts" {
  command = apply

  variables {
    environment                    = "prod"
    site_domain                    = "www.example-barber.de"
    api_domain                     = "api.example-barber.de"
    captcha_secret                 = "turnstile-secret"
    zone_redundant                 = true
    postgres_sku                   = "GP_Standard_D2ds_v5"
    postgres_high_availability     = true
    postgres_backup_retention_days = 35
    postgres_geo_redundant_backup  = true
    alert_email                    = "it@example-barber.de"
  }

  assert {
    condition     = length(azurerm_postgresql_flexible_server.this.high_availability) == 1 && azurerm_postgresql_flexible_server.this.high_availability[0].mode == "ZoneRedundant"
    error_message = "prod: zone-redundant Postgres HA."
  }
  assert {
    condition     = azurerm_postgresql_flexible_server.this.backup_retention_days == 35 && azurerm_postgresql_flexible_server.this.geo_redundant_backup_enabled
    error_message = "prod: 35-day PITR with geo-redundant backups."
  }
  assert {
    condition     = azurerm_key_vault.this.purge_protection_enabled
    error_message = "prod: Key Vault purge protection."
  }
  assert {
    condition     = output.site_url == "https://www.example-barber.de" && output.api_url == "https://api.example-barber.de"
    error_message = "Custom domains drive the public URLs."
  }
  assert {
    condition     = [for e in azurerm_container_app.backend.template[0].container[0].env : e.value if e.name == "APP_CORS_ALLOWED_ORIGINS"][0] == "https://www.example-barber.de"
    error_message = "CORS allows exactly the website origin."
  }
  assert {
    condition     = [for e in azurerm_container_app.backend.template[0].container[0].env : e.value if e.name == "APP_CAPTCHA_PROVIDER"][0] == "turnstile"
    error_message = "prod with a Turnstile secret → CAPTCHA on."
  }
  assert {
    condition     = length(azurerm_container_app_custom_domain.frontend) == 1 && length(azurerm_container_app_custom_domain.backend) == 1
    error_message = "Both custom domains are attached."
  }
  assert {
    condition     = length(azurerm_monitor_metric_alert.api_down) == 1 && length(azurerm_monitor_scheduled_query_rules_alert_v2.mail_dead) == 1
    error_message = "Alerts exist when alert_email is set."
  }
}

run "public_demo_runs_the_demo_seed_and_allows_demo_payments" {
  command = apply

  variables {
    public_demo           = true
    allow_missing_captcha = true
  }

  assert {
    condition     = [for e in azurerm_container_app.backend.template[0].container[0].env : e.value if e.name == "SPRING_PROFILES_ACTIVE"][0] == "prod,demo"
    error_message = "Public demo: prod hardening plus the demo seed."
  }
  assert {
    condition     = [for e in azurerm_container_app.backend.template[0].container[0].env : e.value if e.name == "PAYMENT_DEMO_ALLOW_IN_PROD"][0] == "true"
    error_message = "Public demo: demo payments explicitly allowed."
  }
}

run "several_backend_replicas_need_redis" {
  command = plan

  variables {
    allow_missing_captcha = true
    backend_max_replicas  = 3
  }

  expect_failures = [azurerm_container_app.backend]
}

run "prod_without_captcha_is_refused" {
  command = plan

  variables {
    environment = "prod"
  }

  expect_failures = [azurerm_container_app.backend]
}

run "backend_never_scales_to_zero" {
  command = plan

  variables {
    allow_missing_captcha = true
    backend_min_replicas  = 0
  }

  expect_failures = [var.backend_min_replicas]
}
