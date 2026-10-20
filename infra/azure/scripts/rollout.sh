#!/bin/sh
# Rolls a release out to one environment (CI deploy step, GitHub and GitLab):
#   infra/azure/scripts/rollout.sh <resource group> <migrate job> <backend app> <frontend app> \
#     <backend image> <frontend image> <api url>
# Order: 1) migrations (separate job, owner login) — on failure nothing is rolled out;
# 2) backend (application login, validates the schema); 3) frontend; 4) readiness check with
# retries. Both images must already be in the environment's ACR. Prints no secrets.
set -eu
RG="$1"
MIGRATE_JOB="$2"
BACKEND_APP="$3"
FRONTEND_APP="$4"
BACKEND_IMAGE="$5"
FRONTEND_IMAGE="$6"
API_URL="${7%/}"
HEALTH_TRIES="${HEALTH_TRIES:-30}"
HEALTH_DELAY="${HEALTH_DELAY:-10}"

sh "$(dirname "$0")/run-migrate-job.sh" "$RG" "$MIGRATE_JOB" "$BACKEND_IMAGE"

echo "backend  → $BACKEND_IMAGE"
az containerapp update -g "$RG" -n "$BACKEND_APP" --image "$BACKEND_IMAGE" --output none
echo "frontend → $FRONTEND_IMAGE"
az containerapp update -g "$RG" -n "$FRONTEND_APP" --image "$FRONTEND_IMAGE" --output none

i=1
while [ "$i" -le "$HEALTH_TRIES" ]; do
  if curl -fsS --max-time 10 "$API_URL/actuator/health/readiness" >/dev/null; then
    echo "backend ready ($API_URL/actuator/health/readiness, attempt $i)"
    exit 0
  fi
  echo "backend not ready yet (attempt $i/$HEALTH_TRIES)"
  i=$((i + 1))
  if [ "$i" -le "$HEALTH_TRIES" ]; then sleep "$HEALTH_DELAY"; fi
done
echo "backend not ready after $HEALTH_TRIES attempts — roll back: docs/DEPLOYMENT.md → Rollback" >&2
exit 1
