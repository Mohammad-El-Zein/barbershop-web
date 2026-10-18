package com.barbershop.common.web;

import java.util.regex.Pattern;

/**
 * Makes values safe for the logs: no personal data, no secrets.
 * Email-link tokens travel in URL paths (/manage/{token}, /payments/{token} …) and
 * database / SMTP error messages can quote an email address.
 */
public final class LogSafe {

    private static final Pattern EMAIL = Pattern.compile("([A-Za-z0-9._%+-])[A-Za-z0-9._%+-]*@([A-Za-z0-9.-]+)");
    // opaque tokens: long base64url / hex segments (UUIDs of entities stay readable)
    private static final Pattern TOKEN_SEGMENT = Pattern.compile("/[A-Za-z0-9_-]{24,}(?=/|$)");
    private static final Pattern UUID_SEGMENT =
            Pattern.compile("/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}(?=/|$)");

    private LogSafe() {}

    /** Request path with tokens replaced by "/***". */
    public static String path(String uri) {
        if (uri == null) return null;
        StringBuilder out = new StringBuilder();
        var m = TOKEN_SEGMENT.matcher(uri);
        int last = 0;
        while (m.find()) {
            String seg = m.group();
            out.append(uri, last, m.start()).append(UUID_SEGMENT.matcher(seg).matches() ? seg : "/***");
            last = m.end();
        }
        return out.append(uri.substring(last)).toString();
    }

    /** Free text (exception messages) with email addresses masked: a***@example.com. */
    public static String text(String value) {
        return value == null ? null : EMAIL.matcher(value).replaceAll("$1***@$2");
    }
}
