package com.barbershop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.barbershop.common.db.DatabaseRoleCheck;
import com.barbershop.support.AbstractIntegrationTest;
import com.barbershop.support.DbRoles;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Least privilege: the backend's pool connects with the DML-only application role, Flyway with
 * the owner role (both created by infra/postgres/init-roles.sql). The prod profile refuses a
 * privileged application login.
 */
class DatabaseRolesIT extends AbstractIntegrationTest {

    private static JdbcTemplate as(String user, String password) {
        return new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), user, password));
    }

    /** Spring wraps the Postgres error; its message is in the innermost cause. */
    private static void assertDenied(ThrowingCallable call, String postgresMessage) {
        assertThatThrownBy(call).extracting(NestedExceptionUtils::getMostSpecificCause)
                .extracting(Throwable::getMessage).asString().contains(postgresMessage);
    }

    @Test
    void theApplicationRunsAsTheDmlOnlyRole() {
        assertThat(jdbc.queryForObject("select current_user", String.class)).isEqualTo(DbRoles.APP);
        assertThat(DatabaseRoleCheck.check(jdbc)).isEmpty();
        // tables belong to the migration role
        assertThat(jdbc.queryForList("select distinct tableowner from pg_tables where schemaname = 'public'",
                String.class)).containsExactly(DbRoles.MIGRATOR);
    }

    @Test
    void theApplicationRoleCannotRunDdlOrTouchTheMigrationHistory() {
        assertDenied(() -> jdbc.execute("create table hack (id int)"), "permission denied");
        assertDenied(() -> jdbc.execute("alter table booking add column hack int"), "must be owner");
        assertDenied(() -> jdbc.execute("drop table booking"), "must be owner");
        assertDenied(() -> jdbc.execute("truncate booking"), "permission denied");
        assertDenied(() -> jdbc.update("delete from flyway_schema_history"), "permission denied");
        // reading the history is allowed: the production backend validates the schema
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history", Long.class)).isPositive();
    }

    @Test
    void ownerAndSuperuserLoginsAreReported() {
        assertThat(DatabaseRoleCheck.check(as(DbRoles.MIGRATOR, DbRoles.MIGRATOR_PASSWORD)))
                .anyMatch(p -> p.contains("owns the database"))
                .anyMatch(p -> p.contains("table(s)"));
        assertThat(DatabaseRoleCheck.check(as(POSTGRES.getUsername(), POSTGRES.getPassword())))
                .anyMatch(p -> p.contains("superuser"));
    }

    @Test
    void prodRefusesToStartWithTheOwnerLogin() {
        assertThatThrownBy(() -> new SpringApplicationBuilder(BarbershopApplication.class)
                .profiles("prod", "demo")
                .run("--server.port=0",
                        "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "--spring.datasource.username=" + DbRoles.MIGRATOR,
                        "--spring.datasource.password=" + DbRoles.MIGRATOR_PASSWORD,
                        "--spring.flyway.user=" + DbRoles.MIGRATOR,
                        "--spring.flyway.password=" + DbRoles.MIGRATOR_PASSWORD,
                        "--spring.mail.host=localhost",
                        "--spring.mail.port=" + MAIL.getSmtp().getPort(),
                        "--app.jwt.secret=" + UUID.randomUUID() + UUID.randomUUID(),
                        "--app.jobs.enabled=false",
                        "--app.frontend-base-url=https://shop.example",
                        "--app.cors.allowed-origins=https://shop.example",
                        "--app.auth.cookie-secure=true",
                        "--app.captcha.allow-none-in-prod=true",
                        "--payment.demo.allow-in-prod=true")
                .close())
                .extracting(NestedExceptionUtils::getMostSpecificCause)
                .extracting(Throwable::getMessage).asString()
                .contains("Refusing to start", "database login '" + DbRoles.MIGRATOR + "'", "owns the database");
    }

    @Test
    void theMigrationRunStartsWithoutAWebServerAndExitsCleanly() {
        // what the migrate job / compose service runs (APP_MIGRATE_ONLY=true), with the prod profile
        int exit = BarbershopApplication.migrate(
                "--spring.profiles.active=prod,demo",
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + DbRoles.APP,
                "--spring.datasource.password=" + DbRoles.APP_PASSWORD,
                "--spring.flyway.user=" + DbRoles.MIGRATOR,
                "--spring.flyway.password=" + DbRoles.MIGRATOR_PASSWORD,
                "--app.db.migrate=false", // overridden: the migration run always migrates
                "--spring.mail.host=localhost",
                "--spring.mail.port=" + MAIL.getSmtp().getPort(),
                "--app.jwt.secret=" + UUID.randomUUID() + UUID.randomUUID(),
                "--app.frontend-base-url=https://shop.example",
                "--app.cors.allowed-origins=https://shop.example",
                "--app.auth.cookie-secure=true",
                "--app.captcha.allow-none-in-prod=true",
                "--payment.demo.allow-in-prod=true");
        assertThat(exit).isZero();
        assertThat(DatabaseRoleCheck.check(jdbc)).isEmpty();
    }

    @Test
    void theRoleScriptIsIdempotentAndNeverPrintsAPassword() {
        String output = DbRoles.create(POSTGRES);
        // the job log of the Azure db-init job is exactly this output
        assertThat(output).doesNotContain(DbRoles.MIGRATOR_PASSWORD, DbRoles.APP_PASSWORD)
                .contains("roles ready: " + DbRoles.MIGRATOR);
        assertThat(DatabaseRoleCheck.check(jdbc)).isEmpty();
        assertThat(DatabaseRoleCheck.check(as(DbRoles.APP, DbRoles.APP_PASSWORD))).isEmpty();
    }

    /** Password rotation: every run sets both passwords to exactly the env values, also for existing roles. */
    @Test
    void everyRunSetsBothPasswordsExactlyAsGiven() {
        String special = " '\"\\%&+/= $$x$(Y) ";
        String migratorA = "migrator-A" + special, appA = "app-A" + special;
        String migratorB = "migrator-B" + special, appB = "app-B" + special;
        try {
            String outputA = DbRoles.create(POSTGRES, migratorA, appA);
            assertThat(as(DbRoles.MIGRATOR, migratorA).queryForObject("select current_user", String.class))
                    .isEqualTo(DbRoles.MIGRATOR);
            assertThat(as(DbRoles.APP, appA).queryForObject("select current_user", String.class))
                    .isEqualTo(DbRoles.APP);

            String outputB = DbRoles.create(POSTGRES, migratorB, appB);
            assertThat(as(DbRoles.MIGRATOR, migratorB).queryForObject("select current_user", String.class))
                    .isEqualTo(DbRoles.MIGRATOR);
            assertThat(as(DbRoles.APP, appB).queryForObject("select current_user", String.class))
                    .isEqualTo(DbRoles.APP);
            assertDenied(() -> as(DbRoles.MIGRATOR, migratorA).execute("select 1"), "password authentication failed");
            assertDenied(() -> as(DbRoles.APP, appA).execute("select 1"), "password authentication failed");

            assertThat(outputA + outputB).doesNotContain(migratorA, appA, migratorB, appB, "migrator-A", "app-B")
                    .doesNotContain("NOTICE", "WARNING");
        } finally {
            DbRoles.create(POSTGRES); // the other tests use the default passwords
        }
    }
}
