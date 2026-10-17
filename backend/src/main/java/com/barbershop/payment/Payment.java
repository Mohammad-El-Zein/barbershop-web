package com.barbershop.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** The deposit for one booking. Status changes are recorded in payment_event. */
@Entity
@Table(name = "payment")
public class Payment {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "booking_id", nullable = false, updatable = false, unique = true)
    private UUID bookingId;

    @Column(nullable = false, updatable = false)
    private String provider;

    @Column(name = "provider_ref", nullable = false, updatable = false, unique = true)
    private String providerRef;

    @Column(name = "amount_cents", nullable = false, updatable = false)
    private int amountCents;

    @Column(nullable = false, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "forfeited_at")
    private Instant forfeitedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() {}

    Payment(UUID tenantId, UUID bookingId, String provider, String providerRef, int amountCents, String currency,
            Instant now) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.bookingId = bookingId;
        this.provider = provider;
        this.providerRef = providerRef;
        this.amountCents = amountCents;
        this.currency = currency;
        this.status = PaymentStatus.UNPAID;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void markPaid(Instant now) {
        require(PaymentStatus.UNPAID);
        status = PaymentStatus.PAID;
        paidAt = now;
        updatedAt = now;
    }

    /** PAID → REFUNDED, or FORFEITED → REFUNDED (admin override). */
    void markRefunded(Instant now) {
        if (status != PaymentStatus.PAID && status != PaymentStatus.FORFEITED) {
            throw new IllegalStateException("cannot refund a " + status + " payment");
        }
        status = PaymentStatus.REFUNDED;
        refundedAt = now;
        updatedAt = now;
    }

    void markForfeited(Instant now) {
        require(PaymentStatus.PAID);
        status = PaymentStatus.FORFEITED;
        forfeitedAt = now;
        updatedAt = now;
    }

    private void require(PaymentStatus expected) {
        if (status != expected) throw new IllegalStateException("payment is " + status + ", expected " + expected);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getBookingId() { return bookingId; }
    public String getProvider() { return provider; }
    public String getProviderRef() { return providerRef; }
    public int getAmountCents() { return amountCents; }
    public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getRefundedAt() { return refundedAt; }
    public Instant getForfeitedAt() { return forfeitedAt; }
}
