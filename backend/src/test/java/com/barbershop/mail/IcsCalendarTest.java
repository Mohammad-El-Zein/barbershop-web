package com.barbershop.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IcsCalendarTest {

    @Test
    void producesAValidUtcEventWithEscapedAndFoldedText() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
        String ics = new String(IcsCalendar.event(id, Instant.parse("2026-11-03T09:00:00Z"),
                Instant.parse("2026-11-03T10:15:00Z"), Instant.parse("2026-10-17T12:00:00Z"),
                "Haarschnitt + Bart-Styling – The Barber News",
                "Barber: Luca\nIm Laden zu zahlen: 50 €; bitte pünktlich, danke " + "x".repeat(80), "The Barber News"),
                StandardCharsets.UTF_8);

        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n")
                .contains("UID:" + id + "@barbershop\r\n")
                .contains("DTSTART:20261103T090000Z\r\n")
                .contains("DTEND:20261103T101500Z\r\n")
                .contains("METHOD:PUBLISH");
        // ; , and newlines are escaped
        assertThat(ics.replace("\r\n ", "")).contains("Im Laden zu zahlen: 50 €\\; bitte pünktlich\\, danke")
                .contains("Barber: Luca\\nIm Laden");
        // no physical line longer than 75 octets
        assertThat(Arrays.stream(ics.split("\r\n")))
                .allSatisfy(line -> assertThat(line.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75));
    }
}
