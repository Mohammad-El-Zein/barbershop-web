package com.barbershop.mail;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Minimal RFC 5545 calendar file (one VEVENT) for the confirmation email.
 * Times in UTC, CRLF line endings, text escaped and long lines folded.
 */
public final class IcsCalendar {

    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);

    private IcsCalendar() {}

    public static byte[] event(UUID bookingId, Instant start, Instant end, Instant stamp, String summary,
                               String description, String location) {
        StringBuilder sb = new StringBuilder();
        line(sb, "BEGIN:VCALENDAR");
        line(sb, "VERSION:2.0");
        line(sb, "PRODID:-//Barbershop//Booking//EN");
        line(sb, "CALSCALE:GREGORIAN");
        line(sb, "METHOD:PUBLISH");
        line(sb, "BEGIN:VEVENT");
        line(sb, "UID:" + bookingId + "@barbershop");
        line(sb, "DTSTAMP:" + UTC.format(stamp));
        line(sb, "DTSTART:" + UTC.format(start));
        line(sb, "DTEND:" + UTC.format(end));
        line(sb, "SUMMARY:" + escape(summary));
        if (description != null) line(sb, "DESCRIPTION:" + escape(description));
        if (location != null) line(sb, "LOCATION:" + escape(location));
        line(sb, "STATUS:CONFIRMED");
        line(sb, "TRANSP:OPAQUE");
        line(sb, "BEGIN:VALARM");
        line(sb, "TRIGGER:-PT1H");
        line(sb, "ACTION:DISPLAY");
        line(sb, "DESCRIPTION:" + escape(summary));
        line(sb, "END:VALARM");
        line(sb, "END:VEVENT");
        line(sb, "END:VCALENDAR");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String escape(String s) {
        return s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r", "").replace("\n", "\\n");
    }

    /** Content lines are folded at 75 octets (continuation lines start with a space). */
    private static void line(StringBuilder sb, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= 75) {
            sb.append(content).append("\r\n");
            return;
        }
        int limit = 75;
        StringBuilder current = new StringBuilder();
        int currentBytes = 0;
        for (int i = 0; i < content.length(); ) {
            int cp = content.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            int len = ch.getBytes(StandardCharsets.UTF_8).length;
            if (currentBytes + len > limit) {
                sb.append(current).append("\r\n ");
                current.setLength(0);
                currentBytes = 0;
                limit = 74; // the leading space counts
            }
            current.append(ch);
            currentBytes += len;
            i += Character.charCount(cp);
        }
        sb.append(current).append("\r\n");
    }
}
