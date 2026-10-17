package com.barbershop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.barbershop.support.AbstractIntegrationTest;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;

/**
 * Double-booking protection under real concurrency: many clients book the same
 * slot at the same instant. Exactly the possible number of bookings succeed; all
 * others get 409. The last line of defence is the PostgreSQL exclusion constraint.
 */
class BookingConcurrencyIT extends AbstractIntegrationTest {

    private static final int CLIENTS = 16;

    @Test
    void manyClientsBookTheSameBarberAndSlot_exactlyOneWins() throws Exception {
        OffsetDateTime slot = at(next(DayOfWeek.WEDNESDAY), "15:00");
        List<ResponseEntity<Map<String, Object>>> results = race(() -> booking(HAIRCUT, LUCA, slot, uniqueEmail(), "de"));

        assertThat(results).filteredOn(r -> r.getStatusCode().value() == 201).hasSize(1);
        assertThat(results).filteredOn(r -> r.getStatusCode().value() == 409).hasSize(CLIENTS - 1)
                .allSatisfy(r -> assertThat(r.getBody()).containsEntry("code", "booking.slot_taken"));
        assertThat(activeBookingsAt(LUCA, slot)).isEqualTo(1);
    }

    @Test
    void manyClientsBookAnyBarber_eachFreeBarberIsBookedOnce() throws Exception {
        OffsetDateTime slot = at(next(DayOfWeek.WEDNESDAY), "10:30"); // Luca and Mo both work
        List<ResponseEntity<Map<String, Object>>> results = race(() -> booking(HAIRCUT, null, slot, uniqueEmail(), "de"));

        List<ResponseEntity<Map<String, Object>>> created = results.stream()
                .filter(r -> r.getStatusCode().value() == 201).toList();
        assertThat(created).hasSize(2);
        assertThat(created).extracting(r -> (Object) ((Map<?, ?>) r.getBody().get("staff")).get("name"))
                .containsExactlyInAnyOrder("Luca", "Mo");
        assertThat(results).filteredOn(r -> r.getStatusCode().value() == 409).hasSize(CLIENTS - 2);
        assertThat(activeBookingsAt(LUCA, slot) + activeBookingsAt(MO, slot)).isEqualTo(2);
    }

    @Test
    void theDatabaseItselfRejectsOverlappingActiveBookings() {
        LocalDate day = next(DayOfWeek.THURSDAY);
        insert(LUCA, at(day, "10:00"), at(day, "10:45"), "CONFIRMED");

        // overlapping active booking for the same barber → exclusion_violation (23P01)
        assertThatThrownBy(() -> insert(LUCA, at(day, "10:30"), at(day, "11:00"), "PENDING"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(sqlState(e)).isEqualTo("23P01"))
                .hasMessageContaining("booking_no_overlap");

        // touching intervals are fine ([) ranges), other barbers too, and cancelled bookings never block
        insert(LUCA, at(day, "10:45"), at(day, "11:15"), "CONFIRMED");
        insert(MO, at(day, "10:00"), at(day, "10:45"), "CONFIRMED");
        insert(LUCA, at(day, "10:00"), at(day, "10:45"), "CANCELLED");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Fires all requests at the same moment (latch) from separate threads. */
    private List<ResponseEntity<Map<String, Object>>> race(java.util.function.Supplier<Map<String, Object>> body)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(CLIENTS);
        CountDownLatch ready = new CountDownLatch(CLIENTS);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<ResponseEntity<Map<String, Object>>>> futures = new ArrayList<>();
        for (int i = 0; i < CLIENTS; i++) {
            Map<String, Object> request = body.get();
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return book(request);
            }));
        }
        ready.await(10, TimeUnit.SECONDS);
        go.countDown();
        List<ResponseEntity<Map<String, Object>>> results = new ArrayList<>();
        for (Future<ResponseEntity<Map<String, Object>>> f : futures) results.add(f.get(30, TimeUnit.SECONDS));
        pool.shutdown();
        return results;
    }

    private long activeBookingsAt(UUID staff, OffsetDateTime start) {
        return jdbc.queryForObject("select count(*) from booking where staff_id = ? and start_time = ? "
                + "and status <> 'CANCELLED'", Long.class, staff, Timestamp.from(start.toInstant()));
    }

    private void insert(UUID staff, OffsetDateTime start, OffsetDateTime end, String status) {
        jdbc.update("""
                insert into booking (id, tenant_id, service_id, staff_id, start_time, end_time, customer_name,
                  customer_phone, customer_email, locale, status, cancel_token_hash)
                values (?, ?, ?, ?, ?, ?, 'Direct', '+49 1', 'direct@example.com', 'de', ?, ?)""",
                UUID.randomUUID(), TENANT_ID, HAIRCUT, staff, Timestamp.from(start.toInstant()),
                Timestamp.from(end.toInstant()), status, UUID.randomUUID().toString());
    }

    private static String sqlState(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql) return sql.getSQLState();
        }
        return null;
    }
}
