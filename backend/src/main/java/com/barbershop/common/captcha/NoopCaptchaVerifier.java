package com.barbershop.common.captcha;

/** No bot check (local development, e2e tests). Refused with the prod profile unless explicitly allowed. */
public class NoopCaptchaVerifier implements CaptchaVerifier {

    @Override
    public boolean verify(String token, String remoteIp) {
        return true;
    }

    @Override
    public boolean enabled() {
        return false;
    }
}
