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
 * Admin access tokens: HS256 JWTs signed with APP_JWT_SECRET (≥ 32 bytes).
 * Claims: sub = admin id, tenant, role, email, iss, iat, exp.
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

    public IssuedToken issue(AdminUser admin, JwtEncoder encoder) {
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(config.ttl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(config.issuer())
                .subject(admin.getId().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(AdminPrincipal.TENANT_CLAIM, admin.getTenantId().toString())
                .claim(AdminPrincipal.ROLE_CLAIM, admin.getRole())
                .claim(AdminPrincipal.EMAIL_CLAIM, admin.getEmail())
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new IssuedToken(token, expiresAt);
    }

    public record IssuedToken(String token, Instant expiresAt) {}
}
