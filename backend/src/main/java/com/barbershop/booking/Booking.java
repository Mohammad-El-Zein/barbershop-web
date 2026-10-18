package com.barbershop.booking;

import com.barbershop.booking.BookingPricing.Breakdown;
import com.barbershop.common.i18n.Lang;
import com.barbershop.service.BarberService;
import com.barbershop.staff.Staff;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Persistable;

/**
 * An appointment with one or more services. The database derives time_range
 * from [start_time, blocked_until) (generated column, not mapped) and enforces
 * that a barber never has two overlapping slot-blocking bookings
 * (constraint booking_no_overlap). Status changes only via {@link #transitionTo},
 * which delegates the rules to {@link BookingStateMachine}.
 */
@Entity
@Table(name = "booking")
public class Booking implements Persistable<UUID> {

    @Id
    private UUID id;

    /**
     * The id is assigned in the constructor, so Spring Data cannot tell a new booking
     * from a stored one and would {@code merge} it — returning a copy and silently
     * dropping later changes to this instance. With {@link Persistable} it is persisted.
     */
    @Transient
    private boolean isNew = true;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false, updatable = false)
    private Staff staff;

    /** The customer's account; null for guests (and after account deletion). */
    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private BookingSource source;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    /** end + buffer. */
    @Column(name = "blocked_until", nullable = false)
    private Instant blockedUntil;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "customer_phone")
    private String customerPhone;

    /** Language for all customer emails ("de"/"en"). */
    @Column(nullable = false)
    private String locale;

    @Column(name = "customer_note")
    private String customerNote;

    @Column(name = "consent_at")
    private Instant consentAt;

    @Column(nullable = false, updatable = false)
    private String currency;

    @Column(name = "price_total_cents", nullable = false)
    private int priceTotalCents;

    @Column(name = "loyalty_discount_cents", nullable = false)
    private int loyaltyDiscountCents;

    @Column(name = "deposit_cents", nullable = false)
    private int depositCents;

    @Column(name = "amount_due_at_shop_cents", nullable = false)
    private int amountDueAtShopCents;

    @Enumerated(EnumType.STRING)
    @Column(name = "loyalty_status", nullable = false)
    private LoyaltyStatus loyaltyStatus = LoyaltyStatus.NONE;

    /** Marketing first touch (only with the visitor's marketing consent); null for most bookings. */
    @Embedded
    private BookingAttribution attribution;

    @Column(name = "barber_deadline_at")
    private Instant barberDeadlineAt;

    @Column(name = "payment_deadline_at")
    private Instant paymentDeadlineAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "declined_at")
    private Instant declinedAt;

    @Column(name = "expired_at")
    private Instant expiredAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "no_show_at")
    private Instant noShowAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancelled_by")
    private Actor cancelledBy;

    /** Free text: decline reason, cancellation reason. */
    @Column(name = "status_reason")
    private String statusReason;

    @Column(name = "reminder_sent_at")
    private Instant reminderSentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<BookingItem> items = new ArrayList<>();

    protected Booking() {}

    public Booking(UUID tenantId, Staff staff, UUID userId, BookingSource source, Instant startTime,
                   Duration buffer, List<BarberService> services, String customerName, String customerEmail,
                   String customerPhone, Lang lang, String customerNote, Instant consentAt, String currency,
                   Instant now) {
        if (services.isEmpty()) throw new IllegalArgumentException("services");
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.staff = staff;
        this.userId = userId;
        this.source = source;
        this.status = BookingStateMachine.initialStatus(source);
        int minutes = 0;
        for (int i = 0; i < services.size(); i++) {
            items.add(new BookingItem(this, services.get(i), i));
            minutes += services.get(i).getDurationMin();
        }
        this.startTime = startTime;
        this.endTime = startTime.plus(Duration.ofMinutes(minutes));
        this.blockedUntil = endTime.plus(buffer);
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.customerPhone = customerPhone;
        this.locale = lang.code();
        this.customerNote = customerNote;
        this.consentAt = consentAt;
        this.currency = currency;
        this.createdAt = now;
        this.updatedAt = now;
        if (status == BookingStatus.CONFIRMED) this.confirmedAt = now;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markStored() {
        this.isNew = false;
    }

    // ── lifecycle ────────────────────────────────────────────────────────────

    /** The only way to change the status: validated by {@link BookingStateMachine}. */
    public void transitionTo(BookingStatus to, Actor actor, Instant now) {
        BookingStateMachine.check(status, to, actor);
        this.status = to;
        switch (to) {
            case AWAITING_PAYMENT -> acceptedAt = now;
            case CONFIRMED -> confirmedAt = now;
            case DECLINED -> declinedAt = now;
            case EXPIRED -> expiredAt = now;
            case CANCELLED -> {
                cancelledAt = now;
                cancelledBy = actor;
            }
            case COMPLETED -> completedAt = now;
            case NO_SHOW -> noShowAt = now;
            default -> { }
        }
        this.updatedAt = now;
    }

    public void applyPricing(Breakdown p) {
        this.priceTotalCents = p.totalCents();
        this.loyaltyDiscountCents = p.loyaltyDiscountCents();
        this.depositCents = p.depositCents();
        this.amountDueAtShopCents = p.amountDueAtShopCents();
    }

    public BookingAttribution getAttribution() { return attribution; }

    public void setAttribution(BookingAttribution attribution) {
        this.attribution = attribution;
    }

    public void setLoyaltyStatus(LoyaltyStatus loyaltyStatus) {
        this.loyaltyStatus = loyaltyStatus;
    }

    public void setBarberDeadlineAt(Instant barberDeadlineAt) {
        this.barberDeadlineAt = barberDeadlineAt;
    }

    public void setPaymentDeadlineAt(Instant paymentDeadlineAt) {
        this.paymentDeadlineAt = paymentDeadlineAt;
    }

    public void setStatusReason(String statusReason) {
        this.statusReason = statusReason;
    }

    public void markReminderSent(Instant now) {
        this.reminderSentAt = now;
    }

    /** GDPR: removes personal data, keeps the appointment and amounts for the shop's books. */
    public void anonymise(String placeholderName) {
        this.userId = null;
        this.customerName = placeholderName;
        this.customerEmail = null;
        this.customerPhone = null;
        this.customerNote = null;
    }

    public void linkToUser(UUID userId) {
        this.userId = userId;
    }

    // ── derived ──────────────────────────────────────────────────────────────

    public Lang lang() {
        Lang lang = Lang.parse(locale);
        return lang != null ? lang : Lang.DEFAULT;
    }

    public int durationMin() {
        return (int) Duration.between(startTime, endTime).toMinutes();
    }

    public String serviceNames(Lang lang) {
        return items.stream().map(i -> i.name(lang)).collect(Collectors.joining(" + "));
    }

    @Override
    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public Staff getStaff() { return staff; }
    public UUID getUserId() { return userId; }
    public BookingStatus getStatus() { return status; }
    public BookingSource getSource() { return source; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public Instant getBlockedUntil() { return blockedUntil; }
    public String getCustomerName() { return customerName; }
    public String getCustomerEmail() { return customerEmail; }
    public String getCustomerPhone() { return customerPhone; }
    public String getLocale() { return locale; }
    public String getCustomerNote() { return customerNote; }
    public Instant getConsentAt() { return consentAt; }
    public String getCurrency() { return currency; }
    public int getPriceTotalCents() { return priceTotalCents; }
    public int getLoyaltyDiscountCents() { return loyaltyDiscountCents; }
    public int getDepositCents() { return depositCents; }
    public int getAmountDueAtShopCents() { return amountDueAtShopCents; }
    public LoyaltyStatus getLoyaltyStatus() { return loyaltyStatus; }
    public Instant getBarberDeadlineAt() { return barberDeadlineAt; }
    public Instant getPaymentDeadlineAt() { return paymentDeadlineAt; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public Instant getDeclinedAt() { return declinedAt; }
    public Instant getExpiredAt() { return expiredAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getNoShowAt() { return noShowAt; }
    public Actor getCancelledBy() { return cancelledBy; }
    public String getStatusReason() { return statusReason; }
    public Instant getReminderSentAt() { return reminderSentAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<BookingItem> getItems() { return items; }
}
