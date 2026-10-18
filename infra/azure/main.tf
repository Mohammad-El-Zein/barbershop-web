# Barbershop on Azure Container Apps — resource group, naming, identity, registry, monitoring.
# The other parts: network.tf, postgres.tf, keyvault.tf, email.tf, containerapps.tf.

data "azurerm_client_config" "current" {}

resource "random_string" "suffix" {
  length  = 4
  upper   = false
  special = false
}

locals {
  name   = "${var.name_prefix}-${var.environment}"
  suffix = random_string.suffix.result
  # globally unique, alphanumeric-only names
  flat = substr(replace("${var.name_prefix}${var.environment}${local.suffix}", "-", ""), 0, 22)

  backend_name  = "ca-${local.name}-api"
  frontend_name = "ca-${local.name}-web"

  tags = merge({
    app         = "barbershop"
    environment = var.environment
    managed-by  = "terraform"
  }, var.tags)
}

resource "azurerm_resource_group" "this" {
  name     = "rg-${local.name}"
  location = var.location
  tags     = local.tags
}

# One user-assigned identity for both apps: pulls images (AcrPull) and reads secrets
# (Key Vault Secrets User). No passwords for either.
resource "azurerm_user_assigned_identity" "apps" {
  name                = "id-${local.name}-apps"
  location            = azurerm_resource_group.this.location
  resource_group_name = azurerm_resource_group.this.name
  tags                = local.tags
}

# ── container registry ───────────────────────────────────────────────────────

resource "azurerm_container_registry" "this" {
  name                = "acr${local.flat}"
  location            = azurerm_resource_group.this.location
  resource_group_name = azurerm_resource_group.this.name
  sku                 = "Basic"
  admin_enabled       = false # identities only
  tags                = local.tags
}

resource "azurerm_role_assignment" "apps_acr_pull" {
  scope                = azurerm_container_registry.this.id
  role_definition_name = "AcrPull"
  principal_id         = azurerm_user_assigned_identity.apps.principal_id
  principal_type       = "ServicePrincipal"
}

# CI/CD (GitHub OIDC / GitLab id_tokens): push images, update the container apps
resource "azurerm_role_assignment" "ci_acr_push" {
  count                = var.ci_principal_id == "" ? 0 : 1
  scope                = azurerm_container_registry.this.id
  role_definition_name = "AcrPush"
  principal_id         = var.ci_principal_id
}

resource "azurerm_role_assignment" "ci_rg_contributor" {
  count                = var.ci_principal_id == "" ? 0 : 1
  scope                = azurerm_resource_group.this.id
  role_definition_name = "Contributor"
  principal_id         = var.ci_principal_id
}

# ── monitoring ───────────────────────────────────────────────────────────────

resource "azurerm_log_analytics_workspace" "this" {
  name                = "log-${local.name}"
  location            = azurerm_resource_group.this.location
  resource_group_name = azurerm_resource_group.this.name
  sku                 = "PerGB2018"
  retention_in_days   = var.log_retention_days
  # a hard cap against runaway logging costs (GB/day); -1 = unlimited
  daily_quota_gb = var.environment == "prod" ? 2 : 0.5
  tags           = local.tags
}

resource "azurerm_application_insights" "this" {
  name                = "appi-${local.name}"
  location            = azurerm_resource_group.this.location
  resource_group_name = azurerm_resource_group.this.name
  workspace_id        = azurerm_log_analytics_workspace.this.id
  application_type    = "web"
  retention_in_days   = 90
  tags                = local.tags
}

# Availability: the API's readiness endpoint from three European locations (prod every 5 min, dev 15).
resource "azurerm_application_insights_standard_web_test" "api" {
  name                    = "webtest-${local.name}-api"
  location                = azurerm_resource_group.this.location
  resource_group_name     = azurerm_resource_group.this.name
  application_insights_id = azurerm_application_insights.this.id
  geo_locations           = ["emea-nl-ams-azr", "emea-gb-db3-azr", "emea-fr-pra-edge"]
  frequency               = var.environment == "prod" ? 300 : 900 # billed per run (README → Costs)
  retry_enabled           = true
  enabled                 = true
  tags                    = local.tags

  request {
    url = "${local.api_url}/actuator/health/readiness"
  }

  validation_rules {
    expected_status_code        = 200
    ssl_check_enabled           = true
    ssl_cert_remaining_lifetime = 7
  }
}

resource "azurerm_monitor_action_group" "ops" {
  count               = var.alert_email == "" ? 0 : 1
  name                = "ag-${local.name}-ops"
  resource_group_name = azurerm_resource_group.this.name
  short_name          = "barbershop"
  tags                = local.tags

  email_receiver {
    name                    = "ops"
    email_address           = var.alert_email
    use_common_alert_schema = true
  }
}

resource "azurerm_monitor_metric_alert" "api_down" {
  count               = var.alert_email == "" ? 0 : 1
  name                = "alert-${local.name}-api-down"
  resource_group_name = azurerm_resource_group.this.name
  scopes              = [azurerm_application_insights_standard_web_test.api.id, azurerm_application_insights.this.id]
  description         = "The API readiness check fails from at least 2 locations."
  severity            = 1
  frequency           = "PT5M"
  window_size         = "PT15M"
  tags                = local.tags

  application_insights_web_test_location_availability_criteria {
    web_test_id           = azurerm_application_insights_standard_web_test.api.id
    component_id          = azurerm_application_insights.this.id
    failed_location_count = 2
  }

  action {
    action_group_id = azurerm_monitor_action_group.ops[0].id
  }
}

# Mail delivery: an email went DEAD after all retries (MailOutboxWorker logs "needs attention").
resource "azurerm_monitor_scheduled_query_rules_alert_v2" "mail_dead" {
  count                = var.alert_email == "" ? 0 : 1
  name                 = "alert-${local.name}-mail-dead"
  resource_group_name  = azurerm_resource_group.this.name
  location             = azurerm_resource_group.this.location
  scopes               = [azurerm_log_analytics_workspace.this.id]
  description          = "An email could not be delivered after all retries — see Admin → Email delivery."
  severity             = 2
  evaluation_frequency = "PT15M"
  window_duration      = "PT15M"
  tags                 = local.tags

  criteria {
    query                   = <<-KQL
      ContainerAppConsoleLogs_CL
      | where ContainerAppName_s == "${local.backend_name}"
      | where Log_s has "needs attention"
    KQL
    time_aggregation_method = "Count"
    operator                = "GreaterThan"
    threshold               = 0
  }

  action {
    action_groups = [azurerm_monitor_action_group.ops[0].id]
  }
}
