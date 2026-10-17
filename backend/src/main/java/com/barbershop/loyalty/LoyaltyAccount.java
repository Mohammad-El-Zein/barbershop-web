package com.barbershop.loyalty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Loyalty state of one customer account (accounts only — guests do not collect visits). */
@Entity
@Table(name = "loyalty_account")
public class LoyaltyAccount {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "visits_in_cycle", nullable = false)
    private int visitsInCycle;

    @Column(name = "total_visits", nullable = false)
    private int totalVisits;

    @Column(name = "rewards_used", nullable = false)
    private int rewardsUsed;

    @Column(name = "reserved_booking_id")
    private UUID reservedBookingId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LoyaltyAccount() {}

    LoyaltyAccount(UUID userId, UUID tenantId, Instant now) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.updatedAt = now;
    }

    void reserve(UUID bookingId, Instant now) {
        this.reservedBookingId = bookingId;
        this.updatedAt = now;
    }

    void release(Instant now) {
        this.reservedBookingId = null;
        this.updatedAt = now;
    }

    void recordVisit(boolean consumedDiscount, int threshold, Instant now) {
        this.visitsInCycle = LoyaltyRules.afterCompletion(visitsInCycle, consumedDiscount, threshold);
        this.totalVisits++;
        if (consumedDiscount) {
            this.rewardsUsed++;
            this.reservedBookingId = null;
        }
        this.updatedAt = now;
    }

    void addVisits(int count, Instant now) {
        this.visitsInCycle += count;
        this.totalVisits += count;
        this.updatedAt = now;
    }

    public UUID getUserId() { return userId; }
    public UUID getTenantId() { return tenantId; }
    public int getVisitsInCycle() { return visitsInCycle; }
    public int getTotalVisits() { return totalVisits; }
    public int getRewardsUsed() { return rewardsUsed; }
    public UUID getReservedBookingId() { return reservedBookingId; }
}
