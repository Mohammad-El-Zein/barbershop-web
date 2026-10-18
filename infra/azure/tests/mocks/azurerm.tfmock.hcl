# Mocked azurerm values for `terraform test` (ids must have the real Azure format).
mock_resource "azurerm_resource_group" {
  defaults = {
    id = "/subscriptions/00000000-0000-4000-8000-000000000004/resourceGroups/rg-barbershop-dev"
  }
}

mock_resource "azurerm_virtual_network" {
  defaults = {
    id = "/subscriptions/00000000-0000-4000-8000-000000000004/resourceGroups/rg-barbershop-dev/providers/Microsoft.Network/virtualNetworks/vnet-barbershop-dev"
  }
}

mock_resource "azurerm_subnet" {
  defaults = {
    id = "/subscriptions/00000000-0000-4000-8000-000000000004/resourceGroups/rg-barbershop-dev/providers/Microsoft.Network/virtualNetworks/vnet-barbershop-dev/subnets/snet-aca"
  }
}

mock_resource "azurerm_private_dns_zone" {
  defaults = {
    id = "/subscriptions/00000000-0000-4000-8000-000000000004/resourceGroups/rg-barbershop-dev/providers/Microsoft.Network/privateDnsZones/barbershop-dev.private.postgres.database.azure.com"
  }
}

mock_resource "azurerm_log_analytics_workspace" {
  defaults = {
    id = "/subscriptions/00000000-0000-4000-8000-000000000004/resourceGroups/rg-barbershop-dev/providers/Microsoft.OperationalInsights/workspaces/log-barbershop-dev"
  }
}

mock_resource "azurerm_application_insights" {
  defaults = {
    id = "/subscriptions/00000000-0000-4000-8000-000000000004/resourceGroups/rg-barbershop-dev/providers/Microsoft.Insights/components/appi-barbershop-dev"
  }
}

mock_resource "azurerm_application_insights_standard_web_test" {
  defaults = {
    id = "/subscriptions/00000000-0000-4000-8000-000000000004/resourceGroups/rg-barbershop-dev/providers/Microsoft.Insights/webTests/webtest-barbershop-dev-api"
  }
}

mock_resource "azurerm_monitor_action_group" {
  defaults = {
    id = "/subscriptions/00000000-0000-4000-8000-000000000004/resourceGroups/rg-barbershop-dev/providers/Microsoft.Insights/actionGroups/ag-barbershop-dev-ops"
  }
}

mock_resource "azurerm_container_registry" {
  defaults = {
    id = "/subscriptions/00000000-0000-4000-8000-000000000004/resourceGroups/rg-barbershop-dev/providers/Microsoft.ContainerRegistry/registries/acrbarbershopdev0000"
  }
}

mock_resource "azurerm_key_vault" {
  defaults = {
    id = "/subscriptions/00000000-0000-4000-8000-000000000004/resourceGroups/rg-barbershop-dev/providers/Microsoft.KeyVault/vaults/kv-barbershopdev0000"
  }
}

mock_resource "azurerm_container_app" {
  defaults = {
    id = "/subscriptions/00000000-0000-4000-8000-000000000004/resourceGroups/rg-barbershop-dev/providers/Microsoft.App/containerApps/ca-barbershop-dev-web"
  }
}

mock_resource "azurerm_email_communication_service" {
  defaults = {
    id = "/subscriptions/00000000-0000-4000-8000-000000000004/resourceGroups/rg-barbershop-dev/providers/Microsoft.Communication/emailServices/ecs-barbershop-dev-0000"
  }
}

mock_resource "azurerm_email_communication_service_domain" {
  defaults = {
    id = "/subscriptions/00000000-0000-4000-8000-000000000004/resourceGroups/rg-barbershop-dev/providers/Microsoft.Communication/emailServices/ecs-barbershop-dev-0000/domains/AzureManagedDomain"
  }
}

mock_resource "azurerm_communication_service" {
  defaults = {
    id = "/subscriptions/00000000-0000-4000-8000-000000000004/resourceGroups/rg-barbershop-dev/providers/Microsoft.Communication/communicationServices/acs-barbershop-dev-0000"
  }
}

mock_resource "azurerm_user_assigned_identity" {
  defaults = {
    id           = "/subscriptions/00000000-0000-4000-8000-000000000004/resourceGroups/rg-barbershop-dev/providers/Microsoft.ManagedIdentity/userAssignedIdentities/id-barbershop-dev-apps"
    principal_id = "00000000-0000-4000-8000-000000000010"
    client_id    = "00000000-0000-4000-8000-000000000011"
    tenant_id    = "00000000-0000-4000-8000-000000000001"
  }
}

mock_resource "azurerm_key_vault_secret" {
  defaults = {
    id             = "https://kv-barbershopdev0000.vault.azure.net/secrets/mock/0123456789abcdef0123456789abcdef"
    versionless_id = "https://kv-barbershopdev0000.vault.azure.net/secrets/mock"
  }
}

mock_resource "azurerm_container_app_environment" {
  defaults = {
    id                            = "/subscriptions/00000000-0000-4000-8000-000000000004/resourceGroups/rg-barbershop-dev/providers/Microsoft.App/managedEnvironments/cae-barbershop-dev"
    default_domain                = "nicefield-0000.germanywestcentral.azurecontainerapps.io"
    custom_domain_verification_id = "0000000000000000000000000000000000000000000000000000000000000000"
  }
}

mock_resource "azurerm_postgresql_flexible_server" {
  defaults = {
    id   = "/subscriptions/00000000-0000-4000-8000-000000000004/resourceGroups/rg-barbershop-dev/providers/Microsoft.DBforPostgreSQL/flexibleServers/psql-barbershop-dev-0000"
    fqdn = "psql-barbershop-dev-0000.postgres.database.azure.com"
  }
}

mock_data "azurerm_client_config" {
  defaults = {
    tenant_id       = "00000000-0000-4000-8000-000000000001"
    object_id       = "00000000-0000-4000-8000-000000000002"
    client_id       = "00000000-0000-4000-8000-000000000003"
    subscription_id = "00000000-0000-4000-8000-000000000004"
  }
}
