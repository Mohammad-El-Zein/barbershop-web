package com.barbershop.common.error;

import com.barbershop.common.i18n.Messages;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;

/**
 * Builds localized RFC 9457 problem details:
 * <pre>{ "type", "title", "status", "detail", "instance", "code", "timestamp", "errors"? }</pre>
 * {@code title} is the localized HTTP reason, {@code detail} the localized message
 * for {@code code}. Also writes problems directly to the response for code that
 * runs before Spring MVC (security entry points, rate limiter).
 */
@Component
public class ProblemFactory {

    private final Messages messages;
    private final LocaleResolver localeResolver;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ProblemFactory(Messages messages, LocaleResolver localeResolver, ObjectMapper objectMapper, Clock clock) {
        this.messages = messages;
        this.localeResolver = localeResolver;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public ProblemDetail create(HttpStatusCode status, String code, Locale locale, Object... args) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create("about:blank"));
        problem.setTitle(messages.get(locale, "http." + status.value()));
        problem.setDetail(messages.get(locale, code, args));
        problem.setProperty("code", code);
        problem.setProperty("timestamp", Instant.now(clock));
        return problem;
    }

    public ProblemDetail withErrors(ProblemDetail problem, Map<String, String> errors) {
        problem.setProperty("errors", errors);
        return problem;
    }

    /** For filters / security handlers outside Spring MVC (locale resolved from the request). */
    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatusCode status, String code,
                      Object... args) throws IOException {
        Locale locale = localeResolver.resolveLocale(request);
        ProblemDetail problem = create(status, code, locale, args);
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Language", locale.getLanguage());
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
