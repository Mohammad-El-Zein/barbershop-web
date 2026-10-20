-- Database roles for the barbershop backend (docs/DEPLOYMENT.md → Database roles).
-- Run ONCE per database as the server admin (local/VPS: the Postgres superuser; Azure: the
-- Flexible Server admin). Idempotent: running it again updates passwords and ownership.
--
--   MIGRATOR_PASSWORD=… APP_PASSWORD=… psql -X -q -v ON_ERROR_STOP=1 \
--        -v migrator=barbershop_migrator -v app=barbershop_app -d barbershop -f init-roles.sql
--
--   migrator  owns the database, the schema and all tables; used ONLY by Flyway (DDL)
--   app       the backend's connection pool: DML only (grants come from Flyway's afterMigrate)
-- The admin login itself is not used by the application.
--
-- Plain psql on purpose (psql 15+): no DO block, no dollar quoting at all. Azure Container Apps
-- (like Kubernetes) rewrites dollar signs in a job's command and env values, and this file is
-- passed to the db-init job as an env value. The passwords come from the environment
-- (not the command line) and are never printed: no result rows, no echo, terse errors.
\set ON_ERROR_STOP on
\set QUIET on
\set ECHO none
\set VERBOSITY terse
\set SHOW_CONTEXT never

-- a failed check runs a cast that fails with the reason as text → psql stops with an error
\getenv migrator_password MIGRATOR_PASSWORD
\getenv app_password APP_PASSWORD
\if :{?migrator}
\else
  SELECT 'missing: -v migrator=<role>'::int;
\endif
\if :{?app}
\else
  SELECT 'missing: -v app=<role>'::int;
\endif
\if :{?migrator_password}
\else
  SELECT 'missing: environment variable MIGRATOR_PASSWORD'::int;
\endif
\if :{?app_password}
\else
  SELECT 'missing: environment variable APP_PASSWORD'::int;
\endif

SELECT :'migrator' = :'app' OR :'migrator' = current_user OR :'app' = current_user AS same_roles,
       :'migrator_password' = '' OR :'app_password' = '' AS no_passwords,
       EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'migrator') AS migrator_exists,
       EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'app') AS app_exists,
       (SELECT rolsuper FROM pg_roles WHERE rolname = current_user) AS is_superuser,
       current_database() AS db
\gset
\if :same_roles
  SELECT 'migrator, app and admin must be three different roles'::int;
\endif
\if :no_passwords
  SELECT 'MIGRATOR_PASSWORD and APP_PASSWORD must be set'::int;
\endif

\if :migrator_exists
\else
  CREATE ROLE :"migrator" LOGIN;
\endif
\if :app_exists
\else
  CREATE ROLE :"app" LOGIN;
\endif
-- neither role gets any special attribute; the app role is never a member of the owner.
-- SUPERUSER / REPLICATION / BYPASSRLS may only be named by an admin that has them itself (PG 16+;
-- Azure's admin is no superuser); a role without them can't get them from such an admin anyway,
-- and the check below fails if either role has one.
SELECT concat_ws(' ', CASE WHEN rolsuper THEN 'NOSUPERUSER' END,
                      CASE WHEN rolsuper OR rolreplication THEN 'NOREPLICATION' END,
                      CASE WHEN rolsuper OR rolbypassrls THEN 'NOBYPASSRLS' END) AS no_privileged
FROM pg_roles WHERE rolname = current_user
\gset
ALTER ROLE :"migrator" LOGIN NOCREATEDB NOCREATEROLE :no_privileged PASSWORD :'migrator_password';
ALTER ROLE :"app" LOGIN NOCREATEDB NOCREATEROLE NOINHERIT :no_privileged PASSWORD :'app_password';
\unset migrator_password
\unset app_password
SELECT EXISTS (SELECT 1 FROM pg_roles WHERE rolname IN (:'migrator', :'app')
               AND (rolsuper OR rolreplication OR rolbypassrls OR rolcreatedb OR rolcreaterole)) AS privileged
\gset
\if :privileged
  SELECT 'migrator/app must not have SUPERUSER, REPLICATION, BYPASSRLS, CREATEDB or CREATEROLE'::int;
\endif

SELECT pg_has_role(:'app', :'migrator', 'MEMBER') AS app_in_migrator \gset
\if :app_in_migrator
  REVOKE :"migrator" FROM :"app";
\endif

-- a non-superuser admin (Azure) needs SET on the migrator to hand ownership to it
\if :is_superuser
\else
  GRANT :"migrator" TO CURRENT_USER WITH INHERIT FALSE, SET TRUE;
\endif

-- the migrator owns the database → it owns schema public (pg_database_owner, PG 15+)
ALTER DATABASE :"db" OWNER TO :"migrator";
-- existing objects (a database migrated before the roles existed)
SELECT format('ALTER %s public.%I OWNER TO %I',
              CASE c.relkind WHEN 'S' THEN 'SEQUENCE' WHEN 'v' THEN 'VIEW'
                             WHEN 'm' THEN 'MATERIALIZED VIEW' ELSE 'TABLE' END, c.relname, :'migrator')
FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p', 'S', 'v', 'm')
  AND pg_get_userbyid(c.relowner) <> :'migrator'
  AND NOT EXISTS (SELECT 1 FROM pg_depend d WHERE d.objid = c.oid AND d.deptype IN ('a', 'i'))
\gexec

-- nobody else creates objects in public; the app connects but owns nothing.
-- As the new owner: a non-superuser admin no longer may grant on what it gave away.
SET ROLE :"migrator";
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT CONNECT ON DATABASE :"db" TO :"app";
GRANT USAGE ON SCHEMA public TO :"app";
RESET ROLE;
\echo roles ready: :migrator (owner, migrations), :app (application, DML only)
