# The real env/*.tfvars files against mocked providers (types, preconditions, wiring):
#   terraform test -test-directory=tests-tfvars -var-file=env/dev.tfvars -var subscription_id=00000000-0000-4000-8000-000000000004
#   terraform test -test-directory=tests-tfvars -var-file=env/prod.tfvars -var subscription_id=00000000-0000-4000-8000-000000000004 -var captcha_secret=x

mock_provider "azurerm" {
  source = "./tests/mocks"
}

mock_provider "random" {
  source = "./tests/mocks"
}

run "tfvars_apply_against_mocks" {
  command = apply

  assert {
    condition     = startswith(output.site_url, "https://") && startswith(output.api_url, "https://")
    error_message = "Both public URLs are https."
  }
  assert {
    condition     = output.site_url == (var.web_domain != "" ? "https://${var.web_domain}" : output.site_url) && output.api_url == (var.api_domain != "" ? "https://${var.api_domain}" : output.api_url)
    error_message = "web_domain / api_domain drive the public URLs."
  }
  assert {
    condition     = [for e in azurerm_container_app.backend.template[0].container[0].env : e.value if e.name == "APP_FRONTEND_BASE_URL"][0] == output.site_url && split(",", [for e in azurerm_container_app.backend.template[0].container[0].env : e.value if e.name == "APP_CORS_ALLOWED_ORIGINS"][0])[0] == output.site_url
    error_message = "Email links and the first CORS origin are the website URL."
  }
  assert {
    condition     = var.environment != "prod" || length(split(",", [for e in azurerm_container_app.backend.template[0].container[0].env : e.value if e.name == "APP_CORS_ALLOWED_ORIGINS"][0])) == 1
    error_message = "prod: CORS allows exactly one origin."
  }
  assert {
    condition     = output.ci_variables.API_URL == output.api_url && output.ci_variables.NEXT_PUBLIC_API_URL == output.api_url && output.ci_variables.NEXT_PUBLIC_SITE_URL == output.site_url
    error_message = "ci_variables carry the public URLs (frontend build, CD health check)."
  }
  assert {
    condition     = length(azurerm_container_app_environment_managed_certificate.frontend) == (var.web_domain != "" ? 1 : 0) && length(azurerm_container_app_environment_managed_certificate.backend) == (var.api_domain != "" ? 1 : 0)
    error_message = "A managed certificate per custom domain."
  }
  assert {
    condition = alltrue([
      for c in [azurerm_container_app.backend.template[0].container[0], azurerm_container_app_job.migrate.template[0].container[0]] :
      [for e in c.env : e.value if e.name == "PAYMENT_DEMO_ALLOW_IN_PROD"][0] == tostring(var.environment != "prod")
    ])
    error_message = "Demo payments: allowed in dev (backend and migrate job), never in prod."
  }
  assert {
    condition     = length(azurerm_monitor_scheduled_query_rules_alert_v2.mail_dead) == (var.environment == "prod" && var.alert_email != "" ? 1 : 0)
    error_message = "The mail log alert: off in dev, on in prod when alert_email is set."
  }
}
