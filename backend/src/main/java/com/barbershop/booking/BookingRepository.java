package com.barbershop.booking;

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
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, UUID>, JpaSpecificationExecutor<Booking> {

    /** Active (not cancelled) bookings of these staff members overlapping [from, to). */
    @Query("""
            select b from Booking b
            where b.staff.id in :staffIds
              and b.status <> com.barbershop.booking.BookingStatus.CANCELLED
              and b.startTime < :to and b.endTime > :from""")
    List<Booking> findActiveOverlapping(@Param("staffIds") Collection<UUID> staffIds, @Param("from") Instant from,
                                        @Param("to") Instant to);

    @Query("""
            select count(b) > 0 from Booking b
            where b.staff.id = :staffId
              and b.status <> com.barbershop.booking.BookingStatus.CANCELLED
              and b.startTime < :end and b.endTime > :start""")
    boolean existsActiveOverlap(@Param("staffId") UUID staffId, @Param("start") Instant start,
                                @Param("end") Instant end);

    @Query("""
            select count(b) from Booking b
            where b.staff.id = :staffId
              and b.status <> com.barbershop.booking.BookingStatus.CANCELLED
              and b.startTime >= :from and b.startTime < :to""")
    long countActiveForStaff(@Param("staffId") UUID staffId, @Param("from") Instant from, @Param("to") Instant to);

    @EntityGraph(attributePaths = {"service", "staff"})
    Optional<Booking> findByCancelTokenHash(String cancelTokenHash);

    @EntityGraph(attributePaths = {"service", "staff"})
    Optional<Booking> findByIdAndTenantId(UUID id, UUID tenantId);

    @EntityGraph(attributePaths = {"service", "staff"})
    Optional<Booking> findWithDetailsById(UUID id);

    /** Admin list: fetch service + staff in the same query (no N+1). */
    @Override
    @EntityGraph(attributePaths = {"service", "staff"})
    Page<Booking> findAll(Specification<Booking> spec, Pageable pageable);
}
