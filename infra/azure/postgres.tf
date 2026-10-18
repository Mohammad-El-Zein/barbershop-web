# Azure Database for PostgreSQL Flexible Server: TLS only, automatic backups
# (point-in-time restore), optional zone-redundant HA and geo-redundant backups.
# Flyway (in the backend) creates and migrates the schema on start.

# Three logins (docs/DEPLOYMENT.md → Database roles), all generated and kept in Key Vault:
#   admin     server administrator: only the one-off db-init job (creates the two roles)
#   migrator  owns the schema; only the migrate job (Flyway) uses it
#   app       the backend's connection pool: DML only — the prod start-up check refuses more
resource "random_password" "db" {
  length  = 32
  special = false # URL / JDBC-safe
}

resource "random_password" "db_migrator" {
  length  = 32
  special = false
}

resource "random_password" "db_app" {
  length  = 32
  special = false
}

locals {
  db_migrator_role = "barbershop_migrator"
  db_app_role      = "barbershop_app"
}

resource "azurerm_postgresql_flexible_server" "this" {
  name                = "psql-${local.name}-${local.suffix}"
  location            = azurerm_resource_group.this.location
  resource_group_name = azurerm_resource_group.this.name
  version             = var.postgres_version
  sku_name            = var.postgres_sku
  storage_mb          = var.postgres_storage_mb
  auto_grow_enabled   = true
  zone                = var.postgres_zone

  administrator_login    = "barbershop_admin"
  administrator_password = random_password.db.result

  backup_retention_days        = var.postgres_backup_retention_days
  geo_redundant_backup_enabled = var.postgres_geo_redundant_backup

  # private: VNet-integrated, no public endpoint · public: firewall rules below
  public_network_access_enabled = var.postgres_public_access
  delegated_subnet_id           = var.postgres_public_access ? null : azurerm_subnet.postgres[0].id
  private_dns_zone_id           = var.postgres_public_access ? null : azurerm_private_dns_zone.postgres[0].id

  authentication {
    password_auth_enabled         = true
    active_directory_auth_enabled = false
  }

  dynamic "high_availability" {
    for_each = var.postgres_high_availability ? [1] : []
    content {
      mode                      = "ZoneRedundant"
      standby_availability_zone = var.postgres_standby_zone
    }
  }

  # Sunday 03:00 UTC
  maintenance_window {
    day_of_week  = 0
    start_hour   = 3
    start_minute = 0
  }

  tags = local.tags

  lifecycle {
    # Azure may swap primary / standby zones after a failover
    ignore_changes = [zone, high_availability[0].standby_availability_zone]
  }

  depends_on = [azurerm_private_dns_zone_virtual_network_link.postgres]
}

resource "azurerm_postgresql_flexible_server_database" "app" {
  name      = "barbershop"
  server_id = azurerm_postgresql_flexible_server.this.id
  charset   = "UTF8"
  collation = "en_US.utf8"

  lifecycle {
    prevent_destroy = true # the shop's data
  }
}

# TLS is enforced (require_secure_transport is on by default); at least TLS 1.2
resource "azurerm_postgresql_flexible_server_configuration" "tls" {
  name      = "ssl_min_protocol_version"
  server_id = azurerm_postgresql_flexible_server.this.id
  value     = "TLSv1.2"
}

resource "azurerm_postgresql_flexible_server_firewall_rule" "allowed" {
  for_each         = var.postgres_public_access ? var.postgres_firewall_ips : {}
  name             = each.key
  server_id        = azurerm_postgresql_flexible_server.this.id
  start_ip_address = each.value
  end_ip_address   = each.value
}
