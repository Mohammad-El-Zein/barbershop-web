package com.barbershop.common.config;

import com.barbershop.auth.CurrentUser;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI docs at /v3/api-docs (Swagger UI at /swagger-ui.html).
 * Phase 3 generates the frontend's typed API client from this single spec.
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER = "bearerAuth";

    static {
        // resolved from the JWT, not a request parameter
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(CurrentUser.class);
    }

    @Bean
    public OpenAPI barbershopOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Barbershop Booking API")
                        .version("v2")
                        .description("""
                                Public booking API (services, staff, availability, booking requests, email-link
                                pages, demo payments), customer account API (/api/me), barber API (/api/barber)
                                and admin API (/api/admin). Access tokens are short-lived JWTs (Bearer); the
                                refresh token is an httpOnly cookie on /api/auth.
                                Customer-facing texts are localized: `?lang=de|en` or the `Accept-Language`
                                header (default `de`). Times are ISO-8601 with the offset of the tenant's timezone.
                                Errors are RFC 9457 problem details with a stable `code`.
                                Payments: DEMO MODE ONLY — no real payment provider is connected."""))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }
}
