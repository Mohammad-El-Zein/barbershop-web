package com.barbershop.common.captcha;

import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Cloudflare Turnstile: the token from the widget is checked server-side at
 * {@code siteverify} with the secret key. Fails closed: a timeout or an error of the
 * verification service rejects the request (logged), so the forms can't be flooded
 * while Cloudflare is unreachable. Cloudflare's test keys ("always passes" /
 * "always fails") work for staging, see docs/DEPLOYMENT.md.
 */
public class TurnstileCaptchaVerifier implements CaptchaVerifier {

    public static final String DEFAULT_VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";
    private static final Logger log = LoggerFactory.getLogger(TurnstileCaptchaVerifier.class);

    private final RestClient http;
    private final String secret;
    private final String verifyUrl;

    public TurnstileCaptchaVerifier(String secret, String verifyUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.http = RestClient.builder().requestFactory(factory).build();
        this.secret = secret;
        this.verifyUrl = verifyUrl;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean verify(String token, String remoteIp) {
        if (token == null || token.isBlank() || token.length() > 2048) return false;
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secret);
        form.add("response", token);
        if (remoteIp != null) form.add("remoteip", remoteIp);
        try {
            Map<String, Object> body = http.post().uri(verifyUrl).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(Map.class);
            boolean ok = body != null && Boolean.TRUE.equals(body.get("success"));
            if (!ok) log.info("Captcha rejected: {}", body == null ? "empty response" : body.get("error-codes"));
            return ok;
        } catch (Exception ex) {
            log.warn("Captcha verification unavailable ({}): request rejected", ex.getClass().getSimpleName());
            return false;
        }
    }

    @Override
    public boolean enabled() {
        return true;
    }
}
