package com.barbershop;

import static org.assertj.core.api.Assertions.assertThat;

import com.barbershop.booking.BookingJobs;
import com.barbershop.payment.PaymentService;
import com.barbershop.support.AbstractIntegrationTest;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

/**
 * Who gets which booking email, per event: the customer, the barber (own login; without one,
 * the admins instead) and every admin of the shop — each address once per event. Read from the
 * outbox (kind + recipient of every mail of the booking), the texts from the delivered mails.
 */
class BookingMailRecipientsIT extends AbstractIntegrationTest {

    private static final String SECOND_ADMIN = "second-admin@example.test";
    private static final UUID SECOND_ADMIN_ID = UUID.randomUUID();

    @Autowired
    PaymentService payments;

    @Autowired
    BookingJobs jobs;

    private String barberId;
    private String barberEmail;
    private String barberWithoutLogin;
    private String freeService;

    /**
     * A second admin of the shop, a barber with an own login, one without, and a 0 € service. The
     * base class deletes every non-admin account before each test: the barber login is made per test.
     */
    @BeforeEach
    void setUp() {
        jdbc.update("""
                insert into app_user (id, tenant_id, email, password_hash, role, name, locale, created_at,
                                      email_verified_at, failed_login_count, must_change_password)
                values (?, ?, ?, 'x', 'ADMIN', 'Second Admin', 'en', now(), now(), 0, false)
                on conflict do nothing""", SECOND_ADMIN_ID, TENANT_ID, SECOND_ADMIN);
        String admin = adminToken();
        freeService = (String) call(HttpMethod.POST, "/api/admin/services", Map.of("nameDe", "Gratis-Check",
                "nameEn", "Free check", "durationMin", 15, "priceCents", 0, "priceFrom", false, "active", true,
                "sortOrder", 99), "en", admin).getBody().get("id");
        barberId = newBarber(admin, "Mail Barber", List.of(HAIRCUT.toString(), freeService));
        barberWithoutLogin = newBarber(admin, "No Login Barber", List.of(HAIRCUT.toString()));
        barberEmail = "mail-barber-" + uniqueEmail();
        var account = call(HttpMethod.POST, "/api/admin/staff/" + barberId + "/account", Map.of("email", barberEmail,
                "lang", "de", "password", "barber-password-1"), "en", admin);
        assertThat(account.getStatusCode()).as(String.valueOf(account.getBody())).isEqualTo(HttpStatus.CREATED);
    }

    /** Keep the shared demo data clean: only Luca and Mo stay, and the demo services. */
    @AfterEach
    void removeOwnBarbersAndService() {
        jdbc.update("delete from booking where staff_id not in (?, ?)", LUCA, MO);
        jdbc.update("delete from staff where tenant_id = ? and id not in (?, ?)", TENANT_ID, LUCA, MO);
        jdbc.update("delete from service where id = ?::uuid", freeService);
    }

    @AfterAll
    static void cleanUp(@Autowired org.springframework.jdbc.core.JdbcTemplate jdbc) {
        jdbc.update("delete from app_user where id = ?", SECOND_ADMIN_ID);
    }

    @Test
    void requestAcceptAndPayment_reachCustomerBarberAndEveryAdminOnce() throws Exception {
        String customer = uniqueEmail();
        String id = request(barberId, HAIRCUT.toString(), customer, "10:00");
        assertThat(recipients(id)).isEqualTo(Map.of(
                "requested", Set.of(customer),
                "barber-requested", Set.of(barberEmail),
                "admin-requested", Set.of(ADMIN_EMAIL, SECOND_ADMIN)));
        // "Kunde <Name> hat bei <Barber> eine Anfrage für <Leistung> am <Datum> um <Uhrzeit> gestellt."
        String requested = text(mailTo(ADMIN_EMAIL, "Neue Anfrage bei Mail Barber: " + nameFor(customer)));
        assertThat(requested).contains("Kunde " + nameFor(customer) + " hat bei Mail Barber eine Anfrage für Haarschnitt am ",
                " um 10:00 Uhr gestellt.", "/barber/requests/");
        assertThat(text(mailTo(SECOND_ADMIN, "New request for Mail Barber: " + nameFor(customer))))
                .contains("Customer " + nameFor(customer) + " has requested Haircut with Mail Barber on ", " at 10:00 AM.");

        call(HttpMethod.POST, "/api/admin/bookings/" + id + "/accept", null, "de", adminToken());
        assertThat(recipients(id, "accepted")).isEqualTo(Map.of(
                "accepted", Set.of(customer),
                "admin-accepted", Set.of(ADMIN_EMAIL, SECOND_ADMIN)));
        assertThat(text(mailTo(ADMIN_EMAIL, "Angenommen: " + nameFor(customer))))
                .contains("Mail Barber hat die Anfrage von " + nameFor(customer) + " für ", "angenommen. Anzahlung fällig bis ");

        payments.handlePaymentSucceeded(jdbc.queryForObject(
                "select provider_ref from payment where booking_id = ?::uuid", String.class, id));
        assertThat(recipients(id, "confirmed")).isEqualTo(Map.of(
                "confirmed", Set.of(customer),
                "barber-confirmed", Set.of(barberEmail),
                "admin-confirmed", Set.of(ADMIN_EMAIL, SECOND_ADMIN)));
        assertThat(text(mailTo(ADMIN_EMAIL, "Anzahlung bezahlt: " + nameFor(customer))))
                .contains(nameFor(customer) + " hat bei Mail Barber für ", "die Anzahlung bezahlt. Der Termin ist bestätigt.")
                // name, barber, service, time — no contact data, no prices
                .doesNotContain(customer, "+49", "€");
        assertThat(subject(mailTo(barberEmail, "Anzahlung erhalten: " + nameFor(customer)))).isNotBlank();

        // cancelled by the shop → customer, barber, admins
        call(HttpMethod.POST, "/api/admin/bookings/" + id + "/cancel", Map.of(), "de", adminToken());
        assertThat(recipients(id, "cancelled")).isEqualTo(Map.of(
                "cancelled", Set.of(customer),
                "barber-cancelled", Set.of(barberEmail),
                "admin-cancelled", Set.of(ADMIN_EMAIL, SECOND_ADMIN)));
    }

