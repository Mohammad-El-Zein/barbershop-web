# Database jobs inside the VNet (the server has no public endpoint), started manually / by CI:
#
#   db-init   once per server: creates barbershop_migrator (owner) and barbershop_app (DML only)
#             with the admin login — the same script as locally (infra/postgres/init-roles.sql)
#   migrate   before every rollout: Flyway with the OWNER login (backend image, APP_MIGRATE_ONLY),
#             then exits. The long-running backend only has the application login and
#             refuses to start while migrations are pending.
#
#   az containerapp job start -g <rg> -n <job>        (docs/DEPLOYMENT.md → Database roles)

locals {
  init_roles_sql = file("${path.module}/../postgres/init-roles.sql")
}

resource "azurerm_container_app_job" "db_init" {
  name                         = "job-${local.name}-db-init"
  location                     = azurerm_resource_group.this.location
  resource_group_name          = azurerm_resource_group.this.name
  container_app_environment_id = azurerm_container_app_environment.this.id
  workload_profile_name        = "Consumption"
  replica_timeout_in_seconds   = 300
  replica_retry_limit          = 0
  tags                         = local.tags

  manual_trigger_config {
    parallelism              = 1
    replica_completion_count = 1
  }

  identity {
    type         = "UserAssigned"
    identity_ids = [azurerm_user_assigned_identity.apps.id]
  }

  dynamic "secret" {
    for_each = toset(["db-admin-password", "db-migration-password", "db-app-password"])
    content {
      name                = secret.value
      key_vault_secret_id = local.secret_ids[secret.value]
      identity            = azurerm_user_assigned_identity.apps.id
    }
  }

  template {
    container {
      name   = "db-init"
      image  = "docker.io/library/postgres:${var.postgres_version}-alpine"
      cpu    = 0.25
      memory = "0.5Gi"
      # Container Apps rewrites "$$" and "$(…)" in command and env values (like Kubernetes):
      # neither may appear here or in the script (tests/plan.tftest.hcl). Passwords reach psql
      # only through the environment (libpq PGPASSWORD, \getenv in the script): not on the
      # command line, never printed (-q, no result rows, terse errors).
      command = ["sh", "-c", join(" ", [
        "printf '%s' \"$INIT_SQL\" > /tmp/init-roles.sql &&",
        "psql -X -q -v ON_ERROR_STOP=1 -v migrator=\"$MIGRATOR\" -v app=\"$APP\" -f /tmp/init-roles.sql",
      ])]

      env {
        name  = "INIT_SQL"
        value = local.init_roles_sql
      }
      env {
        name  = "PGHOST"
        value = azurerm_postgresql_flexible_server.this.fqdn
      }
      env {
        name  = "PGDATABASE"
        value = azurerm_postgresql_flexible_server_database.app.name
      }
      env {
        name  = "PGUSER"
        value = azurerm_postgresql_flexible_server.this.administrator_login
      }
      env {
        name  = "PGSSLMODE"
        value = "require"
      }
      env {
        name  = "MIGRATOR"
        value = local.db_migrator_role
      }
      env {
        name  = "APP"
        value = local.db_app_role
      }
      env {
        name        = "PGPASSWORD"
        secret_name = "db-admin-password"
      }
      env {
        name        = "MIGRATOR_PASSWORD"
        secret_name = "db-migration-password"
      }
      env {
        name        = "APP_PASSWORD"
        secret_name = "db-app-password"
      }
    }
  }

  lifecycle {
    precondition {
      condition     = !strcontains(local.init_roles_sql, "$")
      error_message = "infra/postgres/init-roles.sql must not contain \"$\": Container Apps rewrites it in env values."
    }
  }

  depends_on = [azurerm_role_assignment.apps_kv_reader]
}

resource "azurerm_container_app_job" "migrate" {
  name                         = "job-${local.name}-migrate"
  location                     = azurerm_resource_group.this.location
  resource_group_name          = azurerm_resource_group.this.name
  container_app_environment_id = azurerm_container_app_environment.this.id
  workload_profile_name        = "Consumption"
  replica_timeout_in_seconds   = 900
  replica_retry_limit          = 0
  tags                         = local.tags

  manual_trigger_config {
    parallelism              = 1
    replica_completion_count = 1
  }

  identity {
    type         = "UserAssigned"
    identity_ids = [azurerm_user_assigned_identity.apps.id]
  }

  registry {
    server   = azurerm_container_registry.this.login_server
    identity = azurerm_user_assigned_identity.apps.id
  }

  # the full backend configuration (its start-up checks run) + the owner login
  dynamic "secret" {
    for_each = toset(concat(local.backend_secret_names, ["db-migration-password"]))
    content {
      name                = secret.value
      key_vault_secret_id = local.secret_ids[secret.value]
      identity            = azurerm_user_assigned_identity.apps.id
    }
  }

  template {
    container {
      name   = "migrate"
      image  = var.backend_image
      cpu    = var.backend_cpu
      memory = var.backend_memory

      dynamic "env" {
        for_each = merge(local.backend_env, {
          APP_MIGRATE_ONLY      = "true"
          DB_MIGRATION_USERNAME = local.db_migrator_role
        })
        content {
          name  = env.key
          value = env.value
        }
      }

      dynamic "env" {
        for_each = merge(local.backend_secret_env, { DB_MIGRATION_PASSWORD = "db-migration-password" })
        content {
          name        = env.key
          secret_name = env.value
        }
      }
    }
  }

  lifecycle {
    # CI sets the image of each release (az containerapp job update --image) before starting it
    ignore_changes = [template[0].container[0].image]
  }

  depends_on = [
    azurerm_role_assignment.apps_acr_pull,
    azurerm_role_assignment.apps_kv_reader,
  ]
}
