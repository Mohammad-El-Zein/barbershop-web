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
    condition     = length([for e in azurerm_container_app.backend.template[0].container[0].env : e if e.name == "DB_PASSWORD" && e.secret_name == "db-app-password" && e.value == null]) == 1
    error_message = "The backend's DB password is the application role's, from Key Vault — never a plain value."
  }
  assert {
    condition     = [for e in azurerm_container_app.backend.template[0].container[0].env : e.value if e.name == "DB_USERNAME"][0] == "barbershop_app" && [for e in azurerm_container_app.backend.template[0].container[0].env : e.value if e.name == "APP_DB_MIGRATE"][0] == "false"
    error_message = "The backend connects as the DML-only application role and does not migrate."
  }
  assert {
    condition     = length([for e in azurerm_container_app.backend.template[0].container[0].env : e if startswith(e.name, "DB_MIGRATION") || e.name == "PGPASSWORD"]) == 0 && !contains([for s in azurerm_container_app.backend.secret : s.name], "db-admin-password") && !contains([for s in azurerm_container_app.backend.secret : s.name], "db-migration-password")
    error_message = "The long-running backend must not know the owner or admin login."
  }
  assert {
    condition     = [for e in azurerm_container_app_job.migrate.template[0].container[0].env : e.value if e.name == "APP_MIGRATE_ONLY"][0] == "true" && [for e in azurerm_container_app_job.migrate.template[0].container[0].env : e.value if e.name == "DB_MIGRATION_USERNAME"][0] == "barbershop_migrator"
    error_message = "Migrations run in the separate job with the owner role."
  }
  assert {
    condition     = strcontains([for e in azurerm_container_app_job.db_init.template[0].container[0].env : e.value if e.name == "INIT_SQL"][0], "rolsuper OR rolreplication OR rolbypassrls OR rolcreatedb OR rolcreaterole")
    error_message = "The db-init job runs the shared role script."
  }
  assert {
    condition     = contains(split(",", upper(azurerm_postgresql_flexible_server_configuration.extensions.value)), "BTREE_GIST")
    error_message = "The migrations create btree_gist: it must be allow-listed (azure.extensions)."
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

# Container Apps rewrites "$$" → "$" and "$(NAME)" in commands, args and env values (like
# Kubernetes) — that broke the DO $$ block of db-init once. And job logs must never show a secret.
run "jobs_survive_dollar_rewriting_and_print_no_secrets" {
  command = apply

  variables {
    allow_missing_captcha = true
    smtp_password         = "smtp-secret"
  }

  assert {
    condition = alltrue([
      for c in [
        azurerm_container_app_job.db_init.template[0].container[0],
        azurerm_container_app_job.migrate.template[0].container[0],
        azurerm_container_app.backend.template[0].container[0],
        azurerm_container_app.frontend.template[0].container[0],
        ] : alltrue([
          for s in concat(c.command == null ? [] : c.command, c.args == null ? [] : c.args, [for e in c.env : e.value if e.value != null]) :
          !strcontains(s, "$$") && !strcontains(s, "$(")
      ])
    ])
    error_message = "No \"$$\" or \"$(\" in any command, args or env value: Container Apps rewrites them."
  }
  assert {
    condition     = !strcontains([for e in azurerm_container_app_job.db_init.template[0].container[0].env : e.value if e.name == "INIT_SQL"][0], "$")
    error_message = "The role script (INIT_SQL) must not contain \"$\" at all (no dollar quoting, no DO block)."
  }
  assert {
    condition = length(regexall("(?i)(set_config|current_setting|\\\\echo[^\\n]*password|\\\\qecho|\\\\set echo (all|queries)|raise notice[^\\n]*password)",
    [for e in azurerm_container_app_job.db_init.template[0].container[0].env : e.value if e.name == "INIT_SQL"][0])) == 0
    error_message = "The role script must not print passwords (no set_config/current_setting rows, no echo of them)."
  }
  assert {
    condition = alltrue([
      for s in azurerm_container_app_job.db_init.template[0].container[0].command :
      !strcontains(s, "set -x") && length(regexall("(?i)password=|echo ", s)) == 0
    ]) && strcontains(join(" ", azurerm_container_app_job.db_init.template[0].container[0].command), "psql -X -q -v ON_ERROR_STOP=1")
    error_message = "db-init: quiet psql that stops on errors; no passwords on the command line, no set -x / echo."
  }
  assert {
    condition = length([
      for e in azurerm_container_app_job.db_init.template[0].container[0].env : e
      if length(regexall("PASSWORD", e.name)) > 0 && (e.value != null || e.secret_name == null)
    ]) == 0
    error_message = "db-init: every password env var comes from a Key Vault secret reference."
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
  assert {
    condition     = output.ci_variables.NEXT_PUBLIC_SHOW_DEMO_BANNER == "false"
    error_message = "prod: frontend builds hide the DEMO strip."
  }
  assert {
    condition = alltrue([
      for c in [azurerm_container_app.backend.template[0].container[0], azurerm_container_app_job.migrate.template[0].container[0]] :
      [for e in c.env : e.value if e.name == "PAYMENT_DEMO_ALLOW_IN_PROD"][0] == "false"
    ])
    error_message = "prod: demo payments stay forbidden → the backend refuses to start without a real payment provider."
  }
}

# dev: the prod profile with the demo payment provider must be allowed explicitly, in the
# backend AND in the migrate job (both run the backend's start-up checks)
run "dev_may_run_demo_payments" {
  command = apply

  variables {
    allow_missing_captcha      = true
    payment_demo_allow_in_prod = true
  }

  assert {
    condition = alltrue([
      for c in [azurerm_container_app.backend.template[0].container[0], azurerm_container_app_job.migrate.template[0].container[0]] :
      [for e in c.env : e.value if e.name == "PAYMENT_PROVIDER"][0] == "demo" && [for e in c.env : e.value if e.name == "PAYMENT_DEMO_ALLOW_IN_PROD"][0] == "true"
    ])
    error_message = "dev: backend and migrate job allow the demo payment provider."
  }
  assert {
    condition     = [for e in azurerm_container_app.backend.template[0].container[0].env : e.value if e.name == "SPRING_PROFILES_ACTIVE"][0] == "prod"
    error_message = "Allowing demo payments does not switch on the demo seed."
  }
}

run "prod_refuses_demo_payments" {
  command = plan

  variables {
    environment                = "prod"
    captcha_secret             = "turnstile-secret"
    payment_demo_allow_in_prod = true
  }

  expect_failures = [var.payment_demo_allow_in_prod]
}

run "mail_dead_alert_can_be_switched_off" {
  command = apply

  variables {
    allow_missing_captcha   = true
    alert_email             = "ops@example.test"
    mail_dead_alert_enabled = false
  }

  assert {
    condition     = length(azurerm_monitor_scheduled_query_rules_alert_v2.mail_dead) == 0 && length(azurerm_monitor_metric_alert.api_down) == 1 && length(azurerm_monitor_action_group.ops) == 1
    error_message = "mail_dead_alert_enabled = false removes only the mail alert; the availability alert stays."
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
  assert {
    condition     = output.ci_variables.NEXT_PUBLIC_SHOW_DEMO_BANNER == "true"
    error_message = "Public demo: the DEMO strip stays on."
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
