package com.barbershop.common.i18n;

import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/** Small facade over the MessageSource (messages.properties = German, messages_en.properties = English). */
@Component
public class Messages {

    private final MessageSource source;

    public Messages(MessageSource source) {
        this.source = source;
    }

    /** Message in the current request's language. */
    public String get(String code, Object... args) {
        return get(LocaleContextHolder.getLocale(), code, args);
    }

    public String get(Locale locale, String code, Object... args) {
        return source.getMessage(code, args, code, Lang.of(locale).locale());
    }

    /** Language of the current request. */
    public static Lang currentLang() {
        return Lang.of(LocaleContextHolder.getLocale());
    }
}
