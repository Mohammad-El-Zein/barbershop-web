package com.barbershop.auth;

import com.barbershop.auth.JwtService.IssuedToken;
import com.barbershop.common.config.AppProperties;
import com.barbershop.common.error.ApiException;
import com.barbershop.tenant.Tenant;
import com.barbershop.tenant.TenantService;
import com.barbershop.token.TokenHasher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login, refresh and logout.
 *
 * <ul>
 *   <li><b>No user enumeration:</b> unknown email, wrong password and a locked
 *       account all return the same 401 {@code auth.invalid_credentials}, and an
 *       unknown email still costs one BCrypt check (dummy hash). "Email not
 *       verified" is only revealed after a correct password.</li>
 *   <li><b>Lockout:</b> {@code max-failed-logins} wrong passwords in a row lock
 *       the account for {@code lockout} (on top of the per-IP rate limit).</li>
 *   <li><b>Refresh rotation:</b> every refresh issues a new cookie token and
 *       revokes the old one. Presenting a rotated token again (outside a short
 *       grace window for parallel tabs) revokes the whole family — a stolen
 *       cookie gets at most one use.</li>
 * </ul>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    static final String REFRESH_PURPOSE = "REFRESH";
    /** Two tabs refreshing with the same cookie at the same moment is not an attack. */
    private static final Duration ROTATION_GRACE = Duration.ofSeconds(10);

    private final AppUserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;
    private final JwtEncoder encoder;
    private final TokenHasher hasher;
    private final TenantService tenants;
    private final AppProperties.Auth config;
    private final Clock clock;
    /** A real BCrypt hash checked when the email is unknown, so both paths cost the same time. */
    private final String dummyHash;

    public AuthService(AppUserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
                       JwtService jwt, JwtEncoder encoder, TokenHasher hasher, TenantService tenants,
                       AppProperties props, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
        this.encoder = encoder;
        this.hasher = hasher;
        this.tenants = tenants;
        this.config = props.auth();
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public record Session(IssuedToken access, String refreshToken, Instant refreshExpiresAt, AppUser user,
                          Tenant tenant) {}

    /** The failed-login counter must be committed even though we answer with an error. */
    @Transactional(noRollbackFor = ApiException.class)
    public Session login(String tenantSlug, String email, String password) {
        Tenant tenant = tenants.requireBySlug(tenantSlug);
        Instant now = Instant.now(clock);
        Optional<AppUser> found = users.findByTenantAndEmailForUpdate(tenant.getId(), email.trim());
        boolean passwordOk = passwordEncoder.matches(password, found.map(AppUser::getPasswordHash).orElse(dummyHash));
        if (found.isEmpty()) throw ApiException.unauthorized("auth.invalid_credentials");

        AppUser user = found.get();
        if (user.isLocked(now)) {
            log.info("Login attempt for locked account {}", user.getId());
            throw ApiException.unauthorized("auth.invalid_credentials");
        }
        if (!passwordOk) {
            user.recordFailedLogin(now, config.maxFailedLogins(), config.lockout());
            if (user.isLocked(now)) log.warn("Account {} locked after {} failed logins", user.getId(), config.maxFailedLogins());
            throw ApiException.unauthorized("auth.invalid_credentials");
        }
        if (!user.isEmailVerified()) throw ApiException.forbidden("auth.email_not_verified");

        user.recordLogin(now);
        return newSession(user, tenant, UUID.randomUUID(), now).session();
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Session refresh(String rawRefreshToken) {
        if (!TokenHasher.plausible(rawRefreshToken)) throw ApiException.unauthorized("auth.session_expired");
        Instant now = Instant.now(clock);
        RefreshToken current = refreshTokens.findByTokenHashForUpdate(hasher.hash(REFRESH_PURPOSE, rawRefreshToken))
                .orElseThrow(() -> ApiException.unauthorized("auth.session_expired"));

        if (current.getRevokedAt() != null) {
            boolean rotated = current.getReplacedBy() != null;
            if (rotated && current.getRevokedAt().plus(ROTATION_GRACE).isBefore(now)) {
                // a token that was already exchanged is used again → assume it was stolen
                refreshTokens.revokeFamily(current.getFamilyId(), now);
                log.warn("Refresh token reuse detected for user {} — session family revoked", current.getUserId());
            }
            throw ApiException.unauthorized("auth.session_expired");
        }
        if (!current.isActive(now)) throw ApiException.unauthorized("auth.session_expired");

        AppUser user = users.findById(current.getUserId())
                .orElseThrow(() -> ApiException.unauthorized("auth.session_expired"));
        Issued next = newSession(user, tenants.require(user.getTenantId()), current.getFamilyId(), now);
        current.rotate(next.refreshTokenId(), now);
        return next.session();
    }

    /** Revokes the session family of this cookie. Unknown/expired cookies are ignored (idempotent). */
    @Transactional
    public void logout(String rawRefreshToken) {
        if (!TokenHasher.plausible(rawRefreshToken)) return;
        refreshTokens.findByTokenHashForUpdate(hasher.hash(REFRESH_PURPOSE, rawRefreshToken))
                .ifPresent(t -> refreshTokens.revokeFamily(t.getFamilyId(), Instant.now(clock)));
    }

    /** A fresh session for an already authenticated user (e.g. right after a password change). */
    @Transactional
    public Session startSession(AppUser user) {
        return newSession(user, tenants.require(user.getTenantId()), UUID.randomUUID(), Instant.now(clock)).session();
    }

    /** Ends every session of a user (password reset, account deletion). */
    @Transactional
    public void revokeAllSessions(UUID userId) {
        refreshTokens.revokeAllForUser(userId, Instant.now(clock));
    }

    private Issued newSession(AppUser user, Tenant tenant, UUID familyId, Instant now) {
        String raw = hasher.generate();
        Instant expiresAt = now.plus(config.refreshTtl());
        RefreshToken stored = refreshTokens.save(
                new RefreshToken(user.getId(), familyId, hasher.hash(REFRESH_PURPOSE, raw), expiresAt, now));
        return new Issued(new Session(jwt.issue(user, encoder), raw, expiresAt, user, tenant), stored.getId());
    }

    /** A new session plus the id of its stored refresh token (needed to link the rotation). */
    private record Issued(Session session, UUID refreshTokenId) {}
}
