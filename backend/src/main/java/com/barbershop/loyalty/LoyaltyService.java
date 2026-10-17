package com.barbershop.loyalty;

import com.barbershop.tenant.BookingPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loyalty bookkeeping. The booking lifecycle calls it inside its own transaction:
 * reserve on creation, release on cancel/decline/expiry/no-show, record a visit
 * on completion. The account row is locked for every change.
 */
@Service
public class LoyaltyService {

    private final LoyaltyAccountRepository accounts;
    private final Clock clock;

    public LoyaltyService(LoyaltyAccountRepository accounts, Clock clock) {
        this.accounts = accounts;
        this.clock = clock;
    }

    /** What the API exposes: visits_in_current_cycle and next_discount_available. */
    public record LoyaltyView(int visitsInCurrentCycle, int threshold, int discountPercent,
                              boolean nextDiscountAvailable, boolean discountReserved, UUID reservedBookingId,
                              int totalVisits, int rewardsUsed) {}

    @Transactional(readOnly = true)
    public LoyaltyView view(UUID userId, BookingPolicy policy) {
        return accounts.findById(userId)
                .map(a -> view(a, policy))
                .orElseGet(() -> new LoyaltyView(0, policy.getLoyaltyThreshold(), policy.getLoyaltyPercent(),
                        false, false, null, 0, 0));
    }

    /** Would a new booking of this customer get the discount right now? (price preview) */
    @Transactional(readOnly = true)
    public boolean discountAvailable(UUID userId, BookingPolicy policy) {
        return accounts.findById(userId)
                .map(a -> LoyaltyRules.discountAvailable(a.getVisitsInCycle(), policy.getLoyaltyThreshold(),
                        a.getReservedBookingId() != null))
                .orElse(false);
    }

    /** Reserves the reward for this booking if available. Returns true if the discount applies. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean tryReserve(UUID userId, UUID tenantId, UUID bookingId, BookingPolicy policy) {
        if (policy.getLoyaltyPercent() == 0) return false;
        LoyaltyAccount a = lock(userId, tenantId);
        if (!LoyaltyRules.discountAvailable(a.getVisitsInCycle(), policy.getLoyaltyThreshold(),
                a.getReservedBookingId() != null)) {
            return false;
        }
        a.reserve(bookingId, now());
        return true;
    }

    /** Gives a reservation back (only if this booking holds it). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(UUID userId, UUID tenantId, UUID bookingId) {
        LoyaltyAccount a = lock(userId, tenantId);
        if (bookingId.equals(a.getReservedBookingId())) a.release(now());
    }

    /** A completed booking: one visit, or — if it held the reward — consumes it and starts a new cycle. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean recordCompletedVisit(UUID userId, UUID tenantId, UUID bookingId, BookingPolicy policy) {
        LoyaltyAccount a = lock(userId, tenantId);
        boolean consumed = bookingId.equals(a.getReservedBookingId());
        a.recordVisit(consumed, policy.getLoyaltyThreshold(), now());
        return consumed;
    }

    /** Past completed guest bookings that were linked to a newly verified account. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void addLinkedVisits(UUID userId, UUID tenantId, int count) {
        if (count > 0) lock(userId, tenantId).addVisits(count, now());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void delete(UUID userId) {
        accounts.deleteById(userId);
    }

    private LoyaltyAccount lock(UUID userId, UUID tenantId) {
        accounts.ensureExists(userId, tenantId);
        return accounts.findForUpdate(userId).orElseThrow();
    }

    private static LoyaltyView view(LoyaltyAccount a, BookingPolicy policy) {
        boolean reserved = a.getReservedBookingId() != null;
        return new LoyaltyView(a.getVisitsInCycle(), policy.getLoyaltyThreshold(), policy.getLoyaltyPercent(),
                LoyaltyRules.discountAvailable(a.getVisitsInCycle(), policy.getLoyaltyThreshold(), reserved),
                reserved, a.getReservedBookingId(), a.getTotalVisits(), a.getRewardsUsed());
    }

    private Instant now() {
        return Instant.now(clock);
    }
}
