#!/bin/sh
# Offline tests for rollout.sh / run-migrate-job.sh: fake `az` and `curl` on PATH record every
# call; no Azure account, no network.   sh infra/azure/scripts/tests/rollout.test.sh
set -eu
SCRIPTS="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir "$WORK/bin"

# fake az: logs its arguments; the migration status and job start come from env variables
cat > "$WORK/bin/az" <<'FAKE'
#!/bin/sh
echo "az $*" >> "$CALLS"
case "$*" in
  "containerapp job start "*) echo "exec-1" ;;
  "containerapp job execution show "*) echo "${FAKE_MIGRATE_STATUS:-Succeeded}" ;;
esac
FAKE
# fake curl: fails until attempt $FAKE_READY_AFTER (0 = never ready)
cat > "$WORK/bin/curl" <<'FAKE'
#!/bin/sh
n=$(($(cat "$COUNTER" 2>/dev/null || echo 0) + 1))
echo "$n" > "$COUNTER"
echo "curl $*" >> "$CALLS"
[ "${FAKE_READY_AFTER:-1}" -ne 0 ] && [ "$n" -ge "${FAKE_READY_AFTER:-1}" ]
FAKE
chmod +x "$WORK/bin/az" "$WORK/bin/curl"

export PATH="$WORK/bin:$PATH" MIGRATE_POLL_SECONDS=0 HEALTH_DELAY=0 HEALTH_TRIES=3
FAILED=0

run() { # <name> <expected exit code> ; env FAKE_* set by the caller
  export CALLS="$WORK/$1.calls" COUNTER="$WORK/$1.count"
  set +e
  sh "$SCRIPTS/rollout.sh" rg-x job-migrate ca-api ca-web acr.io/backend:sha-abc1234 \
    acr.io/frontend:sha-abc1234 https://api.example.test/ > "$WORK/$1.out" 2>&1
  CODE=$?
  set -e
  if [ "$CODE" -ne "$2" ]; then
    echo "FAIL $1: exit $CODE, expected $2"; cat "$WORK/$1.out"; FAILED=1
  fi
}
expect() { # <name> <grep pattern> : the calls contain it
  grep -q -- "$2" "$WORK/$1.calls" || { echo "FAIL $1: no call matching '$2'"; cat "$WORK/$1.calls"; FAILED=1; }
}
reject() { # <name> <grep pattern> : the calls do not contain it
  if grep -q -- "$2" "$WORK/$1.calls"; then echo "FAIL $1: unexpected call '$2'"; FAILED=1; fi
}

# 1) happy path: migrate → backend → frontend → health, in this order
FAKE_MIGRATE_STATUS=Succeeded FAKE_READY_AFTER=1 run ok 0
expect ok "containerapp job update -g rg-x -n job-migrate --image acr.io/backend:sha-abc1234"
expect ok "readiness"
ORDER=$(grep -o -e "job start" -e "-n ca-api" -e "-n ca-web" -e "^curl" "$WORK/ok.calls" | tr '\n' '|')
case "$ORDER" in
  "job start|-n ca-api|-n ca-web|curl|") ;;
  *) echo "FAIL ok: order $ORDER"; FAILED=1 ;;
esac
grep -q -- "-n ca-api --image acr.io/backend:sha-abc1234" "$WORK/ok.calls" || { echo "FAIL ok: backend image"; FAILED=1; }
grep -q -- "-n ca-web --image acr.io/frontend:sha-abc1234" "$WORK/ok.calls" || { echo "FAIL ok: frontend image"; FAILED=1; }
grep -q "https://api.example.test/actuator/health/readiness" "$WORK/ok.calls" || { echo "FAIL ok: url (trailing slash)"; FAILED=1; }

# 2) failed migration: nothing is rolled out, no health check
FAKE_MIGRATE_STATUS=Failed run migrate-failed 1
reject migrate-failed "containerapp update"
reject migrate-failed "curl"
grep -q "nothing was rolled out" "$WORK/migrate-failed.out" || { echo "FAIL migrate-failed: message"; FAILED=1; }

# 3) backend becomes ready on the 3rd attempt
FAKE_READY_AFTER=3 run slow 0
[ "$(grep -c '^curl' "$WORK/slow.calls")" -eq 3 ] || { echo "FAIL slow: expected 3 health attempts"; FAILED=1; }

# 4) never ready: exit 1 after HEALTH_TRIES attempts
FAKE_READY_AFTER=0 run never-ready 1
[ "$(grep -c '^curl' "$WORK/never-ready.calls")" -eq 3 ] || { echo "FAIL never-ready: expected 3 health attempts"; FAILED=1; }

if [ "$FAILED" -ne 0 ]; then exit 1; fi
echo "rollout tests passed (4 scenarios)"
