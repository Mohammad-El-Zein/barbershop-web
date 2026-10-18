# Container Apps environment (VNet-integrated, logs → Log Analytics) with the two apps.
# HTTPS: Container Apps terminates TLS (managed certificates); plain HTTP is redirected.
# Images: Terraform sets the initial image only; CI rolls out new tags with
# `az containerapp update --image` (ignore_changes below keeps Terraform from reverting them).

resource "azurerm_container_app_environment" "this" {
  name                               = "cae-${local.name}"
  location                           = azurerm_resource_group.this.location
  resource_group_name                = azurerm_resource_group.this.name
  log_analytics_workspace_id         = azurerm_log_analytics_workspace.this.id
  infrastructure_subnet_id           = azurerm_subnet.aca.id
  infrastructure_resource_group_name = "rg-${local.name}-aca-infra"
  internal_load_balancer_enabled     = false
  zone_redundancy_enabled            = var.zone_redundant
  tags                               = local.tags

  workload_profile {
    name                  = "Consumption"
    workload_profile_type = "Consumption"
  }
}

locals {
  default_domain = azurerm_container_app_environment.this.default_domain
  site_url       = var.site_domain != "" ? "https://${var.site_domain}" : "https://${local.frontend_name}.${local.default_domain}"
  api_url        = var.api_domain != "" ? "https://${var.api_domain}" : "https://${local.backend_name}.${local.default_domain}"

  captcha_on = local.optional_secret_set["captcha-secret"]
  redis_on   = local.optional_secret_set["redis-url"]

  # Key Vault secret → Container Apps secret name (same name)
  backend_secret_names = concat(
    ["db-password", "jwt-secret", "token-secret", "appi-conn"],
    tolist(local.optional_secrets),
  )
  secret_ids = merge(
    { for k, s in azurerm_key_vault_secret.generated : k => s.versionless_id },
    { for k, s in azurerm_key_vault_secret.optional : k => s.versionless_id },
  )

  backend_env = {
    SPRING_PROFILES_ACTIVE         = var.public_demo ? "prod,demo" : "prod"
    DB_URL                         = "jdbc:postgresql://${azurerm_postgresql_flexible_server.this.fqdn}:5432/${azurerm_postgresql_flexible_server_database.app.name}?sslmode=require"
    DB_USERNAME                    = azurerm_postgresql_flexible_server.this.administrator_login
    APP_FRONTEND_BASE_URL          = local.site_url
    APP_CORS_ALLOWED_ORIGINS       = local.site_url
    APP_COOKIE_SECURE              = "true"
    APP_ADMIN_EMAIL                = var.admin_email
    APP_ADMIN_TENANT               = var.tenant_slug
    APP_TENANT_NAME                = var.tenant_name
    APP_TENANT_TIMEZONE            = var.tenant_timezone
    SMTP_HOST                      = var.smtp_host
    SMTP_PORT                      = tostring(var.smtp_port)
    SMTP_USERNAME                  = var.smtp_username
    SMTP_AUTH                      = var.smtp_username != "" ? "true" : "false"
    SMTP_STARTTLS                  = "true"
    APP_MAIL_FROM                  = var.mail_from
    APP_CAPTCHA_PROVIDER           = local.captcha_on ? "turnstile" : "none"
    APP_CAPTCHA_ALLOW_NONE_IN_PROD = local.captcha_on ? "false" : tostring(var.allow_missing_captcha)
    APP_RATE_LIMIT_STORE           = local.redis_on ? "redis" : "memory"
    PAYMENT_PROVIDER               = "demo"
    PAYMENT_DEMO_ALLOW_IN_PROD     = tostring(var.public_demo)
    LOG_FORMAT                     = "ecs"
  }
  # env var → Container Apps secret
  backend_secret_env = merge(
    {
      DB_PASSWORD                           = "db-password"
      APP_JWT_SECRET                        = "jwt-secret"
      APP_TOKEN_SECRET                      = "token-secret"
      APPLICATIONINSIGHTS_CONNECTION_STRING = "appi-conn"
    },
    local.optional_secret_set["smtp-password"] ? { SMTP_PASSWORD = "smtp-password" } : {},
    local.optional_secret_set["admin-password"] ? { APP_ADMIN_PASSWORD = "admin-password" } : {},
    local.captcha_on ? { APP_CAPTCHA_SECRET = "captcha-secret" } : {},
    local.redis_on ? { APP_RATE_LIMIT_REDIS_URL = "redis-url" } : {},
  )
}

