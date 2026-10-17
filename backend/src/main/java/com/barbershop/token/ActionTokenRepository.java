package com.barbershop.token;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActionTokenRepository extends JpaRepository<ActionToken, UUID> {

    Optional<ActionToken> findByTokenHash(String tokenHash);

    /** Row lock, so two concurrent POSTs with the same link cannot both use it. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ActionToken t where t.tokenHash = :hash")
    Optional<ActionToken> findByTokenHashForUpdate(@Param("hash") String hash);

    /** Invalidates all still-open links of a booking for a purpose (e.g. after a decision). */
    @Modifying
    @Query("""
            update ActionToken t set t.usedAt = :now
            where t.bookingId = :bookingId and t.purpose = :purpose and t.usedAt is null""")
    int invalidateForBooking(@Param("bookingId") UUID bookingId, @Param("purpose") TokenPurpose purpose,
                             @Param("now") Instant now);

    @Modifying
    @Query("""
            update ActionToken t set t.usedAt = :now
            where t.userId = :userId and t.purpose = :purpose and t.usedAt is null""")
    int invalidateForUser(@Param("userId") UUID userId, @Param("purpose") TokenPurpose purpose,
                          @Param("now") Instant now);

    @Modifying
    @Query("delete from ActionToken t where t.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
