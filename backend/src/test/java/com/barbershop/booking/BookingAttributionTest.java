package com.barbershop.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.barbershop.booking.BookingDtos.AttributionRequest;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** Attribution from the browser is untrusted: cleaned, cut, and only kept inside the 30-day window. */
class BookingAttributionTest {

    private static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");

    private static OffsetDateTime ago(Duration d) {
        return NOW.minus(d).atOffset(ZoneOffset.UTC);
    }

    @Test
    void valuesAreTrimmedStrippedOfControlCharactersAndCut() {
        BookingAttribution a = BookingAttribution.of(new AttributionRequest(" google ", "cpc\n", "herbst\u0000-fade",
                "x".repeat(200), "", "Cj0K", null, "/de/lp/herbst-fade", ago(Duration.ofDays(2))), NOW);
        assertThat(a.getSource()).isEqualTo("google");
        assertThat(a.getMedium()).isEqualTo("cpc");
        assertThat(a.getCampaign()).isEqualTo("herbst-fade");
        assertThat(a.getTerm()).hasSize(150);
        assertThat(a.getContent()).isNull();
        assertThat(a.getGclid()).isEqualTo("Cj0K");
        assertThat(a.getLandingPath()).isEqualTo("/de/lp/herbst-fade");
    }

    @Test
    void outsideTheWindowOrEmpty_nothingIsStored() {
        assertThat(BookingAttribution.of(new AttributionRequest("google", null, null, null, null, null, null, null,
                ago(Duration.ofDays(40))), NOW)).isNull();
        assertThat(BookingAttribution.of(new AttributionRequest("google", null, null, null, null, null, null, null,
                NOW.plus(Duration.ofDays(3)).atOffset(ZoneOffset.UTC)), NOW)).isNull();
        assertThat(BookingAttribution.of(new AttributionRequest(" ", "", null, null, null, null, null, "not-a-path",
                ago(Duration.ofDays(1))), NOW)).isNull();
        assertThat(BookingAttribution.of(null, NOW)).isNull();
    }
}
