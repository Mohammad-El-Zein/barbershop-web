package com.barbershop.common.i18n;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * Request language: {@code ?lang=de|en} wins, then the {@code Accept-Language}
 * header, default German. Bean Validation messages are resolved through the
 * same MessageSource, so validation errors are localized too.
 */
@Configuration
public class LocaleConfig {

    @Bean
    public LocaleResolver localeResolver() {
        return new LangParamLocaleResolver();
    }

    @Bean
    public LocalValidatorFactoryBean validator(MessageSource messageSource) {
        LocalValidatorFactoryBean bean = new LocalValidatorFactoryBean();
        bean.setValidationMessageSource(messageSource);
        return bean;
    }

    /** ?lang= parameter first, then Accept-Language (de/en only), default de. */
    static class LangParamLocaleResolver extends AcceptHeaderLocaleResolver {

        LangParamLocaleResolver() {
            setSupportedLocales(Lang.SUPPORTED_LOCALES);
            setDefaultLocale(Lang.DEFAULT.locale());
        }

        @Override
        public Locale resolveLocale(HttpServletRequest request) {
            Lang fromParam = Lang.parse(request.getParameter("lang"));
            if (fromParam != null) return fromParam.locale();
            return Lang.of(super.resolveLocale(request)).locale();
        }
    }
}
