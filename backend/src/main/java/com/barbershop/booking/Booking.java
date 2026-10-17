package com.barbershop.booking;

import com.barbershop.common.i18n.Lang;
import com.barbershop.service.BarberService;
import com.barbershop.staff.Staff;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A customer's appointment. The database derives time_range from start/end
 * (generated column, not mapped here) and enforces that one staff member never
 * has two overlapping active bookings (constraint booking_no_overlap).
 */
@Entity
@Table(name = "booking")
public class Booking {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false, updatable = false)
    private BarberService service;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false, updatable = false)
    private Staff staff;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "customer_phone", nullable = false)
    private String customerPhone;

    @Column(name = "customer_email", nullable = false)
    private String customerEmail;

    /** Language the customer booked in ("de"/"en"); emails use it. */
    @Column(nullable = false)
    private String locale;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    /** SHA-256 (hex) of the cancel token; the raw token is never stored. */
    @Column(name = "cancel_token_hash", nullable = false, unique = true, updatable = false)
    private String cancelTokenHash;

    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    protected Booking() {}

    public Booking(UUID tenantId, BarberService service, Staff staff, Instant startTime, Instant endTime,
                   String customerName, String customerPhone, String customerEmail, Lang lang,
                   BookingStatus status, String cancelTokenHash, String notes, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.service = service;
        this.staff = staff;
        this.startTime = startTime;
        this.endTime = endTime;
        this.customerName = customerName;
        this.customerPhone = customerPhone;
        this.customerEmail = customerEmail;
        this.locale = lang.code();
        this.status = status;
        this.cancelTokenHash = cancelTokenHash;
        this.notes = notes;
        this.createdAt = createdAt;
    }

    public void cancel(Instant now) {
        this.status = BookingStatus.CANCELLED;
        this.cancelledAt = now;
    }

    public void confirm() {
        this.status = BookingStatus.CONFIRMED;
    }

    public Lang lang() {
        Lang lang = Lang.parse(locale);
        return lang != null ? lang : Lang.DEFAULT;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public BarberService getService() { return service; }
    public Staff getStaff() { return staff; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public String getCustomerName() { return customerName; }
    public String getCustomerPhone() { return customerPhone; }
    public String getCustomerEmail() { return customerEmail; }
    public String getLocale() { return locale; }
    public BookingStatus getStatus() { return status; }
    public String getCancelTokenHash() { return cancelTokenHash; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCancelledAt() { return cancelledAt; }
}
