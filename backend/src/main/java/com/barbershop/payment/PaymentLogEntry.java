package com.barbershop.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Audit record of a payment (table payment_event): created, succeeded, duplicate
 * success ignored, refunded (simulated in demo mode), forfeited, override …
 * provider_event_id is unique, so a provider event is only ever applied once.
 */
@Entity
@Table(name = "payment_event")
public class PaymentLogEntry {

    public enum Type {
        CREATED, SUCCEEDED, DUPLICATE_SUCCESS_IGNORED, REFUNDED, FORFEITED, REFUND_OVERRIDE, LATE_PAYMENT_REFUNDED
    }

    @Id
    private UUID id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(nullable = false, updatable = false)
    private String type;

    @Column(name = "provider_event_id", updatable = false, unique = true)
    private String providerEventId;

    @Column(updatable = false)
    private String actor;

    @Column(updatable = false)
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PaymentLogEntry() {}

    PaymentLogEntry(UUID paymentId, Type type, String providerEventId, String actor, String detail, Instant now) {
        this.id = UUID.randomUUID();
        this.paymentId = paymentId;
        this.type = type.name();
        this.providerEventId = providerEventId;
        this.actor = actor;
        this.detail = detail;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public UUID getPaymentId() { return paymentId; }
    public String getType() { return type; }
    public String getProviderEventId() { return providerEventId; }
    public String getActor() { return actor; }
    public String getDetail() { return detail; }
    public Instant getCreatedAt() { return createdAt; }
}
