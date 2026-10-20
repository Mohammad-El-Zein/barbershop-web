package com.barbershop.support;

import java.nio.file.Path;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Creates the production database roles in the test container with the REAL script
 * (infra/postgres/init-roles.sql, run with psql as the container's superuser), so the tests run
 * like production: Flyway as the migration role, the application as the DML-only role.
 */
public final class DbRoles {

    public static final String MIGRATOR = "barbershop_migrator";
    public static final String MIGRATOR_PASSWORD = "migrator-test-password";
    public static final String APP = "barbershop_app";
    public static final String APP_PASSWORD = "app-test-password";

    private static final Path SCRIPT = Path.of("..", "infra", "postgres", "init-roles.sql");

    private DbRoles() {}

    /** Runs the script like the db-init job does; returns everything psql printed (stdout + stderr). */
    public static String create(PostgreSQLContainer<?> pg) {
        return create(pg, MIGRATOR_PASSWORD, APP_PASSWORD);
    }

    /** The same with other passwords (a rotation); passed as-is, without a shell in between. */
    public static String create(PostgreSQLContainer<?> pg, String migratorPassword, String appPassword) {
        return create(pg, pg.getUsername(), pg.getDatabaseName(), migratorPassword, appPassword);
    }

    /** The same as another admin login (e.g. a non-superuser like Azure's) on another database. */
    public static String create(PostgreSQLContainer<?> pg, String admin, String database,
                                String migratorPassword, String appPassword) {
        try {
            pg.copyFileToContainer(MountableFile.forHostPath(SCRIPT), "/tmp/init-roles.sql");
            ExecResult r = pg.execInContainer("env",
                    "MIGRATOR_PASSWORD=" + migratorPassword, "APP_PASSWORD=" + appPassword,
                    "psql", "-X", "-q", "-v", "ON_ERROR_STOP=1",
                    "-U", admin, "-d", database,
                    "-v", "migrator=" + MIGRATOR, "-v", "app=" + APP, "-f", "/tmp/init-roles.sql");
            String output = r.getStdout() + r.getStderr();
            if (r.getExitCode() != 0) {
                throw new IllegalStateException("psql exit " + r.getExitCode() + ": " + output);
            }
            return output;
        } catch (Exception e) {
            throw new IllegalStateException("could not create the database roles from " + SCRIPT.toAbsolutePath(), e);
        }
    }
}
