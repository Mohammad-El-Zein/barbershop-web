package com.barbershop.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Duration;

/**
 * Per-tenant booking rules (columns on {@code tenant}, defaults in the V2
 * migration). Durations are stored in minutes; the getters return {@link Duration}.
 */
@Embeddable
public class BookingPolicy {

    /** How long the barber has to accept/decline a request (default 12 h). */
    @Column(name = "barber_response_min", nullable = false)
    private int barberResponseMin = 720;

    /** How long the customer has to pay the deposit after acceptance (default 24 h). */
    @Column(name = "payment_deadline_min", nullable = false)
    private int paymentDeadlineMin = 1440;

    /** Both deadlines end at the latest this long before the appointment (default 2 h). */
    @Column(name = "deadline_before_start_min", nullable = false)
    private int deadlineBeforeStartMin = 120;

    @Column(name = "deposit_cents", nullable = false)
    private int depositCents = 1000;

    /** Customer cancels at least this long before → deposit refunded (default 24 h). */
    @Column(name = "refund_cutoff_min", nullable = false)
    private int refundCutoffMin = 1440;

    /** Customers can cancel online until this long before the start (default 2 h); then: contact the shop. */
    @Column(name = "online_cancel_cutoff_min", nullable = false)
    private int onlineCancelCutoffMin = 120;

    @Column(name = "loyalty_threshold", nullable = false)
    private int loyaltyThreshold = 10;

    @Column(name = "loyalty_percent", nullable = false)
    private int loyaltyPercent = 50;

    @Column(name = "slot_step_min", nullable = false)
    private int slotStepMin = 15;

    /** Cleanup time after each appointment, blocked for the barber. */
    @Column(name = "buffer_min", nullable = false)
    private int bufferMin = 0;

    /** Earliest online booking = now + lead time (default 4 h). */
    @Column(name = "min_lead_min", nullable = false)
    private int minLeadMin = 240;

    @Column(name = "max_days_ahead", nullable = false)
    private int maxDaysAhead = 60;

    /** CONFIRMED bookings become COMPLETED this long after their end (unless marked NO_SHOW). */
    @Column(name = "auto_complete_after_min", nullable = false)
    private int autoCompleteAfterMin = 120;

    @Column(name = "reminder_before_min", nullable = false)
    private int reminderBeforeMin = 1440;

    public Duration barberResponse() { return Duration.ofMinutes(barberResponseMin); }
    public Duration paymentDeadline() { return Duration.ofMinutes(paymentDeadlineMin); }
    public Duration deadlineBeforeStart() { return Duration.ofMinutes(deadlineBeforeStartMin); }
    public Duration refundCutoff() { return Duration.ofMinutes(refundCutoffMin); }
    public Duration onlineCancelCutoff() { return Duration.ofMinutes(onlineCancelCutoffMin); }
    public Duration slotStep() { return Duration.ofMinutes(slotStepMin); }
    public Duration buffer() { return Duration.ofMinutes(bufferMin); }
    public Duration minLead() { return Duration.ofMinutes(minLeadMin); }
    public Duration autoCompleteAfter() { return Duration.ofMinutes(autoCompleteAfterMin); }
    public Duration reminderBefore() { return Duration.ofMinutes(reminderBeforeMin); }

    public int getBarberResponseMin() { return barberResponseMin; }
    public void setBarberResponseMin(int v) { this.barberResponseMin = v; }
    public int getPaymentDeadlineMin() { return paymentDeadlineMin; }
    public void setPaymentDeadlineMin(int v) { this.paymentDeadlineMin = v; }
    public int getDeadlineBeforeStartMin() { return deadlineBeforeStartMin; }
    public void setDeadlineBeforeStartMin(int v) { this.deadlineBeforeStartMin = v; }
    public int getDepositCents() { return depositCents; }
    public void setDepositCents(int v) { this.depositCents = v; }
    public int getRefundCutoffMin() { return refundCutoffMin; }
    public void setRefundCutoffMin(int v) { this.refundCutoffMin = v; }
    public int getOnlineCancelCutoffMin() { return onlineCancelCutoffMin; }
    public void setOnlineCancelCutoffMin(int v) { this.onlineCancelCutoffMin = v; }
    public int getLoyaltyThreshold() { return loyaltyThreshold; }
    public void setLoyaltyThreshold(int v) { this.loyaltyThreshold = v; }
    public int getLoyaltyPercent() { return loyaltyPercent; }
    public void setLoyaltyPercent(int v) { this.loyaltyPercent = v; }
    public int getSlotStepMin() { return slotStepMin; }
    public void setSlotStepMin(int v) { this.slotStepMin = v; }
    public int getBufferMin() { return bufferMin; }
    public void setBufferMin(int v) { this.bufferMin = v; }
    public int getMinLeadMin() { return minLeadMin; }
    public void setMinLeadMin(int v) { this.minLeadMin = v; }
    public int getMaxDaysAhead() { return maxDaysAhead; }
    public void setMaxDaysAhead(int v) { this.maxDaysAhead = v; }
    public int getAutoCompleteAfterMin() { return autoCompleteAfterMin; }
    public void setAutoCompleteAfterMin(int v) { this.autoCompleteAfterMin = v; }
    public int getReminderBeforeMin() { return reminderBeforeMin; }
    public void setReminderBeforeMin(int v) { this.reminderBeforeMin = v; }
}
