package com.barbershop.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * Creates the production database roles in the test container with the REAL script
 * (infra/postgres/init-roles.sql, run as the container's superuser), so the tests run like
 * production: Flyway as the migration role, the application as the DML-only role.
 */
public final class DbRoles {

    public static final String MIGRATOR = "barbershop_migrator";
    public static final String MIGRATOR_PASSWORD = "migrator-test-password";
    public static final String APP = "barbershop_app";
    public static final String APP_PASSWORD = "app-test-password";

    private static final Path SCRIPT = Path.of("..", "infra", "postgres", "init-roles.sql");

    private DbRoles() {}

    public static void create(String jdbcUrl, String adminUser, String adminPassword) {
        try (Connection c = DriverManager.getConnection(jdbcUrl, adminUser, adminPassword);
             Statement st = c.createStatement()) {
            st.execute(psqlToJdbc(Files.readString(SCRIPT)));
        } catch (Exception e) {
            throw new IllegalStateException("could not create the database roles from " + SCRIPT.toAbsolutePath(), e);
        }
    }

    /** The script uses psql variables (:'name') and meta-commands (\set); JDBC gets plain SQL. */
    static String psqlToJdbc(String script) {
        StringBuilder sql = new StringBuilder();
        for (String line : script.split("\\R")) {
            if (!line.startsWith("\\")) sql.append(line).append('\n');
        }
        return sql.toString()
                .replace(":'migrator_password'", quote(MIGRATOR_PASSWORD))
                .replace(":'migrator'", quote(MIGRATOR))
                .replace(":'app_password'", quote(APP_PASSWORD))
                .replace(":'app'", quote(APP));
    }

    private static String quote(String s) {
        return "'" + s.replace("'", "''") + "'";
    }
}
