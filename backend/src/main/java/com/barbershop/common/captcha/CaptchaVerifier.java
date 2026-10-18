package com.barbershop.common.captcha;

/**
 * Bot protection for the public forms that create data or send emails (booking request,
 * registration, forgot password). The browser sends the widget's token in the
 * {@code X-Captcha-Token} header; {@link CaptchaFilter} asks the verifier.
 * Implementations: {@link TurnstileCaptchaVerifier} (Cloudflare Turnstile) and
 * {@link NoopCaptchaVerifier} (local development, e2e tests). Selected by APP_CAPTCHA_PROVIDER.
 */
public interface CaptchaVerifier {

    /** True if the token proves a human solved the challenge (for this client IP). */
    boolean verify(String token, String remoteIp);

    /** False for the no-op verifier: then the header is not required at all. */
    boolean enabled();
}
