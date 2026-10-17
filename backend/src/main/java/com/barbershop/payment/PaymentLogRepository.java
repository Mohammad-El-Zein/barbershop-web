package com.barbershop.payment;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentLogRepository extends JpaRepository<PaymentLogEntry, UUID> {

    List<PaymentLogEntry> findByPaymentIdOrderByCreatedAtAsc(UUID paymentId);

    boolean existsByProviderEventId(String providerEventId);
}
