output "resource_group" {
  value = azurerm_resource_group.this.name
}

output "site_url" {
  description = "Public website URL (APP_FRONTEND_BASE_URL / CORS origin; NEXT_PUBLIC_SITE_URL of the frontend image)."
  value       = local.site_url
}

output "api_url" {
  description = "Public API URL (NEXT_PUBLIC_API_URL of the frontend image; API_URL for the CI smoke test)."
  value       = local.api_url
}

output "acr_name" {
  value = azurerm_container_registry.this.name
}

output "acr_login_server" {
  value = azurerm_container_registry.this.login_server
}

output "backend_app_name" {
  value = azurerm_container_app.backend.name
}

output "frontend_app_name" {
  value = azurerm_container_app.frontend.name
}

output "backend_default_fqdn" {
  description = "CNAME target for api_domain."
  value       = azurerm_container_app.backend.ingress[0].fqdn
}

output "frontend_default_fqdn" {
  description = "CNAME target for web_domain."
  value       = azurerm_container_app.frontend.ingress[0].fqdn
}

output "custom_domain_verification_id" {
  description = "Value of the TXT records asuid.<domain> needed before adding a custom domain."
  value       = azurerm_container_app_environment.this.custom_domain_verification_id
}

output "container_app_environment" {
  value = azurerm_container_app_environment.this.name
}

output "db_init_job_name" {
  description = "Run once after the first apply: az containerapp job start -g <rg> -n <this>."
  value       = azurerm_container_app_job.db_init.name
}

output "migrate_job_name" {
  description = "Flyway with the owner login; CI runs it before every backend rollout."
  value       = azurerm_container_app_job.migrate.name
}

output "postgres_fqdn" {
  value = azurerm_postgresql_flexible_server.this.fqdn
}

output "postgres_server_name" {
  value = azurerm_postgresql_flexible_server.this.name
}

output "key_vault_name" {
  value = azurerm_key_vault.this.name
}

output "log_analytics_workspace_id" {
  value = azurerm_log_analytics_workspace.this.workspace_id
}

output "acs_sender_domain" {
  description = "email_mode = acs: use DoNotReply@<this> as mail_from."
  value       = var.email_mode == "acs" ? azurerm_email_communication_service_domain.managed[0].from_sender_domain : null
}

output "ci_variables" {
  description = "Values for the CI deploy job (GitHub environment variables / GitLab CI variables)."
  value = {
    AZURE_RESOURCE_GROUP = azurerm_resource_group.this.name
    ACR_NAME             = azurerm_container_registry.this.name
    BACKEND_APP_NAME     = azurerm_container_app.backend.name
    MIGRATE_JOB_NAME     = azurerm_container_app_job.migrate.name
    FRONTEND_APP_NAME    = azurerm_container_app.frontend.name
    API_URL              = local.api_url
    NEXT_PUBLIC_SITE_URL = local.site_url
    NEXT_PUBLIC_API_URL  = local.api_url
    NEXT_PUBLIC_TENANT   = var.tenant_slug
    # site-wide DEMO strip: only on a public demo site (the payment page has its own notice)
    NEXT_PUBLIC_SHOW_DEMO_BANNER = tostring(var.public_demo)
  }
}
