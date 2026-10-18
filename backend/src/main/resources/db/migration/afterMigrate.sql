-- Flyway callback: runs as the migration role after EVERY migrate (also when nothing was
-- pending), so tables added by new migrations are always covered. Idempotent.
-- The application role (placeholder app_role = DB_USERNAME) gets DML only: no DDL, no
-- ownership; on the Flyway history table only SELECT (the backend validates the schema).
DO $$
DECLARE
    app text := '${app_role}';
BEGIN
    IF app = current_user THEN
        RAISE NOTICE 'Application and migration login are the same (%): no separate grants', app;
        RETURN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = app) THEN
        RAISE EXCEPTION 'Application role "%" does not exist — create it first (infra/postgres/init-roles.sql)', app;
    END IF;
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO %I', current_database(), app);
    EXECUTE format('GRANT USAGE ON SCHEMA public TO %I', app);
    EXECUTE format('REVOKE CREATE ON SCHEMA public FROM %I', app);
    EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO %I', app);
    EXECUTE format('GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO %I', app);
    EXECUTE format('REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON TABLE public.%I FROM %I', '${flyway:table}', app);
END
$$;
