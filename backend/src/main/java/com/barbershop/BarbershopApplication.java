package com.barbershop;

import java.util.Arrays;
import java.util.stream.Stream;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Barbershop booking API.
 *
 * <p>Packages are organised by feature: tenant, service, staff, availability,
 * booking, mail, auth — plus {@code common} for cross-cutting concerns
 * (configuration, i18n, errors, rate limiting, database).
 *
 * <p>{@code APP_MIGRATE_ONLY=true}: one-off migration run (production deploys, see
 * docs/DEPLOYMENT.md → Database roles). Flyway migrates with DB_MIGRATION_USERNAME, then
 * the process exits — no web server, no scheduled jobs. The long-running backend then
 * only needs the least-privilege application login.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
public class BarbershopApplication {

    public static void main(String[] args) {
        if (Boolean.parseBoolean(System.getenv("APP_MIGRATE_ONLY"))) {
            System.exit(migrate(args));
        }
        SpringApplication.run(BarbershopApplication.class, args);
    }

    /** The migration run: Flyway (migration role) + the start-up checks, no web server; exit code. */
    public static int migrate(String... args) {
        // as command-line arguments: they win over environment variables (APP_DB_MIGRATE=false)
        String[] migrateArgs = Stream.concat(
                Arrays.stream(args).filter(a -> !a.startsWith("--app.db.migrate=") && !a.startsWith("--app.jobs.enabled=")),
                Stream.of("--app.db.migrate=true", "--app.jobs.enabled=false")).toArray(String[]::new);
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(BarbershopApplication.class)
                .web(WebApplicationType.NONE)
                .run(migrateArgs);
        return SpringApplication.exit(ctx);
    }
}
