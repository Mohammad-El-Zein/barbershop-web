package com.barbershop.common.ratelimit;

import com.barbershop.common.config.AppProperties;
import com.barbershop.common.error.ProblemFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Token-bucket rate limiting per client IP for the abuse-prone public POSTs:
 * creating bookings, login, the endpoints that send emails (register, forgot
 * password, resend verification), email-link actions and the token refresh.
 * (Login additionally locks an account after repeated failures, see AuthService.)
 * Buckets live in a {@link RateLimitStore}: in memory per instance by default, in Redis
 * when several instances run. Over the limit → 429 problem detail with
 * Retry-After. The client IP is the request's remote address; behind the
 * reverse proxy Tomcat's RemoteIpValve resolves X-Forwarded-For from trusted
 * internal proxies only (server.forward-headers-strategy=native).
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Pattern BOOKING_POST = Pattern.compile("^/api/public/[^/]+/bookings/?$");
    private static final Pattern LOGIN_POST = Pattern.compile("^/api/auth/[^/]+/login$");
    private static final Pattern AUTH_MAIL_POST =
            Pattern.compile("^/api/public/[^/]+/auth/(register|forgot-password|resend-verification)$");
    private static final Pattern TOKEN_POST = Pattern.compile(
            "^/api/public/(manage|barber-decision|payments|booking-status)/[^/]+/[a-z-]+$|^/api/public/auth/(verify-email|reset-password)$");
    private static final String REFRESH_POST = "/api/auth/refresh";

    private final AppProperties.RateLimit config;
    private final ProblemFactory problems;
    private final RateLimitStore store;

    public RateLimitFilter(AppProperties props, ProblemFactory problems, RateLimitStore store) {
        this.config = props.rateLimit();
        this.problems = problems;
        this.store = store;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !config.enabled() || !"POST".equals(request.getMethod()) || limitFor(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Route route = limitFor(request);
        String key = route.name() + "|" + request.getRemoteAddr();
        RateLimitStore.Probe probe = store.tryConsume(key, route.limit());
        if (probe.consumed()) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.remaining()));
            chain.doFilter(request, response);
            return;
        }
        long retryAfter = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.nanosToWait()) + 1);
        response.setHeader("Retry-After", String.valueOf(retryAfter));
        problems.write(request, response, HttpStatus.TOO_MANY_REQUESTS, "rate_limit.exceeded", retryAfter);
    }

    private Route limitFor(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (BOOKING_POST.matcher(path).matches()) return new Route("bookings", config.bookings());
        if (LOGIN_POST.matcher(path).matches()) return new Route("login", config.login());
        if (AUTH_MAIL_POST.matcher(path).matches()) return new Route("auth-mail", config.authMail());
        if (TOKEN_POST.matcher(path).matches()) return new Route("token", config.tokenActions());
        if (REFRESH_POST.equals(path)) return new Route("refresh", config.refresh());
        return null;
    }

    private record Route(String name, AppProperties.RateLimit.Limit limit) {}
}
