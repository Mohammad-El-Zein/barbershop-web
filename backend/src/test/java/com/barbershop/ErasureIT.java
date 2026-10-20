package com.barbershop;

import static org.assertj.core.api.Assertions.assertThat;

import com.barbershop.support.AbstractIntegrationTest;
import com.barbershop.support.DbRoles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Admin deletion (ErasureService): a booking, a barber, a service, a customer's history. Without
 * money movement a booking is deleted completely; with a paid / refunded / kept deposit it is
 * anonymised and the payment ledger stays. Active bookings always block; history (completed,
 * no-show) blocks a barber or service. Tenant-scoped, audited without personal data.
 */
@ExtendWith(OutputCaptureExtension.class)
class ErasureIT extends AbstractIntegrationTest {

    private static final AtomicInteger SLOT = new AtomicInteger();

    @Autowired
    private DataSource dataSource;

    @AfterEach
    void removeTestBarbersAndServices() {
        jdbc.update("delete from booking");
        jdbc.update("delete from staff where tenant_id = ? and id not in (?, ?)", TENANT_ID, LUCA, MO);
        jdbc.update("delete from service where tenant_id = ? and id not in (?, ?, ?)", TENANT_ID, HAIRCUT, BEARD,
                PREMIUM);
    }

    // ── a single booking ─────────────────────────────────────────────────────

