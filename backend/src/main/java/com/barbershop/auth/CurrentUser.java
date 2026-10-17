package com.barbershop.auth;

import com.barbershop.common.error.ApiException;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The authenticated caller, read from the access token. Controllers declare a
 * {@code CurrentUser} parameter (resolved by {@link CurrentUserArgumentResolver});
 * every tenant-scoped query uses {@link #tenantId()} from the token.
 */
public record CurrentUser(UUID userId, UUID tenantId, Role role, String email, UUID staffId) {

    public static final String TENANT_CLAIM = "tenant";
    public static final String ROLE_CLAIM = "role";
    public static final String EMAIL_CLAIM = "email";
    public static final String STAFF_CLAIM = "staff";

    public static CurrentUser of(Jwt jwt) {
        if (jwt == null || jwt.getClaimAsString(TENANT_CLAIM) == null) {
            throw ApiException.unauthorized("auth.unauthorized");
        }
        String staff = jwt.getClaimAsString(STAFF_CLAIM);
        return new CurrentUser(UUID.fromString(jwt.getSubject()), UUID.fromString(jwt.getClaimAsString(TENANT_CLAIM)),
                Role.valueOf(jwt.getClaimAsString(ROLE_CLAIM)), jwt.getClaimAsString(EMAIL_CLAIM),
                staff == null ? null : UUID.fromString(staff));
    }

    public boolean is(Role r) {
        return role == r;
    }

    /** The barber's staff id; 403 if this account is not linked to a staff profile. */
    public UUID requireStaffId() {
        if (staffId == null) throw ApiException.forbidden("auth.forbidden");
        return staffId;
    }
}
