package com.barbershop.common.captcha;

import com.barbershop.common.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Selects the bot check from APP_CAPTCHA_PROVIDER (none | turnstile). Like the demo-payment
 * guard: with the {@code prod} profile "none" is refused (the application does not start)
 * unless APP_CAPTCHA_ALLOW_NONE_IN_PROD=true.
 */
@Configuration
public class CaptchaConfig {

    private static final Logger log = LoggerFactory.getLogger(CaptchaConfig.class);

    @Bean
    CaptchaVerifier captchaVerifier(AppProperties props, Environment env) {
        AppProperties.Captcha c = props.captcha();
        String provider = c.provider() == null ? "" : c.provider().trim().toLowerCase();
        return switch (provider) {
            case "none" -> {
                checkNoneGuard(c, env);
                log.warn("Captcha: none — public forms are protected by rate limits only");
                yield new NoopCaptchaVerifier();
            }
            case "turnstile" -> {
                if (c.secret() == null || c.secret().isBlank()) {
                    throw new IllegalStateException("APP_CAPTCHA_PROVIDER=turnstile needs APP_CAPTCHA_SECRET");
                }
                String url = c.verifyUrl() == null || c.verifyUrl().isBlank()
                        ? TurnstileCaptchaVerifier.DEFAULT_VERIFY_URL : c.verifyUrl();
                log.info("Captcha: Cloudflare Turnstile");
                yield new TurnstileCaptchaVerifier(c.secret(), url);
            }
            default -> throw new IllegalStateException("Unknown APP_CAPTCHA_PROVIDER '" + c.provider()
                    + "' (none|turnstile)");
        };
    }

    static void checkNoneGuard(AppProperties.Captcha c, Environment env) {
        if (!env.acceptsProfiles(Profiles.of("prod")) || c.allowNoneInProd()) return;
        throw new IllegalStateException("""
                Refusing to start: the `prod` profile is active but APP_CAPTCHA_PROVIDER=none (no bot protection \
                on booking, registration and password reset). Configure Turnstile (APP_CAPTCHA_PROVIDER=turnstile, \
                APP_CAPTCHA_SECRET, NEXT_PUBLIC_TURNSTILE_SITE_KEY in the frontend), or set \
                APP_CAPTCHA_ALLOW_NONE_IN_PROD=true on purpose.""");
    }
}