    @Test
    void aClosedBookingWithoutPaymentIsDeletedCompletelyWithItsMails(CapturedOutput output) {
        String admin = adminToken();
        String email = uniqueEmail();
        UUID id = insertBooking(LUCA, "DECLINED", HAIRCUT, email, "UNPAID", null);
        UUID pending = insertMail(id, "PENDING", email);
        UUID sent = insertMail(id, "SENT", email);
        UUID sending = insertMail(id, "SENDING", email);

        ResponseEntity<Map<String, Object>> r = call(HttpMethod.DELETE, "/api/admin/bookings/" + id, null, "en", admin);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsEntry("deleted", 1).containsEntry("anonymised", 0);

        for (String table : List.of("booking where id", "booking_item where booking_id", "payment where booking_id",
                "action_token where booking_id")) {
            assertThat(count("select count(*) from " + table + " = ?::uuid", id)).as(table).isZero();
        }
        // the mails' content is personal data: gone — except the one being sent right now (unlinked)
        assertThat(count("select count(*) from mail_outbox where id in (?::uuid, ?::uuid)", pending, sent)).isZero();
        assertThat(jdbc.queryForObject("select booking_id is null from mail_outbox where id = ?", Boolean.class,
                sending)).isTrue();
        assertThat(get("/api/admin/bookings/" + id, "en", admin).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        Map<String, Object> audit = jdbc.queryForMap("select * from admin_audit where target_id = ?", id);
        assertThat(audit).containsEntry("action", "BOOKING_DELETED").containsEntry("target_type", "BOOKING")
                .containsEntry("bookings_deleted", 1).containsEntry("bookings_anonymised", 0);
        assertThat(audit.get("actor_user_id")).isNotNull();
        assertThat(audit.values().toString()).doesNotContain(email, nameFor(email));
        assertThat(output.getAll()).contains("BOOKING_DELETED").doesNotContain(email, nameFor(email));
    }

    @Test
    void activeBookingsAreNotDeleted_historyIs() {
        String admin = adminToken();
        for (String status : List.of("PENDING_BARBER", "AWAITING_PAYMENT", "CONFIRMED")) {
            UUID id = insertBooking(LUCA, status, HAIRCUT, uniqueEmail(), null, null);
            ResponseEntity<Map<String, Object>> r = call(HttpMethod.DELETE, "/api/admin/bookings/" + id, null, "de",
                    admin);
            assertThat(r.getStatusCode()).as(status).isEqualTo(HttpStatus.CONFLICT);
            assertThat(r.getBody()).containsEntry("code", "booking.erase_active");
            assertThat((String) r.getBody().get("detail")).contains("stornier");
            assertThat(count("select count(*) from booking where id = ?::uuid and deleted_at is null", id)).isOne();
        }
        for (String status : List.of("COMPLETED", "NO_SHOW", "CANCELLED", "EXPIRED")) {
            UUID id = insertBooking(LUCA, status, HAIRCUT, uniqueEmail(), null, null);
            assertThat(call(HttpMethod.DELETE, "/api/admin/bookings/" + id, null, "en", admin).getBody())
                    .as(status).containsEntry("deleted", 1);
        }
    }

    /** Real flow: request → accepted → deposit paid → cancelled by the shop (refunded) → deleted. */
    @Test
    @SuppressWarnings("unchecked")
    void aBookingWithARefundedDepositIsAnonymisedAndTheLedgerStays() throws Exception {
        String admin = adminToken();
        String email = uniqueEmail();
        Map<String, Object> req = booking(HAIRCUT, LUCA, at(next(DayOfWeek.WEDNESDAY), "11:00"), email, "en");
        req.put("note", "Please use the side entrance");
        String id = (String) book(req).getBody().get("id");
        call(HttpMethod.POST, "/api/admin/bookings/" + id + "/accept", null, "en", admin);
        String pay = tokenFromMail(email, "Luca accepted", PAY_LINK);
        assertThat(post("/api/public/payments/" + pay + "/simulate", null).getBody())
                .containsEntry("paymentStatus", "PAID");
        call(HttpMethod.POST, "/api/admin/bookings/" + id + "/cancel", Map.of("reason", "Barber ill"), "en", admin);
        assertThat(jdbc.queryForObject("select p.status from payment p where p.booking_id = ?::uuid", String.class,
                id)).isEqualTo("REFUNDED");
        long events = count("select count(*) from payment_event e join payment p on p.id = e.payment_id "
                + "where p.booking_id = ?::uuid", id);
        assertThat(events).isGreaterThanOrEqualTo(3); // created, succeeded, refunded

        // the admin detail shows the history before
        Map<String, Object> detail = get("/api/admin/bookings/" + id, "en", admin).getBody();
        assertThat(((List<Map<String, Object>>) detail.get("timeline")).stream().map(e -> e.get("event")))
                .containsSubsequence("CREATED", "ACCEPTED", "PAID", "CONFIRMED", "CANCELLED", "REFUNDED");

        assertThat(call(HttpMethod.DELETE, "/api/admin/bookings/" + id, null, "en", admin).getBody())
                .containsEntry("deleted", 0).containsEntry("anonymised", 1);

        Map<String, Object> row = jdbc.queryForMap("select * from booking where id = ?::uuid", id);
        assertThat(row.get("deleted_at")).isNotNull();
        assertThat(row).containsEntry("customer_name", "Anonymisiert").containsEntry("customer_email", null)
                .containsEntry("customer_phone", null).containsEntry("customer_note", null)
                .containsEntry("status_reason", null).containsEntry("user_id", null).containsEntry("consent_at", null);
        assertThat(row).containsEntry("status", "CANCELLED").containsEntry("deposit_cents", 1000);
        // the money records: unchanged
        assertThat(jdbc.queryForObject("select p.status from payment p where p.booking_id = ?::uuid", String.class,
                id)).isEqualTo("REFUNDED");
        assertThat(count("select count(*) from payment_event e join payment p on p.id = e.payment_id "
                + "where p.booking_id = ?::uuid", id)).isEqualTo(events);
        // links and mails: gone; the booking is invisible
        assertThat(count("select count(*) from action_token where booking_id = ?::uuid", id)).isZero();
        assertThat(count("select count(*) from mail_outbox where booking_id = ?::uuid", id)).isZero();
        assertThat(get("/api/admin/bookings/" + id, "en", admin).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/admin/bookings?q=" + email, "en", admin).getBody()).containsEntry("totalItems", 0);
        assertThat(get("/api/public/payments/" + pay, "en").getStatusCode().is4xxClientError()).isTrue();
        assertThat(call(HttpMethod.DELETE, "/api/admin/bookings/" + id, null, "en", admin).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── a barber ─────────────────────────────────────────────────────────────

    @Test
    void aBarberWithOnlyClosedBookingsIsDeleted_paidOnesStayAnonymous() {
        String admin = adminToken();
        UUID barber = newBarber(admin, "Closed Only");
        UUID declined = insertBooking(barber, "DECLINED", HAIRCUT, uniqueEmail(), null, null);
        UUID refunded = insertBooking(barber, "CANCELLED", HAIRCUT, uniqueEmail(), "REFUNDED", null);
        UUID expired = insertBooking(barber, "EXPIRED", HAIRCUT, uniqueEmail(), "UNPAID", null);

        assertThat(get("/api/admin/staff/" + barber + "/deletion", "en", admin).getBody())
                .containsEntry("allowed", true).containsEntry("deleted", 2).containsEntry("anonymised", 1)
                .containsEntry("active", 0).containsEntry("history", 0);
        assertThat(call(HttpMethod.DELETE, "/api/admin/staff/" + barber + "/permanently", null, "en", admin).getBody())
                .containsEntry("deleted", 2).containsEntry("anonymised", 1);

        assertThat(count("select count(*) from staff where id = ?::uuid", barber)).isZero();
        assertThat(count("select count(*) from booking where id in (?::uuid, ?::uuid)", declined, expired)).isZero();
        Map<String, Object> kept = jdbc.queryForMap("select * from booking where id = ?", refunded);
        assertThat(kept.get("staff_id")).isNull();
        assertThat(kept.get("deleted_at")).isNotNull();
        assertThat(kept.get("customer_email")).isNull();
        assertThat(count("select count(*) from payment where booking_id = ?::uuid and status = 'REFUNDED'", refunded))
                .isOne();
        assertThat(jdbc.queryForMap("select * from admin_audit where target_id = ?", barber))
                .containsEntry("action", "STAFF_DELETED").containsEntry("bookings_deleted", 2)
                .containsEntry("bookings_anonymised", 1);
    }

    @Test
    void aBarberWithActiveBookingsOrHistoryIsNotDeleted() {
        String admin = adminToken();
        UUID active = newBarber(admin, "Active");
        insertBooking(active, "DECLINED", HAIRCUT, uniqueEmail(), null, null);
        insertBooking(active, "CONFIRMED", HAIRCUT, uniqueEmail(), "PAID", null);
        UUID history = newBarber(admin, "History");
        insertBooking(history, "COMPLETED", HAIRCUT, uniqueEmail(), null, null);

        assertThat(get("/api/admin/staff/" + active + "/deletion", "en", admin).getBody())
                .containsEntry("allowed", false).containsEntry("active", 1).containsEntry("deleted", 1);
        ResponseEntity<Map<String, Object>> r = call(HttpMethod.DELETE, "/api/admin/staff/" + active + "/permanently",
                null, "de", admin);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody()).containsEntry("code", "staff.has_active_bookings");
        assertThat((String) r.getBody().get("detail")).contains("1 aktive", "inaktiv");

        r = call(HttpMethod.DELETE, "/api/admin/staff/" + history + "/permanently", null, "en", admin);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody()).containsEntry("code", "staff.has_booking_history");
        assertThat((String) r.getBody().get("detail")).contains("inactive");

        // nothing changed (one transaction): the closed booking of the refused barber is still there
        assertThat(count("select count(*) from booking where staff_id = ?::uuid", active)).isEqualTo(2);
        assertThat(count("select count(*) from staff where id in (?::uuid, ?::uuid)", active, history)).isEqualTo(2);
    }

    /** A booking being created for the barber (uncommitted) holds the delete until it is decided. */
    @Test
    void aBarberDeletionWaitsForABookingInFlightAndThenRefuses() throws Exception {
        String admin = adminToken();
        UUID barber = newBarber(admin, "In Flight");
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (Statement s = c.createStatement()) {
                s.executeUpdate(insertSql(UUID.randomUUID(), barber, "CONFIRMED", uniqueEmail(), null));
            }
            CompletableFuture<ResponseEntity<Map<String, Object>>> delete = CompletableFuture.supplyAsync(() ->
                    call(HttpMethod.DELETE, "/api/admin/staff/" + barber + "/permanently", null, "en", admin));
            Thread.sleep(1500);
            assertThat(delete).as("waits for the booking's transaction").isNotDone();
            c.commit();
            ResponseEntity<Map<String, Object>> r = delete.get(20, TimeUnit.SECONDS);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(r.getBody()).containsEntry("code", "staff.has_active_bookings");
        }
        assertThat(count("select count(*) from staff where id = ?::uuid", barber)).isOne();
    }

    // ── a service ────────────────────────────────────────────────────────────

    @Test
    void aServiceInClosedBookingsOnlyIsDeleted_itemsOfAnonymisedBookingsKeepTheirText() {
        String admin = adminToken();
        UUID service = newService(admin, "Kopfmassage", "Head massage");
        UUID unused = newService(admin, "Unbenutzt", "Unused");
        UUID barber = newBarber(admin, "Offers It"); // not a demo barber: their services stay as seeded
        call(HttpMethod.PUT, "/api/admin/staff/" + barber + "/services", Map.of("serviceIds", List.of(HAIRCUT, service)),
                "en", admin);
        UUID declined = insertBooking(barber, "DECLINED", service, uniqueEmail(), null, null);
        UUID forfeited = insertBooking(barber, "CANCELLED", service, uniqueEmail(), "FORFEITED", null);
        UUID other = insertBooking(barber, "COMPLETED", HAIRCUT, uniqueEmail(), null, null); // another service

        assertThat(get("/api/admin/services/" + service + "/deletion", "en", admin).getBody())
                .containsEntry("allowed", true).containsEntry("deleted", 1).containsEntry("anonymised", 1);
        assertThat(call(HttpMethod.DELETE, "/api/admin/services/" + service + "/permanently", null, "en", admin)
                .getBody()).containsEntry("deleted", 1).containsEntry("anonymised", 1);

        assertThat(count("select count(*) from service where id = ?::uuid", service)).isZero();
        assertThat(count("select count(*) from staff_service where service_id = ?::uuid", service)).isZero();
        assertThat(count("select count(*) from booking where id = ?::uuid", declined)).isZero();
        assertThat(jdbc.queryForMap("select service_id, name_de from booking_item where booking_id = ?", forfeited))
                .containsEntry("service_id", null).containsEntry("name_de", "Kopfmassage");
        assertThat(count("select count(*) from payment where booking_id = ?::uuid and status = 'FORFEITED'", forfeited))
                .isOne();
        assertThat(count("select count(*) from booking where id = ?::uuid and deleted_at is null", other)).isOne();

        // in no booking at all: deleted right away
        assertThat(get("/api/admin/services/" + unused + "/deletion", "en", admin).getBody())
                .containsEntry("allowed", true).containsEntry("deleted", 0).containsEntry("anonymised", 0);
        assertThat(call(HttpMethod.DELETE, "/api/admin/services/" + unused + "/permanently", null, "en", admin)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void aServiceInActiveBookingsOrHistoryIsNotDeleted_deactivatingStillWorks() {
        String admin = adminToken();
        UUID active = newService(admin, "Aktiv", "Active");
        UUID history = newService(admin, "Verlauf", "History");
        insertBooking(LUCA, "AWAITING_PAYMENT", active, uniqueEmail(), "UNPAID", null);
        insertBooking(LUCA, "NO_SHOW", history, uniqueEmail(), "FORFEITED", null);

        ResponseEntity<Map<String, Object>> r = call(HttpMethod.DELETE, "/api/admin/services/" + active
                + "/permanently", null, "en", admin);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody()).containsEntry("code", "service.has_active_bookings");
        r = call(HttpMethod.DELETE, "/api/admin/services/" + history + "/permanently", null, "de", admin);
        assertThat(r.getBody()).containsEntry("code", "service.has_booking_history");
        assertThat((String) r.getBody().get("detail")).contains("inaktiv");
        assertThat(get("/api/admin/services/" + history + "/deletion", "en", admin).getBody())
                .containsEntry("allowed", false).containsEntry("history", 1);

        assertThat(call(HttpMethod.DELETE, "/api/admin/services/" + history, null, "en", admin).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT); // = inactive
        assertThat(jdbc.queryForObject("select active from service where id = ?", Boolean.class, history)).isFalse();
    }

    // ── a customer's history ─────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void aGuestsHistoryIsErased_activeBookingsStay() {
        String admin = adminToken();
        String email = uniqueEmail();
        UUID cancelled = insertBooking(LUCA, "CANCELLED", HAIRCUT, email, null, null);
        UUID completedPaid = insertBooking(LUCA, "COMPLETED", HAIRCUT, email, "PAID", null);
        UUID confirmed = insertBooking(MO, "CONFIRMED", HAIRCUT, email.toUpperCase(), "PAID", null);
        UUID someoneElse = insertBooking(MO, "CANCELLED", HAIRCUT, uniqueEmail(), null, null);

        Map<String, Object> ref = Map.of("email", email);
        assertThat(call(HttpMethod.POST, "/api/admin/customers/history/preview", ref, "en", admin).getBody())
                .containsEntry("allowed", true).containsEntry("deleted", 1).containsEntry("anonymised", 1)
                .containsEntry("active", 1);
        assertThat(call(HttpMethod.POST, "/api/admin/customers/history/delete", ref, "en", admin).getBody())
                .containsEntry("deleted", 1).containsEntry("anonymised", 1).containsEntry("keptActive", 1);

        assertThat(count("select count(*) from booking where id = ?::uuid", cancelled)).isZero();
        assertThat(jdbc.queryForMap("select deleted_at, customer_email from booking where id = ?", completedPaid))
                .containsEntry("customer_email", null).extractingByKey("deleted_at").isNotNull();
        assertThat(count("select count(*) from booking where id = ?::uuid and deleted_at is null", confirmed)).isOne();
        assertThat(count("select count(*) from booking where id = ?::uuid", someoneElse)).isOne();
        // the customer list: only the active booking is left
        Map<String, Object> list = get("/api/admin/customers?q=" + email.substring(0, email.indexOf('@')), "en", admin)
                .getBody();
        assertThat((List<Map<String, Object>>) list.get("items")).singleElement()
                .satisfies(c -> assertThat(c).containsEntry("totalBookings", 1));
        assertThat(jdbc.queryForMap("select * from admin_audit where action = 'CUSTOMER_HISTORY_DELETED' "
                + "order by created_at desc limit 1")).containsEntry("target_type", "GUEST")
                .containsEntry("target_id", null);
    }

    @Test
    void aCustomerAccountStaysWhenItsHistoryIsErased() {
        String admin = adminToken();
        UUID user = UUID.randomUUID();
        String email = uniqueEmail();
        jdbc.update("""
                insert into app_user (id, tenant_id, email, password_hash, role, name, locale, created_at,
                                      email_verified_at, failed_login_count, must_change_password)
                values (?, ?, ?, 'x', 'CUSTOMER', 'Account Customer', 'de', now(), now(), 0, false)""",
                user, TENANT_ID, email);
        insertBooking(LUCA, "COMPLETED", HAIRCUT, email, null, user);
        insertBooking(LUCA, "NO_SHOW", HAIRCUT, email, "FORFEITED", user);

        assertThat(call(HttpMethod.POST, "/api/admin/customers/history/delete", Map.of("userId", user), "en", admin)
                .getBody()).containsEntry("deleted", 1).containsEntry("anonymised", 1).containsEntry("keptActive", 0);
        assertThat(count("select count(*) from app_user where id = ?::uuid", user)).isOne();
        assertThat(count("select count(*) from booking where user_id = ?::uuid", user)).isZero();

        // exactly one way to name the customer
        assertThat(call(HttpMethod.POST, "/api/admin/customers/history/delete", Map.of("userId", user, "email", email),
                "en", admin).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(HttpMethod.POST, "/api/admin/customers/history/delete", Map.of("userId", UUID.randomUUID()),
                "en", admin).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── access ───────────────────────────────────────────────────────────────

    @Test
    void anotherShopsDataIsInvisibleAndUntouched() {
        String admin = adminToken();
        UUID otherTenant = UUID.randomUUID();
        UUID otherStaff = UUID.randomUUID();
        UUID otherService = UUID.randomUUID();
        String email = uniqueEmail();
        jdbc.update("insert into tenant (id, name, slug) values (?, 'Other Shop', ?)", otherTenant,
                "other-" + otherTenant.toString().substring(0, 8));
        jdbc.update("insert into staff (id, tenant_id, name) values (?, ?, 'Other')", otherStaff, otherTenant);
        jdbc.update("insert into service (id, tenant_id, name_de, name_en, duration_min, price_cents) "
                + "values (?, ?, 'Fremd', 'Foreign', 30, 1000)", otherService, otherTenant);
        UUID otherBooking = insertBooking(otherTenant, otherStaff, "CANCELLED", otherService, email, null, null);
        try {
            for (var req : List.of(
                    new Object[] {HttpMethod.DELETE, "/api/admin/bookings/" + otherBooking},
                    new Object[] {HttpMethod.GET, "/api/admin/staff/" + otherStaff + "/deletion"},
                    new Object[] {HttpMethod.DELETE, "/api/admin/staff/" + otherStaff + "/permanently"},
                    new Object[] {HttpMethod.GET, "/api/admin/services/" + otherService + "/deletion"},
                    new Object[] {HttpMethod.DELETE, "/api/admin/services/" + otherService + "/permanently"})) {
                assertThat(call((HttpMethod) req[0], (String) req[1], null, "en", admin).getStatusCode())
                        .as(req[1].toString()).isEqualTo(HttpStatus.NOT_FOUND);
            }
            assertThat(call(HttpMethod.POST, "/api/admin/customers/history/delete", Map.of("email", email), "en",
                    admin).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(count("select count(*) from booking where id = ?::uuid and customer_email is not null",
                    otherBooking)).isOne();
            assertThat(count("select count(*) from staff where id = ?::uuid", otherStaff)).isOne();
            assertThat(count("select count(*) from service where id = ?::uuid", otherService)).isOne();
        } finally {
            jdbc.update("delete from booking where id = ?", otherBooking);
            jdbc.update("delete from tenant where id = ?", otherTenant);
        }
    }

    @Test
    void onlyAdminsMayDelete() {
        UUID id = insertBooking(LUCA, "CANCELLED", HAIRCUT, uniqueEmail(), null, null);
        assertThat(call(HttpMethod.DELETE, "/api/admin/bookings/" + id, null, "en", null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        String email = uniqueEmail();
        jdbc.update("""
                insert into app_user (id, tenant_id, email, password_hash, role, name, locale, created_at,
                                      email_verified_at, failed_login_count, must_change_password)
                values (?, ?, ?, ?, 'CUSTOMER', 'Customer', 'de', now(), now(), 0, false)""",
                UUID.randomUUID(), TENANT_ID, email, passwordEncoder.encode("customer-password-1"));
        String customer = login(email, "customer-password-1");
        assertThat(call(HttpMethod.DELETE, "/api/admin/bookings/" + id, null, "en", customer).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(count("select count(*) from booking where id = ?::uuid", id)).isOne();
    }

    // ── the migration ────────────────────────────────────────────────────────

    @Test
    void theMigrationCanRunAgain() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V10__admin_erasure.sql"));
        try (Connection c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), DbRoles.MIGRATOR,
                DbRoles.MIGRATOR_PASSWORD); Statement s = c.createStatement()) {
            s.execute(sql);
        }
        assertThat(jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where table_name = 'booking' and column_name = 'deleted_at'""", Long.class)).isOne();
        // a live booking without barber is still impossible
        UUID id = insertBooking(LUCA, "CANCELLED", HAIRCUT, uniqueEmail(), null, null);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                jdbc.update("update booking set staff_id = null where id = ?", id))).isNotNull();
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private UUID newBarber(String admin, String name) {
        UUID id = UUID.fromString((String) call(HttpMethod.POST, "/api/admin/staff", Map.of("name", name), "en", admin)
                .getBody().get("id"));
        call(HttpMethod.PUT, "/api/admin/staff/" + id + "/services", Map.of("serviceIds", List.of(HAIRCUT)), "en", admin);
        List<Map<String, Object>> week = new ArrayList<>();
        for (int d = 1; d <= 6; d++) week.add(Map.of("weekday", d, "start", "09:00", "end", "19:00"));
        rest.exchange("/api/admin/staff/" + id + "/working-hours", HttpMethod.PUT,
                new HttpEntity<>(Map.of("entries", week), headers("en", admin)), JSON_LIST);
        return id;
    }

    private UUID newService(String admin, String nameDe, String nameEn) {
        return UUID.fromString((String) call(HttpMethod.POST, "/api/admin/services", Map.of("nameDe", nameDe,
                "nameEn", nameEn, "durationMin", 30, "priceCents", 2000, "priceFrom", false, "active", true,
                "sortOrder", 90), "en", admin).getBody().get("id"));
    }

    private UUID insertBooking(UUID staff, String status, UUID service, String email, String payment, UUID user) {
        return insertBooking(TENANT_ID, staff, status, service, email, payment, user);
    }

    /** A booking (+ item, + payment with events unless {@code payment} is null), each in its own hour. */
    private UUID insertBooking(UUID tenant, UUID staff, String status, UUID service, String email, String payment,
                               UUID user) {
        UUID id = UUID.randomUUID();
        Instant start = Instant.now().plus(Duration.ofDays(20)).plus(Duration.ofHours(SLOT.incrementAndGet()))
                .truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        jdbc.update("""
                insert into booking (id, tenant_id, staff_id, user_id, status, start_time, end_time, blocked_until,
                  customer_name, customer_email, customer_phone, customer_note, locale, currency,
                  price_total_cents, deposit_cents, amount_due_at_shop_cents, status_reason)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '+49 151 2345678', 'Bitte klingeln', 'de', 'EUR',
                  3500, 1000, 2500, 'Grund')""",
                id, tenant, staff, user, status, Timestamp.from(start), Timestamp.from(start.plusSeconds(1800)),
                Timestamp.from(start.plusSeconds(1800)), nameFor(email), email);
        jdbc.update("""
                insert into booking_item (id, booking_id, service_id, name_de, name_en, duration_min, price_cents,
                  position)
                select ?, ?, s.id, s.name_de, s.name_en, 30, s.price_cents, 0 from service s where s.id = ?""",
                UUID.randomUUID(), id, service);
        if (payment != null) {
            UUID p = UUID.randomUUID();
            jdbc.update("""
                    insert into payment (id, tenant_id, booking_id, provider, provider_ref, amount_cents, currency,
                      status, paid_at)
                    values (?, ?, ?, 'demo', ?, 1000, 'EUR', ?, case when ? = 'UNPAID' then null else now() end)""",
                    p, tenant, id, "demo-" + p, payment, payment);
            jdbc.update("insert into payment_event (id, payment_id, type, actor) values (?, ?, 'CREATED', 'SYSTEM')",
                    UUID.randomUUID(), p);
            if (!payment.equals("UNPAID")) {
                jdbc.update("insert into payment_event (id, payment_id, type, actor) "
                        + "values (?, ?, 'SUCCEEDED', 'CUSTOMER')", UUID.randomUUID(), p);
            }
        }
        return id;
    }

    /** The same booking as plain SQL, for a transaction outside Spring. */
    private static String insertSql(UUID id, UUID staff, String status, String email, String payment) {
        Instant start = Instant.now().plus(Duration.ofDays(20)).plus(Duration.ofHours(SLOT.incrementAndGet()))
                .truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        return """
                insert into booking (id, tenant_id, staff_id, status, start_time, end_time, blocked_until,
                  customer_name, customer_email, locale, currency, price_total_cents, deposit_cents,
                  amount_due_at_shop_cents)
                values ('%s', '%s', '%s', '%s', '%s', '%s', '%s', 'Kunde', '%s', 'de', 'EUR', 3500, 0, 3500)"""
                .formatted(id, TENANT_ID, staff, status, start, start.plusSeconds(1800), start.plusSeconds(1800),
                        email);
    }

    private UUID insertMail(UUID booking, String status, String recipient) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into mail_outbox (id, tenant_id, kind, booking_id, recipient, subject, lang, html, status)
                values (?, ?, 'requested', ?, ?, 'Subject', 'de', '<p>Hi</p>', ?)""",
                id, TENANT_ID, booking, recipient, status);
        return id;
    }

    private long count(String sql, Object id) {
        return jdbc.queryForObject(sql, Long.class, id.toString());
    }

    private long count(String sql, Object a, Object b) {
        return jdbc.queryForObject(sql, Long.class, a.toString(), b.toString());
    }
}
