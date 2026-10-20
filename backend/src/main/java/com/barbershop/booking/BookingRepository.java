package com.barbershop.booking;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Items are loaded lazily in batches (hibernate.default_batch_fetch_size), staff via entity graphs. */
public interface BookingRepository extends JpaRepository<Booking, UUID>, JpaSpecificationExecutor<Booking> {

    /** Slot-blocking bookings of these barbers overlapping [from, to) — including their buffer. */
    @Query("""
            select b from Booking b
            where b.staff.id in :staffIds
              and b.status in :statuses
              and b.startTime < :to and b.blockedUntil > :from""")
    List<Booking> findOverlapping(@Param("staffIds") Collection<UUID> staffIds,
                                  @Param("statuses") Collection<BookingStatus> statuses,
                                  @Param("from") Instant from, @Param("to") Instant to);

    default List<Booking> findBlockingOverlapping(Collection<UUID> staffIds, Instant from, Instant to) {
        return findOverlapping(staffIds, BookingStatus.SLOT_BLOCKING, from, to);
    }

    /** Row lock for every status transition (no entity graph: FOR UPDATE + outer joins is not allowed). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"staff"})
    Optional<Booking> findByIdAndTenantId(UUID id, UUID tenantId);

    @EntityGraph(attributePaths = {"staff"})
    Optional<Booking> findWithDetailsById(UUID id);

    @Override
    @EntityGraph(attributePaths = {"staff"})
    Page<Booking> findAll(Specification<Booking> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"staff"})
    @Query("""
            select b from Booking b
            where b.userId = :userId and b.startTime >= :from order by b.startTime asc""")
    List<Booking> findUpcomingForUser(@Param("userId") UUID userId, @Param("from") Instant from);

    @EntityGraph(attributePaths = {"staff"})
    @Query("""
            select b from Booking b
            where b.userId = :userId and b.startTime < :before order by b.startTime desc""")
    List<Booking> findPastForUser(@Param("userId") UUID userId, @Param("before") Instant before);

    @EntityGraph(attributePaths = {"staff"})
    List<Booking> findByUserIdOrderByStartTimeDesc(UUID userId);

    @EntityGraph(attributePaths = {"staff"})
    @Query("""
            select b from Booking b
            where b.staff.id = :staffId and b.startTime < :to and b.endTime > :from
            order by b.startTime""")
    List<Booking> findForStaffBetween(@Param("staffId") UUID staffId, @Param("from") Instant from,
                                      @Param("to") Instant to);

    @EntityGraph(attributePaths = {"staff"})
    List<Booking> findByStaffIdAndStatusOrderByStartTimeAsc(UUID staffId, BookingStatus status);

    /** Open requests per email address (anti-spam limit for guests). */
    @Query("""
            select count(b) from Booking b
            where b.tenantId = :tenantId and lower(b.customerEmail) = lower(:email)
              and b.status in (com.barbershop.booking.BookingStatus.PENDING_BARBER,
                               com.barbershop.booking.BookingStatus.AWAITING_PAYMENT)""")
    long countOpenRequestsForEmail(@Param("tenantId") UUID tenantId, @Param("email") String email);

    // ── guest → account linking ─────────────────────────────────────────────

    @Modifying
    @Query("""
            update Booking b set b.userId = :userId
            where b.tenantId = :tenantId and b.userId is null and lower(b.customerEmail) = lower(:email)""")
    int linkGuestBookings(@Param("tenantId") UUID tenantId, @Param("email") String email,
                          @Param("userId") UUID userId);

    long countByUserIdAndStatus(UUID userId, BookingStatus status);

    // ── scheduled jobs (ids only; each booking is then processed under a row lock) ──

    @Query("""
            select b.id from Booking b
            where b.status = com.barbershop.booking.BookingStatus.PENDING_BARBER and b.barberDeadlineAt <= :now
            order by b.barberDeadlineAt""")
    List<UUID> findIdsAwaitingBarberPastDeadline(@Param("now") Instant now, Pageable page);

    @Query("""
            select b.id from Booking b
            where b.status = com.barbershop.booking.BookingStatus.AWAITING_PAYMENT and b.paymentDeadlineAt <= :now
            order by b.paymentDeadlineAt""")
    List<UUID> findIdsAwaitingPaymentPastDeadline(@Param("now") Instant now, Pageable page);

    /** CONFIRMED bookings whose end lies before {@code endedBefore} (= now − auto-complete delay, per tenant). */
    @Query(value = """
            select b.id from booking b join tenant t on t.id = b.tenant_id
            where b.status = 'CONFIRMED'
              and b.end_time + make_interval(mins => t.auto_complete_after_min) <= :now
            order by b.end_time
            limit :limit""", nativeQuery = true)
    List<UUID> findIdsToAutoComplete(@Param("now") Instant now, @Param("limit") int limit);

    /** CONFIRMED bookings starting within the tenant's reminder window that got no reminder yet. */
    @Query(value = """
            select b.id from booking b join tenant t on t.id = b.tenant_id
            where b.status = 'CONFIRMED' and b.reminder_sent_at is null
              and b.customer_email is not null
              and b.start_time > :now
              and b.start_time - make_interval(mins => t.reminder_before_min) <= :now
            order by b.start_time
            limit :limit""", nativeQuery = true)
    List<UUID> findIdsDueForReminder(@Param("now") Instant now, @Param("limit") int limit);
}
