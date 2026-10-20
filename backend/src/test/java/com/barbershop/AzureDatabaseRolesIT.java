package com.barbershop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.barbershop.common.db.DatabaseRoleCheck;
import com.barbershop.support.DbRoles;
import java.util.Map;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * infra/postgres/init-roles.sql on a server set up like Azure Database for PostgreSQL Flexible
 * Server: the admin login is no superuser (CREATEROLE, CREATEDB, member of azure_pg_admin), and
 * the database and schema public belong to azure_pg_admin instead of pg_database_owner. There a
 * grant by the wrong role is only a WARNING; Flyway then failed with "permission denied for schema
 * public". Its own container: in PG 16+ a non-superuser admin may only change roles it created.
 */
class AzureDatabaseRolesIT {

    private static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:17-alpine");
    private static final String ADMIN = "barbershop_admin";
    private static final String DB = "barbershop";

    @BeforeAll
    static void azureLikeServer() {
        PG.start();
        JdbcTemplate superuser = new JdbcTemplate(new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()));
        superuser.execute("CREATE ROLE azure_pg_admin NOLOGIN");
        superuser.execute("CREATE ROLE " + ADMIN + " LOGIN CREATEROLE CREATEDB PASSWORD 'admin-test-password'");
        superuser.execute("GRANT azure_pg_admin TO " + ADMIN);
        superuser.execute("CREATE DATABASE " + DB + " OWNER azure_pg_admin");
        asSuperuser().execute("ALTER SCHEMA public OWNER TO azure_pg_admin");
        asSuperuser().execute("REVOKE CREATE ON SCHEMA public FROM PUBLIC");
    }

    @AfterAll
    static void stop() {
        PG.stop();
    }

    private static String url() {
        return "jdbc:postgresql://" + PG.getHost() + ":" + PG.getFirstMappedPort() + "/" + DB;
    }

    private static JdbcTemplate as(String user, String password) {
        return new JdbcTemplate(new DriverManagerDataSource(url(), user, password));
    }

    private static JdbcTemplate asSuperuser() {
        return as(PG.getUsername(), PG.getPassword());
    }

    private static String initRoles() {
        return DbRoles.create(PG, ADMIN, DB, DbRoles.MIGRATOR_PASSWORD, DbRoles.APP_PASSWORD);
    }

    /** What the migrate job does: Flyway with the real migrations as the migrator. */
    private static void migrate() {
        Flyway.configure()
                .dataSource(url(), DbRoles.MIGRATOR, DbRoles.MIGRATOR_PASSWORD)
                .locations("classpath:db/migration")
                .placeholders(Map.of("app_role", DbRoles.APP))
                .load()
                .migrate();
    }

    private static void assertNoPasswordAndNoWarning(String output) {
        assertThat(output).doesNotContain(DbRoles.MIGRATOR_PASSWORD, DbRoles.APP_PASSWORD, "admin-test-password")
                .doesNotContain("WARNING", "NOTICE")
                .contains("roles ready: " + DbRoles.MIGRATOR);
    }

    @Test
    void theMigratorCanMigrateAndTheAppGetsDmlOnly() {
        assertThat(asSuperuser().queryForObject(
                "select nspowner::regrole::text from pg_namespace where nspname = 'public'", String.class))
                .isEqualTo("azure_pg_admin");

        assertNoPasswordAndNoWarning(initRoles());
        migrate();

        JdbcTemplate app = as(DbRoles.APP, DbRoles.APP_PASSWORD);
        assertThat(app.queryForObject("select current_user", String.class)).isEqualTo(DbRoles.APP);
        assertThat(app.queryForObject("select count(*) from tenant", Long.class)).isNotNull();
        assertThat(app.queryForObject("select count(*) from flyway_schema_history", Long.class)).isPositive();
        assertThat(DatabaseRoleCheck.check(app)).isEmpty();
        assertDenied(() -> app.execute("create table hack (id int)"), "permission denied for schema public");
        assertDenied(() -> app.update("delete from flyway_schema_history"), "permission denied");
        assertThat(as(DbRoles.MIGRATOR, DbRoles.MIGRATOR_PASSWORD).queryForList(
                "select distinct tableowner from pg_tables where schemaname = 'public'", String.class))
                .containsExactly(DbRoles.MIGRATOR);

        // every run of db-init and migrate is repeatable
        assertNoPasswordAndNoWarning(initRoles());
        migrate();
        assertThat(DatabaseRoleCheck.check(app)).isEmpty();
        assertThat(app.queryForObject("select count(*) from tenant", Long.class)).isNotNull();
    }

    @Test
    void neverReportsReadyWhenTheSchemaRightsAreWrong() {
        initRoles();
        asSuperuser().execute("GRANT CREATE ON SCHEMA public TO " + DbRoles.APP);
        try {
            assertThatThrownBy(AzureDatabaseRolesIT::initRoles).rootCause().hasMessageContaining("psql exit 3")
                    .hasMessageContaining("the migrator needs CREATE, the app USAGE and CONNECT but no CREATE")
                    .hasMessageNotContaining("roles ready")
                    .hasMessageNotContaining(DbRoles.MIGRATOR_PASSWORD)
                    .hasMessageNotContaining(DbRoles.APP_PASSWORD);
        } finally {
            asSuperuser().execute("REVOKE CREATE ON SCHEMA public FROM " + DbRoles.APP);
        }
    }

    private static void assertDenied(ThrowingCallable call, String message) {
        assertThatThrownBy(call).extracting(NestedExceptionUtils::getMostSpecificCause)
                .extracting(Throwable::getMessage).asString().contains(message);
    }
}
