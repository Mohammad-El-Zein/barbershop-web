package com.barbershop.common.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Two database logins (docs/DEPLOYMENT.md → Database roles):
 * <ul>
 *   <li>migration role (DB_MIGRATION_USERNAME, spring.flyway.user): owns the schema, runs the
 *       Flyway migrations; the afterMigrate callback grants the application role its DML rights;</li>
 *   <li>application role (DB_USERNAME): the connection pool — SELECT/INSERT/UPDATE/DELETE only.</li>
 * </ul>
 * APP_DB_MIGRATE=true (default, local development): migrate on start. false (production,
 * where a separate migration job runs first): only validate — the backend refuses to start
 * while migrations are pending or the schema differs.
 */
@Configuration
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    @Bean
    FlywayMigrationStrategy flywayMigrationStrategy(@Value("${app.db.migrate:true}") boolean migrate) {
        return flyway -> {
            if (migrate) {
                flyway.migrate();
            } else {
                log.info("APP_DB_MIGRATE=false: validating the schema only (migrations run as a separate job)");
                flyway.validate();
            }
        };
    }
}
