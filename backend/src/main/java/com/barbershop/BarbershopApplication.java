package com.barbershop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Barbershop booking API.
 *
 * <p>Packages are organised by feature: tenant, service, staff, availability,
 * booking, mail, auth — plus {@code common} for cross-cutting concerns
 * (configuration, i18n, errors, rate limiting).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
public class BarbershopApplication {

    public static void main(String[] args) {
        SpringApplication.run(BarbershopApplication.class, args);
    }
}
