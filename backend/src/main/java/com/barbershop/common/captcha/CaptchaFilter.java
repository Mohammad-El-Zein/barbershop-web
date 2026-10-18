package com.barbershop.common.captcha;

import com.barbershop.common.error.ProblemFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Requires a valid {@code X-Captcha-Token} for: booking requests, registration and
 * forgot-password (in addition to the per-IP rate limits and the max open requests per
 * email). Without one → 400 problem "captcha.failed". Inactive with the no-op verifier.
 */
@Component
public class CaptchaFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Captcha-Token";
    private static final Pattern PROTECTED = Pattern.compile(
            "^/api/public/[^/]+/(bookings|auth/register|auth/forgot-password)/?$");

    private final CaptchaVerifier verifier;
    private final ProblemFactory problems;

    public CaptchaFilter(CaptchaVerifier verifier, ProblemFactory problems) {
        this.verifier = verifier;
        this.problems = problems;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !verifier.enabled() || !"POST".equals(request.getMethod())
                || !PROTECTED.matcher(request.getRequestURI()).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (verifier.verify(request.getHeader(HEADER), request.getRemoteAddr())) {
            chain.doFilter(request, response);
            return;
        }
        problems.write(request, response, HttpStatus.BAD_REQUEST, "captcha.failed");
    }
}
