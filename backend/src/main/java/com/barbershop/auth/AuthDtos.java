package com.barbershop.auth;

import com.barbershop.tenant.Tenant;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** API shapes of login / session handling. */
public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(
            @NotBlank(message = "{validation.required}") @Email(message = "{validation.email}")
            @Size(max = 254, message = "{validation.size.max}") String email,
            @NotBlank(message = "{validation.required}") @Size(max = 200, message = "{validation.size.max}") String password) {}

    public record TenantRef(UUID id, String slug, String name, String timezone) {
        public static TenantRef of(Tenant t) {
            return new TenantRef(t.getId(), t.getSlug(), t.getName(), t.getTimezone());
        }
    }

    /** mustChangePassword: signed in with a start password from the admin → choose an own one first. */
    public record UserView(UUID id, String email, String name, String phone, String lang, Role role, UUID staffId,
                           boolean emailVerified, boolean mustChangePassword, TenantRef tenant) {
        public static UserView of(AppUser u, Tenant t) {
            return new UserView(u.getId(), u.getEmail(), u.getName(), u.getPhone(), u.getLocale(), u.getRole(),
                    u.getStaffId(), u.isEmailVerified(), u.isMustChangePassword(), TenantRef.of(t));
        }
    }

    /** The refresh token is NOT in the body — it is set as httpOnly cookie. */
    public record SessionResponse(String accessToken, String tokenType, Instant expiresAt, UserView user) {}
}
