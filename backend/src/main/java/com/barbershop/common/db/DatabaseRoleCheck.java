package com.barbershop.common.db;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Least privilege for the application's database login (DB_USERNAME). Runs after Flyway and
 * before the web server accepts requests. With the {@code prod} profile the backend refuses
 * to start when the login is a superuser, can create roles or databases, bypasses row-level
 * security, owns the database, owns (or is a member of the owner of) a table, or may create
 * objects in the schema — i.e. when it is the admin or the migration/owner role. Outside
 * prod the problems are logged as warnings.
 */
@Component
public class DatabaseRoleCheck implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(DatabaseRoleCheck.class);

    private final JdbcTemplate jdbc;
    private final Environment env;

    public DatabaseRoleCheck(JdbcTemplate jdbc, Environment env) {
        this.jdbc = jdbc;
        this.env = env;
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<String> problems = check(jdbc);
        String user = jdbc.queryForObject("select current_user", String.class);
        if (problems.isEmpty()) {
            log.info("Database login '{}' is a least-privilege application role", user);
            return;
        }
        if (env.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("Refusing to start with the prod profile: the database login '" + user
                    + "' (DB_USERNAME) is privileged:\n - " + String.join("\n - ", problems)
                    + "\nConnect with the application role and run migrations with DB_MIGRATION_USERNAME"
                    + " (docs/DEPLOYMENT.md → Database roles).");
        }
        problems.forEach(p -> log.warn("Database login '{}' is not least-privilege: {}", user, p));
    }

    /** Everything that is wrong with the current login; empty = least privilege. */
    public static List<String> check(JdbcTemplate jdbc) {
        Map<String, Object> r = jdbc.queryForMap("""
                select r.rolsuper, r.rolcreaterole, r.rolcreatedb, r.rolbypassrls, r.rolreplication,
                       pg_has_role(current_user, d.datdba, 'MEMBER') as db_owner,
                       has_schema_privilege(current_user, 'public', 'CREATE') as can_create,
                       (select count(*) from pg_class c join pg_namespace n on n.oid = c.relnamespace
                         where n.nspname = 'public' and c.relkind in ('r', 'p', 'S', 'v', 'm')
                           and pg_has_role(current_user, c.relowner, 'MEMBER')) as owned
                from pg_roles r, pg_database d
                where r.rolname = current_user and d.datname = current_database()""");
        List<String> problems = new ArrayList<>();
        if (Boolean.TRUE.equals(r.get("rolsuper"))) problems.add("is a superuser");
        if (Boolean.TRUE.equals(r.get("rolcreaterole"))) problems.add("may create roles (admin login)");
        if (Boolean.TRUE.equals(r.get("rolcreatedb"))) problems.add("may create databases");
        if (Boolean.TRUE.equals(r.get("rolbypassrls"))) problems.add("bypasses row-level security");
        if (Boolean.TRUE.equals(r.get("rolreplication"))) problems.add("has the replication privilege");
        if (Boolean.TRUE.equals(r.get("db_owner"))) problems.add("owns the database (or is a member of its owner)");
        if (Boolean.TRUE.equals(r.get("can_create"))) problems.add("may create objects in schema public (DDL)");
        long owned = ((Number) r.get("owned")).longValue();
        if (owned > 0) problems.add("owns " + owned + " table(s)/sequence(s) (the migration/owner role)");
        return problems;
    }
}
