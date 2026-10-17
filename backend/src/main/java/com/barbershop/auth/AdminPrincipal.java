package com.barbershop.auth;

import com.barbershop.common.error.ApiException;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** Reads the admin's tenant from the JWT; every admin query is scoped to it. */
public final class AdminPrincipal {

    public static final String TENANT_CLAIM = "tenant";
    public static final String ROLE_CLAIM = "role";
    public static final String EMAIL_CLAIM = "email";

    private AdminPrincipal() {}

    public static UUID tenantId(Jwt jwt) {
        String tenant = jwt == null ? null : jwt.getClaimAsString(TENANT_CLAIM);
        if (tenant == null) throw ApiException.unauthorized("auth.unauthorized");
        return UUID.fromString(tenant);
    }
}
