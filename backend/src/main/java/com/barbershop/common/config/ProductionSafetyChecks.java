package com.barbershop.common.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Startup checks of security-relevant configuration. With the {@code prod} profile every
 * problem stops the application (a bean that can't be created = no start); otherwise they
 * are logged as warnings:
 * <ul>
 *   <li>APP_JWT_SECRET (and APP_TOKEN_SECRET if set): ≥ 32 characters, enough different
 *       characters, not a placeholder ("change-me", "secret" …) — generate with
 *       {@code openssl rand -base64 48},</li>
 *   <li>refresh cookie Secure (APP_COOKIE_SECURE=true),</li>
 *   <li>CORS origins and the frontend URL: https, explicit (no "*").</li>
 * </ul>
 * (The demo-payment and captcha guards live in PaymentConfig / CaptchaConfig.)
 */
@Component
public class ProductionSafetyChecks {

    private static final Logger log = LoggerFactory.getLogger(ProductionSafetyChecks.class);
    private static final Pattern PLACEHOLDER = Pattern.compile(
            "change[-_ ]?me|changeit|secret|password|example|placeholder|dummy|test|default|todo|xxx", Pattern.CASE_INSENSITIVE);

    public ProductionSafetyChecks(AppProperties props, Environment env) {
        boolean prod = env.acceptsProfiles(Profiles.of("prod"));
        List<String> problems = check(props, prod);
        if (problems.isEmpty()) return;
        if (prod) {
            throw new IllegalStateException("Refusing to start with the prod profile:\n - " + String.join("\n - ", problems));
        }
        problems.forEach(p -> log.warn("Not production-ready: {}", p));
    }

    static List<String> check(AppProperties props, boolean prod) {
        List<String> problems = new ArrayList<>();
        secret("APP_JWT_SECRET", props.jwt().secret(), problems);
        String tokens = props.tokens().secret();
        if (tokens != null && !tokens.isBlank()) secret("APP_TOKEN_SECRET", tokens, problems);
        if (prod) {
            if (!props.auth().cookieSecure()) problems.add("APP_COOKIE_SECURE must be true (refresh cookie over HTTPS only)");
            for (String origin : props.cors().allowedOrigins()) {
                String o = origin.trim();
                if (o.isEmpty()) continue;
                if (o.contains("*") || !o.toLowerCase(Locale.ROOT).startsWith("https://")) {
                    problems.add("APP_CORS_ALLOWED_ORIGINS must list explicit https origins, got '" + o + "'");
                }
            }
            if (!props.frontendBaseUrl().toLowerCase(Locale.ROOT).startsWith("https://")) {
                problems.add("APP_FRONTEND_BASE_URL must be https (links in emails), got '" + props.frontendBaseUrl() + "'");
            }
        }
        return problems;
    }

    /** Length, character variety and no obvious placeholder; never logs the value itself. */
    static void secret(String name, String value, List<String> problems) {
        if (value == null || value.length() < 32) {
            problems.add(name + " must be at least 32 characters (generate: openssl rand -base64 48)");
            return;
        }
        if (value.chars().distinct().count() < 12) problems.add(name + " has too little entropy (too few different characters)");
        if (PLACEHOLDER.matcher(value).find()) problems.add(name + " looks like a placeholder, generate a random one");
    }
}
