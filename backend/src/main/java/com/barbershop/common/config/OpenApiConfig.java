package com.barbershop.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI docs at /v3/api-docs (Swagger UI at /swagger-ui.html).
 * Phase 3 generates the frontend's typed API client from this single spec.
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI barbershopOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Barbershop Booking API")
                        .version("v1")
                        .description("""
                                Public booking API (services, staff, availability, bookings) and admin API (JWT).
                                Customer-facing texts are localized: `?lang=de|en` or the `Accept-Language`
                                header (default `de`). Times are ISO-8601 with the offset of the tenant's timezone.
                                Errors are RFC 9457 problem details with a stable `code`."""))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }
}
