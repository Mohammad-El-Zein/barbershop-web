#!/bin/sh
# Runs init-roles.sql as the Postgres superuser. Mounted into /docker-entrypoint-initdb.d,
# it runs automatically when the data volume is created. For an EXISTING volume run once:
#   docker compose -f infra/docker-compose.yml exec postgres sh /docker-entrypoint-initdb.d/10-init-roles.sh
set -eu
: "${DB_MIGRATION_PASSWORD:?DB_MIGRATION_PASSWORD is required}"
: "${DB_APP_PASSWORD:?DB_APP_PASSWORD is required}"
# passwords via the environment, not the command line (process list)
MIGRATOR_PASSWORD="$DB_MIGRATION_PASSWORD" APP_PASSWORD="$DB_APP_PASSWORD" \
psql -X -q -v ON_ERROR_STOP=1 \
  --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v migrator="${DB_MIGRATION_USERNAME:-barbershop_migrator}" \
  -v app="${DB_APP_USERNAME:-barbershop_app}" \
  -f /opt/barbershop/init-roles.sql
