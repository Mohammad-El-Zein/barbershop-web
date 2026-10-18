# Key Vault (RBAC): every secret the backend needs. The apps read them with their managed
# identity (Container Apps Key Vault references) — no secret is stored in an app definition.
# Generated secrets (JWT, link tokens, DB password) are created here; optional ones
# (SMTP, admin, CAPTCHA, Redis) only when the variable is set.
# Note: Terraform state contains these values → protect the state (README.md → Remote state).

resource "azurerm_key_vault" "this" {
  name                       = "kv-${substr(local.flat, 0, 21)}" # Key Vault: max. 24 characters
  location                   = azurerm_resource_group.this.location
  resource_group_name        = azurerm_resource_group.this.name
  tenant_id                  = data.azurerm_client_config.current.tenant_id
  sku_name                   = "standard"
  rbac_authorization_enabled = true
  soft_delete_retention_days = 90
  purge_protection_enabled   = var.environment == "prod"
  tags                       = local.tags
}

# whoever runs terraform writes the secrets
resource "azurerm_role_assignment" "deployer_kv_officer" {
  scope                = azurerm_key_vault.this.id
  role_definition_name = "Key Vault Secrets Officer"
  principal_id         = data.azurerm_client_config.current.object_id
}

resource "azurerm_role_assignment" "apps_kv_reader" {
  scope                = azurerm_key_vault.this.id
  role_definition_name = "Key Vault Secrets User"
  principal_id         = azurerm_user_assigned_identity.apps.principal_id
  principal_type       = "ServicePrincipal"
}

resource "random_password" "jwt" {
  length  = 64
  special = false
}

resource "random_password" "link_tokens" {
  length  = 64
  special = false
}

locals {
  # name in Key Vault → value; the optional ones only when set (flags are not secret)
  generated_secrets = {
    "db-password"  = random_password.db.result
    "jwt-secret"   = random_password.jwt.result
    "token-secret" = random_password.link_tokens.result
    "appi-conn"    = azurerm_application_insights.this.connection_string
  }
  optional_secret_values = {
    "smtp-password"  = var.smtp_password
    "admin-password" = var.admin_password
    "captcha-secret" = var.captcha_secret
    "redis-url"      = var.rate_limit_redis_url
  }
  optional_secret_set = {
    for k, v in local.optional_secret_values : k => nonsensitive(v != "")
  }
  optional_secrets = toset([for k, set in local.optional_secret_set : k if set])
}

resource "azurerm_key_vault_secret" "generated" {
  for_each     = nonsensitive(toset(keys(local.generated_secrets)))
  name         = each.key
  value        = local.generated_secrets[each.key]
  key_vault_id = azurerm_key_vault.this.id
  depends_on   = [azurerm_role_assignment.deployer_kv_officer]
}

resource "azurerm_key_vault_secret" "optional" {
  for_each     = local.optional_secrets
  name         = each.key
  value        = local.optional_secret_values[each.key]
  key_vault_id = azurerm_key_vault.this.id
  depends_on   = [azurerm_role_assignment.deployer_kv_officer]
}