resource "azurerm_container_app" "backend" {
  name                         = local.backend_name
  container_app_environment_id = azurerm_container_app_environment.this.id
  resource_group_name          = azurerm_resource_group.this.name
  revision_mode                = "Single"
  workload_profile_name        = "Consumption"
  tags                         = local.tags

  identity {
    type         = "UserAssigned"
    identity_ids = [azurerm_user_assigned_identity.apps.id]
  }

  registry {
    server   = azurerm_container_registry.this.login_server
    identity = azurerm_user_assigned_identity.apps.id
  }

  dynamic "secret" {
    for_each = toset(local.backend_secret_names)
    content {
      name                = secret.value
      key_vault_secret_id = local.secret_ids[secret.value]
      identity            = azurerm_user_assigned_identity.apps.id
    }
  }

  ingress {
    external_enabled           = true
    target_port                = 8080
    transport                  = "auto"
    allow_insecure_connections = false

    traffic_weight {
      latest_revision = true
      percentage      = 100
    }
  }

  template {
    min_replicas = var.backend_min_replicas
    max_replicas = var.backend_max_replicas

    container {
      name   = "backend"
      image  = var.backend_image
      cpu    = var.backend_cpu
      memory = var.backend_memory

      dynamic "env" {
        for_each = local.backend_env
        content {
          name  = env.key
          value = env.value
        }
      }

      dynamic "env" {
        for_each = local.backend_secret_env
        content {
          name        = env.key
          secret_name = env.value
        }
      }

      # Spring Boot + Flyway take ~20-60 s on 0.5 vCPU: generous startup window
      startup_probe {
        transport               = "HTTP"
        port                    = 8080
        path                    = "/actuator/health/liveness"
        initial_delay           = 10
        interval_seconds        = 10
        timeout                 = 5
        failure_count_threshold = 10
      }

      liveness_probe {
        transport               = "HTTP"
        port                    = 8080
        path                    = "/actuator/health/liveness"
        interval_seconds        = 30
        timeout                 = 5
        failure_count_threshold = 3
      }

      # traffic only when the database is reachable
      readiness_probe {
        transport               = "HTTP"
        port                    = 8080
        path                    = "/actuator/health/readiness"
        interval_seconds        = 10
        timeout                 = 5
        failure_count_threshold = 3
      }
    }

    http_scale_rule {
      name                = "http"
      concurrent_requests = "50"
    }
  }

  lifecycle {
    ignore_changes = [template[0].container[0].image]

    precondition {
      condition     = var.backend_max_replicas == 1 || local.redis_on
      error_message = "backend_max_replicas > 1 needs rate_limit_redis_url: with in-memory rate limits every replica counts on its own."
    }
    precondition {
      condition     = local.captcha_on || var.allow_missing_captcha
      error_message = "Set captcha_secret (Cloudflare Turnstile) — or allow_missing_captcha = true for a dev environment."
    }
  }

  depends_on = [
    azurerm_role_assignment.apps_acr_pull,
    azurerm_role_assignment.apps_kv_reader,
  ]
}

resource "azurerm_container_app" "frontend" {
  name                         = local.frontend_name
  container_app_environment_id = azurerm_container_app_environment.this.id
  resource_group_name          = azurerm_resource_group.this.name
  revision_mode                = "Single"
  workload_profile_name        = "Consumption"
  tags                         = local.tags

  identity {
    type         = "UserAssigned"
    identity_ids = [azurerm_user_assigned_identity.apps.id]
  }

  registry {
    server   = azurerm_container_registry.this.login_server
    identity = azurerm_user_assigned_identity.apps.id
  }

  ingress {
    external_enabled           = true
    target_port                = 3000
    transport                  = "auto"
    allow_insecure_connections = false

    traffic_weight {
      latest_revision = true
      percentage      = 100
    }
  }

  template {
    min_replicas = var.frontend_min_replicas
    max_replicas = var.frontend_max_replicas

    container {
      name   = "frontend"
      image  = var.frontend_image
      cpu    = var.frontend_cpu
      memory = var.frontend_memory

      # server-side requests (structured data, legal pages): the environment FQDN resolves
      # inside the environment and has a valid certificate (plain http://<app> would be
      # redirected to https, allow_insecure_connections = false)
      env {
        name  = "API_URL_INTERNAL"
        value = "https://${local.backend_name}.${local.default_domain}"
      }

      startup_probe {
        transport               = "HTTP"
        port                    = 3000
        path                    = "/robots.txt"
        interval_seconds        = 5
        timeout                 = 5
        failure_count_threshold = 10
      }

      liveness_probe {
        transport               = "HTTP"
        port                    = 3000
        path                    = "/robots.txt"
        interval_seconds        = 30
        timeout                 = 5
        failure_count_threshold = 3
      }

      readiness_probe {
        transport               = "HTTP"
        port                    = 3000
        path                    = "/robots.txt"
        interval_seconds        = 10
        timeout                 = 5
        failure_count_threshold = 3
      }
    }

    http_scale_rule {
      name                = "http"
      concurrent_requests = "100"
    }
  }

  lifecycle {
    ignore_changes = [template[0].container[0].image]
  }

  depends_on = [azurerm_role_assignment.apps_acr_pull]
}

# ── custom domains (README.md → Custom domains: DNS first, then the managed certificate) ──

resource "azurerm_container_app_custom_domain" "frontend" {
  count            = var.site_domain == "" ? 0 : 1
  name             = var.site_domain
  container_app_id = azurerm_container_app.frontend.id

  # the free managed certificate is bound with `az containerapp hostname bind` (not in azurerm)
  lifecycle {
    ignore_changes = [certificate_binding_type, container_app_environment_certificate_id]
  }
}

resource "azurerm_container_app_custom_domain" "backend" {
  count            = var.api_domain == "" ? 0 : 1
  name             = var.api_domain
  container_app_id = azurerm_container_app.backend.id

  lifecycle {
    ignore_changes = [certificate_binding_type, container_app_environment_certificate_id]
  }
}
