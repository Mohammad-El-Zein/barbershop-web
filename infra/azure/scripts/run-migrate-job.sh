#!/bin/sh
# Runs the Flyway migrations of a release BEFORE the backend is rolled out (CI deploy step).
#   infra/azure/scripts/run-migrate-job.sh <resource group> <migrate job name> <backend image>
# Sets the job's image to the release, starts one execution and waits for it. On failure the
# running backend is untouched (it keeps serving the old version) and the deploy stops.
set -eu
RG="$1"
JOB="$2"
IMAGE="$3"

az containerapp job update -g "$RG" -n "$JOB" --image "$IMAGE" --output none
EXECUTION=$(az containerapp job start -g "$RG" -n "$JOB" --query name -o tsv)
echo "migration job execution: $EXECUTION ($IMAGE)"

i=0
while [ "$i" -lt 90 ]; do # up to 15 minutes
  STATUS=$(az containerapp job execution show -g "$RG" -n "$JOB" --job-execution-name "$EXECUTION" \
    --query properties.status -o tsv)
  case "$STATUS" in
    Succeeded)
      echo "migrations applied"
      exit 0
      ;;
    Failed | Stopped | Degraded)
      echo "migration job $STATUS — nothing was rolled out. Logs:" >&2
      echo "  az containerapp job logs show -g $RG -n $JOB --execution $EXECUTION --container migrate" >&2
      exit 1
      ;;
  esac
  i=$((i + 1))
  sleep 10
done
echo "migration job did not finish within 15 minutes" >&2
exit 1
