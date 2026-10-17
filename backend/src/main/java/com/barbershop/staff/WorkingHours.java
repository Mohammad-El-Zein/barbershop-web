package com.barbershop.staff;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.UUID;

/**
 * One working-time range of a staff member on a weekday (ISO 1 = Monday … 7 = Sunday),
 * in the tenant's local time. Several ranges per day model breaks.
 */
@Entity
@Table(name = "working_hours")
public class WorkingHours {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "staff_id", nullable = false, updatable = false)
    private UUID staffId;

    @Column(nullable = false)
    private int weekday;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    protected WorkingHours() {}

    public WorkingHours(UUID tenantId, UUID staffId, int weekday, LocalTime startTime, LocalTime endTime) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.staffId = staffId;
        this.weekday = weekday;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getStaffId() { return staffId; }
    public int getWeekday() { return weekday; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
}
