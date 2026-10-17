package com.barbershop.auth;

import com.barbershop.auth.AuthDtos.SessionResponse;
import com.barbershop.auth.AuthDtos.UserView;
import com.barbershop.common.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Turns a session into the HTTP response: access token in the body, refresh
 * token as httpOnly / SameSite / path-limited cookie that JavaScript cannot read.
 */
@Component
public class SessionResponses {

    private final AppProperties.Auth config;
    private final Clock clock;

    public SessionResponses(AppProperties props, Clock clock) {
        this.config = props.auth();
        this.clock = clock;
    }

    public ResponseEntity<SessionResponse> of(AuthService.Session s) {
        Duration maxAge = Duration.between(Instant.now(clock), s.refreshExpiresAt());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(s.refreshToken(), maxAge).toString())
                .body(new SessionResponse(s.access().token(), "Bearer", s.access().expiresAt(),
                        UserView.of(s.user(), s.tenant())));
    }

    public String clearCookie() {
        return cookie("", Duration.ZERO).toString();
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(config.refreshCookieName(), value)
                .httpOnly(true)
                .secure(config.cookieSecure())
                .sameSite(config.cookieSameSite())
                .path(config.refreshCookiePath())
                .maxAge(maxAge)
                .build();
    }
}
