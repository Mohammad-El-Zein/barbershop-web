package com.barbershop.common.i18n;

import java.util.List;
import java.util.Locale;

/** Supported customer languages and helpers to pick the right translation. */
public enum Lang {
    DE("de", Locale.GERMAN),
    EN("en", Locale.ENGLISH);

    public static final Lang DEFAULT = DE;
    public static final List<Locale> SUPPORTED_LOCALES = List.of(Locale.GERMAN, Locale.ENGLISH);

    private final String code;
    private final Locale locale;

    Lang(String code, Locale locale) {
        this.code = code;
        this.locale = locale;
    }

    public String code() {
        return code;
    }

    public Locale locale() {
        return locale;
    }

    /** Maps any locale ("en-US", "de-AT", "fr") to a supported language (fallback: German). */
    public static Lang of(Locale locale) {
        if (locale != null && "en".equals(locale.getLanguage())) return EN;
        return DE;
    }

    /** Parses "de"/"en" (case-insensitive, also "en-US"); null if not supported. */
    public static Lang parse(String value) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (v.startsWith("en")) return EN;
        if (v.startsWith("de")) return DE;
        return null;
    }

    /** Picks the text for this language, falling back to German when the English text is missing. */
    public String pick(String de, String en) {
        if (this == EN && en != null && !en.isBlank()) return en;
        return de;
    }
}
