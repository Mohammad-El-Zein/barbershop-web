# Optional: Azure Communication Services Email (email_mode = "acs") with an Azure-managed
# sender domain (xxxx.azurecomm.net). For your own domain, add a custom domain in the
# portal (SPF/DKIM DNS records) and use it in mail_from.
# SMTP login for ACS: an Entra app with a client secret and the "Communication and Email
# Service Owner" role; username "<acs name>.<app id>.<tenant id>" — see README.md → Email.

resource "azurerm_email_communication_service" "this" {
  count               = var.email_mode == "acs" ? 1 : 0
  name                = "ecs-${local.name}-${local.suffix}"
  resource_group_name = azurerm_resource_group.this.name
  data_location       = "Germany"
  tags                = local.tags
}

resource "azurerm_email_communication_service_domain" "managed" {
  count             = var.email_mode == "acs" ? 1 : 0
  name              = "AzureManagedDomain"
  email_service_id  = azurerm_email_communication_service.this[0].id
  domain_management = "AzureManaged"
  tags              = local.tags
}

resource "azurerm_communication_service" "this" {
  count               = var.email_mode == "acs" ? 1 : 0
  name                = "acs-${local.name}-${local.suffix}"
  resource_group_name = azurerm_resource_group.this.name
  data_location       = "Germany"
  tags                = local.tags
}

resource "azurerm_communication_service_email_domain_association" "this" {
  count                    = var.email_mode == "acs" ? 1 : 0
  communication_service_id = azurerm_communication_service.this[0].id
  email_service_domain_id  = azurerm_email_communication_service_domain.managed[0].id
}