    @Test
    void withoutADepositTheBarberAndTheAdminsAreToldTheAppointmentIsConfirmed() throws Exception {
        String customer = uniqueEmail();
        String id = request(barberId, freeService, customer, "12:00");
        call(HttpMethod.POST, "/api/admin/bookings/" + id + "/accept", null, "de", adminToken());
        assertThat(jdbc.queryForObject("select status from booking where id = ?::uuid", String.class, id))
                .isEqualTo("CONFIRMED");
        assertThat(recipients(id, "confirmed")).isEqualTo(Map.of(
                "confirmed", Set.of(customer),
                "barber-confirmed", Set.of(barberEmail),
                "admin-confirmed", Set.of(ADMIN_EMAIL, SECOND_ADMIN)));
        assertThat(text(mailTo(barberEmail, "Termin bestätigt: " + nameFor(customer)))).contains("keine Anzahlung fällig");
        assertThat(text(mailTo(ADMIN_EMAIL, "Bestätigt: " + nameFor(customer))))
                .contains("Es ist keine Anzahlung fällig – der Termin ist bestätigt.");
        assertThat(countMails(ADMIN_EMAIL, "Angenommen: " + nameFor(customer))).isZero();
    }

    @Test
    void declineAndExpiry_reachTheCustomerAndTheAdmins() throws Exception {
        String declined = request(barberId, HAIRCUT.toString(), uniqueEmail(), "14:00");
        call(HttpMethod.POST, "/api/admin/bookings/" + declined + "/decline", Map.of(), "de", adminToken());
        assertThat(recipients(declined, "declined").keySet()).containsExactlyInAnyOrder("declined", "admin-declined");
        assertThat(recipients(declined, "declined").get("admin-declined")).isEqualTo(Set.of(ADMIN_EMAIL, SECOND_ADMIN));

        String expired = request(barberId, HAIRCUT.toString(), uniqueEmail(), "16:00");
        jdbc.update("update booking set barber_deadline_at = now() - interval '1 minute' where id = ?::uuid", expired);
        jobs.runAll();
        assertThat(recipients(expired, "expired").keySet()).containsExactlyInAnyOrder("expired", "admin-expired");
        assertThat(recipients(expired, "expired").get("admin-expired")).isEqualTo(Set.of(ADMIN_EMAIL, SECOND_ADMIN));
    }

    @Test
    void aBarberWithoutLogin_theAdminsGetTheBarberMailOnly_andAManualBookingOnlyTheCustomer() throws Exception {
        String customer = uniqueEmail();
        String id = request(barberWithoutLogin, HAIRCUT.toString(), customer, "11:00");
        assertThat(recipients(id)).isEqualTo(Map.of(
                "requested", Set.of(customer),
                "barber-requested", Set.of(ADMIN_EMAIL, SECOND_ADMIN)));

        String walkIn = uniqueEmail();
        String manual = (String) call(HttpMethod.POST, "/api/admin/bookings", Map.of(
                "serviceIds", List.of(HAIRCUT), "staffId", barberId,
                "start", at(next(DayOfWeek.SATURDAY), "15:00").toString(),
                "customerName", nameFor(walkIn), "customerEmail", walkIn, "lang", "de", "source", "PHONE"), "de",
                adminToken())
                .getBody().get("id");
        assertThat(recipients(manual)).isEqualTo(Map.of("confirmed", Set.of(walkIn)));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String request(String staffId, String service, String email, String time) {
        Map<String, Object> body = booking(List.of(UUID.fromString(service)), UUID.fromString(staffId),
                at(next(DayOfWeek.THURSDAY), time), email, "de");
        var r = book(body);
        assertThat(r.getStatusCode()).as(String.valueOf(r.getBody())).isEqualTo(HttpStatus.CREATED);
        return (String) r.getBody().get("id");
    }

    /** kind → recipients of every mail of the booking (optionally only kinds of these events); fails on a duplicate. */
    private Map<String, Set<String>> recipients(String bookingId, String... events) {
        Map<String, Set<String>> out = new TreeMap<>();
        jdbc.query("select kind, recipient from mail_outbox where booking_id = ?::uuid", rs -> {
            String kind = rs.getString("kind");
            if (events.length == 0 || List.of(events).stream().anyMatch(e -> kind.equals(e) || kind.endsWith("-" + e))) {
                String to = rs.getString("recipient");
                assertThat(out.computeIfAbsent(kind, k -> new TreeSet<>()).add(to)).as("twice: " + kind + " to " + to).isTrue();
            }
        }, bookingId);
        return out;
    }

    private String newBarber(String admin, String name, List<String> services) {
        String id = (String) call(HttpMethod.POST, "/api/admin/staff", Map.of("name", name), "en", admin).getBody()
                .get("id");
        call(HttpMethod.PUT, "/api/admin/staff/" + id + "/services", Map.of("serviceIds", services), "en", admin);
        List<Map<String, Object>> week = new ArrayList<>();
        for (int d = 1; d <= 6; d++) week.add(Map.of("weekday", d, "start", "09:00", "end", "19:00"));
        assertThat(rest.exchange("/api/admin/staff/" + id + "/working-hours", HttpMethod.PUT,
                new HttpEntity<>(Map.of("entries", week), headers("en", admin)), JSON_LIST).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        return id;
    }
}
