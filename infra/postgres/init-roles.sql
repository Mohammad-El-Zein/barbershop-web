-- Database roles for the barbershop backend (docs/DEPLOYMENT.md → Database roles).
-- Run ONCE per database as the server admin (local/VPS: the Postgres superuser; Azure: the
-- Flexible Server admin). Idempotent: running it again updates passwords and ownership.
--
--   psql -v migrator=barbershop_migrator -v migrator_password=… \
--        -v app=barbershop_app -v app_password=… -d barbershop -f init-roles.sql
--
--   migrator  owns the database, the schema and all tables; used ONLY by Flyway (DDL)
--   app       the backend's connection pool: DML only (grants come from Flyway's afterMigrate)
-- The admin login itself is not used by the application.
\set ON_ERROR_STOP on

SELECT set_config('init.migrator', :'migrator', false),
       set_config('init.migrator_password', :'migrator_password', false),
       set_config('init.app', :'app', false),
       set_config('init.app_password', :'app_password', false);

DO $$
DECLARE
    m  text := current_setting('init.migrator');
    a  text := current_setting('init.app');
    su boolean := (SELECT rolsuper FROM pg_roles WHERE rolname = current_user);
    o  record;
BEGIN
    IF m = a OR m = current_user OR a = current_user THEN
        RAISE EXCEPTION 'migrator, app and admin must be three different roles';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = m) THEN
        EXECUTE format('CREATE ROLE %I LOGIN', m);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = a) THEN
        EXECUTE format('CREATE ROLE %I LOGIN', a);
    END IF;
    -- neither role gets any special attribute; the app role is never a member of the owner
    EXECUTE format('ALTER ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD %L',
                   m, current_setting('init.migrator_password'));
    EXECUTE format('ALTER ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS NOINHERIT PASSWORD %L',
                   a, current_setting('init.app_password'));
    IF pg_has_role(a, m, 'MEMBER') THEN
        EXECUTE format('REVOKE %I FROM %I', m, a);
    END IF;

    -- a non-superuser admin (Azure) needs SET on the migrator to hand ownership to it
    IF NOT su THEN
        EXECUTE format('GRANT %I TO %I WITH INHERIT FALSE, SET TRUE', m, current_user);
    END IF;

    -- the migrator owns the database → it owns schema public (pg_database_owner, PG 15+)
    EXECUTE format('ALTER DATABASE %I OWNER TO %I', current_database(), m);
    -- existing objects (a database migrated before the roles existed)
    FOR o IN
        SELECT c.relname, c.relkind FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p', 'S', 'v', 'm')
          AND pg_get_userbyid(c.relowner) <> m
          AND NOT EXISTS (SELECT 1 FROM pg_depend d WHERE d.objid = c.oid AND d.deptype IN ('a', 'i'))
    LOOP
        EXECUTE format('ALTER %s public.%I OWNER TO %I',
                       CASE o.relkind WHEN 'S' THEN 'SEQUENCE' WHEN 'v' THEN 'VIEW'
                                      WHEN 'm' THEN 'MATERIALIZED VIEW' ELSE 'TABLE' END, o.relname, m);
    END LOOP;

    -- nobody else creates objects in public; the app connects but owns nothing
    EXECUTE 'REVOKE CREATE ON SCHEMA public FROM PUBLIC';
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO %I', current_database(), a);
    EXECUTE format('GRANT USAGE ON SCHEMA public TO %I', a);
    RAISE NOTICE 'roles ready: % (owner, migrations), % (application, DML only)', m, a;
END
$$;
