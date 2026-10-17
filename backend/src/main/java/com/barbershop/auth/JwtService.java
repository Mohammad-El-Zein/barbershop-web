package com.barbershop.auth;

import com.barbershop.common.config.AppProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Short-lived access tokens (default 15 min): HS256 JWTs signed with
 * APP_JWT_SECRET (≥ 32 bytes). Claims: sub = user id, tenant, role, email,
 * staff (barbers only), iss, iat, exp. Long sessions use the refresh cookie.
 */
@Configuration
public class JwtService {

    private final AppProperties.Jwt config;
    private final SecretKey key;
    private final Clock clock;

    public JwtService(AppProperties props, Clock clock) {
        this.config = props.jwt();
        byte[] secret = config.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("APP_JWT_SECRET must be at least 32 bytes (e.g. `openssl rand -base64 48`)");
        }
        this.key = new SecretKeySpec(secret, "HmacSHA256");
        this.clock = clock;
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(config.issuer())));
        return decoder;
    }

    public IssuedToken issue(AppUser user, JwtEncoder encoder) {
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(config.accessTtl());
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(config.issuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(CurrentUser.TENANT_CLAIM, user.getTenantId().toString())
                .claim(CurrentUser.ROLE_CLAIM, user.getRole().name())
                .claim(CurrentUser.EMAIL_CLAIM, user.getEmail());
        if (user.getStaffId() != null) claims.claim(CurrentUser.STAFF_CLAIM, user.getStaffId().toString());
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                claims.build())).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }

    public record IssuedToken(String token, Instant expiresAt) {}
}
