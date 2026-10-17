package com.barbershop.staff;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimeOffRepository extends JpaRepository<TimeOff, UUID> {

    /** Absences of the given staff members or shop-wide closures overlapping [from, to). */
    @Query("""
            select t from TimeOff t
            where t.tenantId = :tenantId
              and (t.staffId is null or t.staffId in :staffIds)
              and t.startAt < :to and t.endAt > :from""")
    List<TimeOff> findOverlapping(@Param("tenantId") UUID tenantId, @Param("staffIds") Collection<UUID> staffIds,
                                  @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select t from TimeOff t
            where t.tenantId = :tenantId and t.startAt < :to and t.endAt > :from
            order by t.startAt""")
    List<TimeOff> findForTenant(@Param("tenantId") UUID tenantId, @Param("from") Instant from,
                                @Param("to") Instant to);

    Optional<TimeOff> findByIdAndTenantId(UUID id, UUID tenantId);
}
