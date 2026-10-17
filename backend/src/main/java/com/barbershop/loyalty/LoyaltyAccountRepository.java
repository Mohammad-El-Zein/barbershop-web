package com.barbershop.loyalty;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoyaltyAccountRepository extends JpaRepository<LoyaltyAccount, UUID> {

    /** Row lock: two parallel bookings of the same customer must not both get the reward. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from LoyaltyAccount a where a.userId = :userId")
    Optional<LoyaltyAccount> findForUpdate(@Param("userId") UUID userId);

    /** Creates the row if missing, so it can always be locked afterwards. */
    @Modifying
    @Query(value = """
            insert into loyalty_account (user_id, tenant_id) values (:userId, :tenantId)
            on conflict (user_id) do nothing""", nativeQuery = true)
    void ensureExists(@Param("userId") UUID userId, @Param("tenantId") UUID tenantId);
}
