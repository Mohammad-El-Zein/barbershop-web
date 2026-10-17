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
 * others get 409. The last line of defence is the PostgreSQL exclusion constraint
 * over all slot-blocking statuses (incl. PENDING_BARBER requests).
 */
class BookingConcurrencyIT extends AbstractIntegrationTest {

    private static final int CLIENTS = 16;

    @Test
    void manyClientsRequestTheSameBarberAndSlot_exactlyOneWins() throws Exception {
        OffsetDateTime slot = at(next(DayOfWeek.WEDNESDAY), "15:00");
        List<ResponseEntity<Map<String, Object>>> results =
                race(() -> booking(List.of(HAIRCUT, BEARD), LUCA, slot, uniqueEmail(), "de"));

        assertThat(results).filteredOn(r -> r.getStatusCode().value() == 201).hasSize(1)
                .allSatisfy(r -> assertThat(r.getBody()).containsEntry("status", "PENDING_BARBER"));
        assertThat(results).filteredOn(r -> r.getStatusCode().value() == 409).hasSize(CLIENTS - 1)
                .allSatisfy(r -> assertThat(r.getBody()).containsEntry("code", "booking.slot_taken"));
        assertThat(activeBookingsAt(LUCA, slot)).isEqualTo(1);
    }

    @Test
    void manyClientsRequestAnyBarber_eachFreeBarberIsBookedOnce() throws Exception {
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
    void theDatabaseItselfRejectsOverlappingSlotBlockingBookings() {
        LocalDate day = next(DayOfWeek.THURSDAY);
        insert(LUCA, at(day, "10:00"), at(day, "10:45"), "CONFIRMED");

        // every slot-blocking status collides …
        for (String blocking : List.of("PENDING_BARBER", "AWAITING_PAYMENT", "CONFIRMED", "COMPLETED")) {
            assertThatThrownBy(() -> insert(LUCA, at(day, "10:30"), at(day, "11:00"), blocking))
                    .as(blocking)
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .satisfies(e -> assertThat(sqlState(e)).isEqualTo("23P01"))
                    .hasMessageContaining("booking_no_overlap");
        }
        // … touching intervals are fine ([) ranges), other barbers too, and freed statuses never block
        insert(LUCA, at(day, "10:45"), at(day, "11:15"), "CONFIRMED");
        insert(MO, at(day, "10:00"), at(day, "10:45"), "CONFIRMED");
        for (String freeing : List.of("DECLINED", "EXPIRED", "CANCELLED", "NO_SHOW")) {
            insert(LUCA, at(day, "10:00"), at(day, "10:45"), freeing);
        }
    }

    @Test
    void theBufferIsPartOfTheBlockedTime() {
        jdbc.update("update tenant set buffer_min = 15 where id = ?", TENANT_ID);
        OffsetDateTime first = at(next(DayOfWeek.FRIDAY), "10:00");
        assertThat(book(booking(HAIRCUT, LUCA, first, uniqueEmail(), "de")).getStatusCode().value()).isEqualTo(201);
        // 10:45 would start inside Luca's cleanup buffer (10:45–11:00)
        assertThat(book(booking(HAIRCUT, LUCA, at(next(DayOfWeek.FRIDAY), "10:45"), uniqueEmail(), "de")).getBody())
                .containsEntry("code", "booking.slot_taken");
        assertThat(book(booking(HAIRCUT, LUCA, at(next(DayOfWeek.FRIDAY), "11:00"), uniqueEmail(), "de"))
                .getStatusCode().value()).isEqualTo(201);
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
                + "and status in ('PENDING_BARBER','AWAITING_PAYMENT','CONFIRMED','COMPLETED')", Long.class, staff,
                Timestamp.from(start.toInstant()));
    }

    private void insert(UUID staff, OffsetDateTime start, OffsetDateTime end, String status) {
        jdbc.update("""
                insert into booking (id, tenant_id, staff_id, status, start_time, end_time, blocked_until,
                  customer_name, locale, currency, price_total_cents, deposit_cents, amount_due_at_shop_cents)
                values (?, ?, ?, ?, ?, ?, ?, 'Direct', 'de', 'EUR', 3500, 0, 3500)""",
                UUID.randomUUID(), TENANT_ID, staff, status, Timestamp.from(start.toInstant()),
                Timestamp.from(end.toInstant()), Timestamp.from(end.toInstant()));
    }

    private static String sqlState(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql) return sql.getSQLState();
        }
        return null;
    }
}
