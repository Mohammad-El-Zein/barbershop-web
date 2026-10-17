package com.barbershop.staff;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * An absence or blocked slot. {@code staffId == null} means the whole shop is
 * closed (e.g. a public holiday). Start/end are instants (UTC).
 */
@Entity
@Table(name = "time_off")
public class TimeOff {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "staff_id")
    private UUID staffId;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TimeOff() {}

    public TimeOff(UUID tenantId, UUID staffId, Instant startAt, Instant endAt, String reason) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.staffId = staffId;
        this.startAt = startAt;
        this.endAt = endAt;
        this.reason = reason;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getStaffId() { return staffId; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public String getReason() { return reason; }
}
