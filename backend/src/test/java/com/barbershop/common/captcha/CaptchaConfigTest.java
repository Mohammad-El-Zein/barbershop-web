package com.barbershop.common.captcha;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.barbershop.common.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** The prod profile refuses to run without bot protection unless that is explicitly allowed. */
class CaptchaConfigTest {

    private static MockEnvironment env(String... profiles) {
        MockEnvironment e = new MockEnvironment();
        e.setActiveProfiles(profiles);
        return e;
    }

    @Test
    void noneIsRefusedInProd_unlessAllowed() {
        assertThatThrownBy(() -> CaptchaConfig.checkNoneGuard(new AppProperties.Captcha("none", "", "", false), env("prod")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("APP_CAPTCHA_ALLOW_NONE_IN_PROD");
        assertThatCode(() -> CaptchaConfig.checkNoneGuard(new AppProperties.Captcha("none", "", "", true), env("prod")))
                .doesNotThrowAnyException();
        assertThatCode(() -> CaptchaConfig.checkNoneGuard(new AppProperties.Captcha("none", "", "", false), env("demo")))
                .doesNotThrowAnyException();
    }
}
