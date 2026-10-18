package com.barbershop;

import static org.assertj.core.api.Assertions.assertThat;

import com.barbershop.support.AbstractIntegrationTest;
import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Campaign attribution on online bookings and the admin campaign stats ("which ad produced which booking"). */
class AttributionIT extends AbstractIntegrationTest {

    private Map<String, Object> withAttribution(Map<String, Object> booking, Map<String, Object> attribution) {
        Map<String, Object> b = new HashMap<>(booking);
        b.put("attribution", attribution);
        return b;
    }

    private static Map<String, Object> attr(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        m.putIfAbsent("capturedAt", OffsetDateTime.now().minusDays(1).toString());
        return m;
    }

    @Test
    @SuppressWarnings("unchecked")
    void attributionIsStoredOnTheBookingAndGroupedInTheCampaignStats() throws Exception {
        String admin = adminToken();
        var day = next(DayOfWeek.TUESDAY);

        // 1) Google Ads click via the ad landing page, later confirmed (accepted + demo payment)
        String googleEmail = uniqueEmail();
        String google = (String) book(withAttribution(booking(HAIRCUT, LUCA, at(day, "10:00"), googleEmail, "de"),
                attr("source", "google", "medium", "cpc", "campaign", "herbst-fade", "gclid", "Cj0KCQ",
                        "landingPath", "/de/lp/herbst-fade?utm_source=google"))).getBody().get("id");
        post("/api/public/barber-decision/" + tokenFromMail(ADMIN_EMAIL, nameFor(googleEmail), DECISION_LINK)
                + "/accept", null);
        post("/api/public/payments/" + tokenFromMail(googleEmail, "hat zugesagt", PAY_LINK) + "/simulate", null);

        // 2) a second Google booking of the same campaign (still pending)
        book(withAttribution(booking(HAIRCUT, LUCA, at(day, "11:00"), uniqueEmail(), "de"),
                attr("source", "google", "medium", "cpc", "campaign", "herbst-fade")));
        // 3) Meta click id only (no UTM) → facebook / paid_social
        book(withAttribution(booking(HAIRCUT, LUCA, at(day, "12:00"), uniqueEmail(), "de"), attr("fbclid", "IwAR0")));
        // 4) landing page without UTM → campaign "lp:bart-special"
        book(withAttribution(booking(HAIRCUT, LUCA, at(day, "16:00"), uniqueEmail(), "de"),
                attr("landingPath", "/en/lp/bart-special")));
        // 5) no consent → no attribution → (direct); 6) attribution older than 30 days is ignored
        book(booking(HAIRCUT, LUCA, at(day, "14:00"), uniqueEmail(), "de"));
        book(withAttribution(booking(HAIRCUT, LUCA, at(day, "15:00"), uniqueEmail(), "de"),
                attr("source", "newsletter", "capturedAt", OffsetDateTime.now().minusDays(45).toString())));

        // the admin sees the attribution on the booking (click ids only as yes/no)
        Map<String, Object> view = get("/api/admin/bookings/" + google, "de", admin).getBody();
        assertThat((Map<String, Object>) view.get("attribution")).containsEntry("source", "google")
                .containsEntry("campaign", "herbst-fade").containsEntry("gclid", true).containsEntry("fbclid", false)
                .containsEntry("landingPath", "/de/lp/herbst-fade?utm_source=google");

        Map<String, Object> stats = get("/api/admin/stats/campaigns", "de", admin).getBody();
        assertThat(stats).containsEntry("totalBookings", 6).containsEntry("attributedBookings", 4)
                .containsEntry("currency", "EUR");
        List<Map<String, Object>> rows = (List<Map<String, Object>>) stats.get("campaigns");
        assertThat(rows.getFirst()).containsEntry("source", "google").containsEntry("medium", "cpc")
                .containsEntry("campaign", "herbst-fade").containsEntry("bookings", 2).containsEntry("confirmed", 1)
                .containsEntry("confirmedRevenueCents", 3500);
        assertThat(rows).anySatisfy(r -> assertThat(r).containsEntry("source", "facebook")
                .containsEntry("medium", "paid_social").containsEntry("campaign", "(not set)").containsEntry("bookings", 1));
        assertThat(rows).anySatisfy(r -> assertThat(r).containsEntry("source", "(direct)")
                .containsEntry("campaign", "lp:bart-special").containsEntry("bookings", 1));
        assertThat(rows).anySatisfy(r -> assertThat(r).containsEntry("source", "(direct)")
                .containsEntry("campaign", "(not set)").containsEntry("bookings", 2));
    }

    @Test
    void overLongValuesAreRejected_andTheEndpointIsAdminOnly() {
        Map<String, Object> body = withAttribution(booking(HAIRCUT, LUCA, at(next(DayOfWeek.WEDNESDAY), "10:00"),
                uniqueEmail(), "de"), attr("source", "x".repeat(400)));
        assertThat(book(body).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get("/api/admin/stats/campaigns", "de").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
