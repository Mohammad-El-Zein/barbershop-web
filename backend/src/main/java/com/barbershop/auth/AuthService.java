package com.barbershop.auth;

import com.barbershop.auth.JwtService.IssuedToken;
import com.barbershop.common.error.ApiException;
import com.barbershop.tenant.Tenant;
import com.barbershop.tenant.TenantService;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin login: verifies the BCrypt hash and issues a JWT. */
@Service
public class AuthService {

    private final AdminUserRepository admins;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;
    private final JwtEncoder encoder;
    private final TenantService tenants;
    private final Clock clock;
    /** A real BCrypt hash checked when the email is unknown, so both paths cost the same time. */
    private final String dummyHash;

    public AuthService(AdminUserRepository admins, PasswordEncoder passwordEncoder, JwtService jwt,
                       JwtEncoder encoder, TenantService tenants, Clock clock) {
        this.admins = admins;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
        this.encoder = encoder;
        this.tenants = tenants;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public LoginResult login(String email, String password) {
        Optional<AdminUser> found = admins.findByEmailIgnoreCase(email.trim());
        boolean ok = passwordEncoder.matches(password, found.map(AdminUser::getPasswordHash).orElse(dummyHash));
        if (found.isEmpty() || !ok) throw ApiException.unauthorized("auth.invalid_credentials");
        AdminUser admin = found.get();
        admin.recordLogin(Instant.now(clock));
        Tenant tenant = tenants.require(admin.getTenantId());
        return new LoginResult(jwt.issue(admin, encoder), admin, tenant);
    }

    public record LoginResult(IssuedToken token, AdminUser admin, Tenant tenant) {}
}
