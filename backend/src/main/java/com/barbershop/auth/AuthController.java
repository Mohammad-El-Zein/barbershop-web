package com.barbershop.auth;

import com.barbershop.common.config.OpenApiConfig;
import com.barbershop.tenant.Tenant;
import com.barbershop.tenant.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin: auth")
public class AuthController {

    private final AuthService auth;
    private final TenantService tenants;

    public AuthController(AuthService auth, TenantService tenants) {
        this.auth = auth;
        this.tenants = tenants;
    }

    @PostMapping("/auth/login")
    @Operation(summary = "Admin login → JWT (rate-limited per IP)")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        AuthService.LoginResult r = auth.login(req.email(), req.password());
        return new LoginResponse(r.token().token(), "Bearer", r.token().expiresAt(), r.admin().getEmail(),
                r.admin().getRole(), TenantRef.of(r.tenant()));
    }

    @GetMapping("/me")
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @Operation(summary = "The logged-in admin")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        Tenant tenant = tenants.require(AdminPrincipal.tenantId(jwt));
        return new MeResponse(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString(AdminPrincipal.EMAIL_CLAIM),
                jwt.getClaimAsString(AdminPrincipal.ROLE_CLAIM), TenantRef.of(tenant), jwt.getExpiresAt());
    }

    public record LoginRequest(
            @NotBlank(message = "{validation.required}") @Email(message = "{validation.email}") String email,
            @NotBlank(message = "{validation.required}") @Size(max = 200, message = "{validation.size.max}") String password) {}

    public record TenantRef(UUID id, String slug, String name, String timezone) {
        static TenantRef of(Tenant t) {
            return new TenantRef(t.getId(), t.getSlug(), t.getName(), t.getTimezone());
        }
    }

    public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, String email, String role,
                                TenantRef tenant) {}

    public record MeResponse(UUID id, String email, String role, TenantRef tenant, Instant expiresAt) {}
}
