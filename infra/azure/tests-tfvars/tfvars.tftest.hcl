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
