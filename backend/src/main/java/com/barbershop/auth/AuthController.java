package com.barbershop.auth;

import com.barbershop.auth.AuthDtos.LoginRequest;
import com.barbershop.auth.AuthDtos.SessionResponse;
import com.barbershop.common.config.AppProperties;
import com.barbershop.common.error.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login (per shop), refresh and logout. The access token (15 min) is returned in
 * the body and kept in memory by the frontend; the refresh token is an httpOnly
 * cookie limited to /api/auth, so JavaScript can never read it. Every endpoint that
 * sets this cookie lives under /api/auth (strict cookie clients such as .NET reject
 * a cookie whose path does not cover the URL that set it).
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth: session")
public class AuthController {

    private final AuthService auth;
    private final SessionResponses sessions;
    private final AppProperties props;

    public AuthController(AuthService auth, SessionResponses sessions, AppProperties props) {
        this.auth = auth;
        this.sessions = sessions;
        this.props = props;
    }

    @PostMapping("/{tenant}/login")
    @Operation(summary = "Login (customer, barber or admin) → access token + refresh cookie. The same 401 for "
            + "unknown email / wrong password / locked account; 403 auth.email_not_verified only after a correct password.")
    public ResponseEntity<SessionResponse> login(@PathVariable("tenant") String tenant,
                                                 @Valid @RequestBody LoginRequest req) {
        return sessions.of(auth.login(tenant, req.email(), req.password()));
    }

    @PostMapping("/refresh")
    @Operation(summary = "New access token from the refresh cookie (rotates the cookie; reuse revokes the session)")
    public ResponseEntity<SessionResponse> refresh(HttpServletRequest request,
                                                   @CookieValue(name = "${app.auth.refresh-cookie-name}", required = false)
                                                   String refreshToken) {
        requireAllowedOrigin(request);
        if (refreshToken == null) throw ApiException.unauthorized("auth.session_expired");
        return sessions.of(auth.refresh(refreshToken));
    }

    @PostMapping("/logout")
    @Operation(summary = "Ends this session (revokes the refresh token family, clears the cookie). Idempotent.")
    public ResponseEntity<Void> logout(HttpServletRequest request,
                                       @CookieValue(name = "${app.auth.refresh-cookie-name}", required = false)
                                       String refreshToken) {
        requireAllowedOrigin(request);
        if (refreshToken != null) auth.logout(refreshToken);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, sessions.clearCookie()).build();
    }

    /**
     * Defence in depth on the two cookie-authenticated endpoints: a browser always
     * sends Origin on cross-origin POSTs, so a foreign site is rejected even if
     * SameSite were not honoured. Requests without Origin (curl, server-side) pass.
     */
    private void requireAllowedOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin != null && !SecurityConfig.allowedOrigins(props).contains(origin)) {
            throw ApiException.forbidden("auth.forbidden");
        }
    }
}
