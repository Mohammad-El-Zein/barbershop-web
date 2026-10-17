package com.barbershop.token;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A single-use, expiring email-link token. Only its keyed hash is stored; the raw
 * value exists only in the email. "Used" is set when the action (POST) happens —
 * opening the link (GET) never changes anything, because email scanners open links.
 */
@Entity
@Table(name = "action_token")
public class ActionToken {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TokenPurpose purpose;

    @Column(name = "token_hash", nullable = false, unique = true, updatable = false)
    private String tokenHash;

    @Column(name = "booking_id", updatable = false)
    private UUID bookingId;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "payment_id", updatable = false)
    private UUID paymentId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ActionToken() {}

    ActionToken(UUID tenantId, TokenPurpose purpose, String tokenHash, UUID bookingId, UUID userId, UUID paymentId,
                Instant expiresAt, Instant now) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.purpose = purpose;
        this.tokenHash = tokenHash;
        this.bookingId = bookingId;
        this.userId = userId;
        this.paymentId = paymentId;
        this.expiresAt = expiresAt;
        this.createdAt = now;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    void markUsed(Instant now) {
        this.usedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public TokenPurpose getPurpose() { return purpose; }
    public UUID getBookingId() { return bookingId; }
    public UUID getUserId() { return userId; }
    public UUID getPaymentId() { return paymentId; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
}
