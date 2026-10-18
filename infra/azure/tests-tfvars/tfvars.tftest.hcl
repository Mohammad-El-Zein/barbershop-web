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
}
