package com.barbershop.staff;

import com.barbershop.common.error.ApiException;
import com.barbershop.common.i18n.Lang;
import com.barbershop.common.i18n.Messages;
import com.barbershop.service.BarberServiceRepository;
import com.barbershop.staff.StaffDtos.StaffAdminView;
import com.barbershop.staff.StaffDtos.StaffRequest;
import com.barbershop.staff.StaffDtos.StaffView;
import com.barbershop.staff.StaffDtos.TimeOffRequest;
import com.barbershop.staff.StaffDtos.TimeOffView;
import com.barbershop.staff.StaffDtos.WorkingHoursEntry;
import com.barbershop.tenant.Tenant;
import com.barbershop.tenant.TenantService;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Staff, their services, working hours and time off. */
@Service
@Transactional(readOnly = true)
public class StaffDirectory {

    private final StaffRepository staff;
    private final WorkingHoursRepository workingHours;
    private final TimeOffRepository timeOff;
    private final BarberServiceRepository services;
    private final TenantService tenants;

    public StaffDirectory(StaffRepository staff, WorkingHoursRepository workingHours, TimeOffRepository timeOff,
                          BarberServiceRepository services, TenantService tenants) {
        this.staff = staff;
        this.workingHours = workingHours;
        this.timeOff = timeOff;
        this.services = services;
        this.tenants = tenants;
    }

    // ── public ───────────────────────────────────────────────────────────────

    /** Active staff, optionally only those offering a service. */
    public List<StaffView> listActive(String tenantSlug, UUID serviceId) {
        Tenant tenant = tenants.requireBySlug(tenantSlug);
        Lang lang = Messages.currentLang();
        Map<UUID, List<UUID>> assignments = assignments(tenant.getId());
        return staff.findByTenantIdAndActiveTrueOrderBySortOrderAscNameAsc(tenant.getId()).stream()
                .filter(s -> serviceId == null || assignments.getOrDefault(s.getId(), List.of()).contains(serviceId))
                .map(s -> new StaffView(s.getId(), s.getName(), s.getPhotoUrl(), s.bio(lang),
                        assignments.getOrDefault(s.getId(), List.of())))
                .toList();
    }

    /** Active staff of the tenant who offer the given service (used by availability and booking). */
    public List<Staff> activeOffering(UUID tenantId, UUID serviceId) {
        Set<UUID> ids = new HashSet<>(staff.findStaffIdsOffering(serviceId));
        return staff.findByTenantIdAndActiveTrueOrderBySortOrderAscNameAsc(tenantId).stream()
                .filter(s -> ids.contains(s.getId()))
                .toList();
    }

    // ── admin: staff ─────────────────────────────────────────────────────────

    public List<StaffAdminView> adminList(UUID tenantId) {
        Map<UUID, List<UUID>> assignments = assignments(tenantId);
        return staff.findByTenantIdOrderBySortOrderAscNameAsc(tenantId).stream()
                .map(s -> adminView(s, assignments.getOrDefault(s.getId(), List.of())))
                .toList();
    }

    public StaffAdminView adminGet(UUID tenantId, UUID id) {
        Staff s = require(tenantId, id);
        return adminView(s, staff.findServiceIds(s.getId()));
    }

    @Transactional
    public StaffAdminView create(UUID tenantId, StaffRequest req) {
        Staff s = new Staff(tenantId);
        apply(s, req);
        staff.save(s);
        return adminView(s, List.of());
    }

    @Transactional
    public StaffAdminView update(UUID tenantId, UUID id, StaffRequest req) {
        Staff s = require(tenantId, id);
        apply(s, req);
        return adminView(s, staff.findServiceIds(s.getId()));
    }

    /** Soft delete: past bookings keep referencing the staff member. */
    @Transactional
    public void deactivate(UUID tenantId, UUID id) {
        require(tenantId, id).setActive(false);
    }

    @Transactional
    public StaffAdminView setServices(UUID tenantId, UUID id, Set<UUID> serviceIds) {
        Staff s = require(tenantId, id);
        for (UUID serviceId : serviceIds) {
            services.findByIdAndTenantId(serviceId, tenantId)
                    .orElseThrow(() -> ApiException.unprocessable("service.not_found"));
        }
        staff.deleteAssignments(s.getId());
        serviceIds.forEach(serviceId -> staff.insertAssignment(tenantId, s.getId(), serviceId));
        return adminView(s, new ArrayList<>(serviceIds));
    }

    // ── admin: working hours ─────────────────────────────────────────────────

    public List<WorkingHoursEntry> workingHours(UUID tenantId, UUID staffId) {
        require(tenantId, staffId);
        return workingHours.findByStaffIdOrderByWeekdayAscStartTimeAsc(staffId).stream()
                .map(w -> new WorkingHoursEntry(w.getWeekday(), w.getStartTime(), w.getEndTime()))
                .toList();
    }

    /** Replaces the whole week. Ranges must end after they start and must not overlap within a day. */
    @Transactional
    public List<WorkingHoursEntry> replaceWorkingHours(UUID tenantId, UUID staffId, List<WorkingHoursEntry> entries) {
        require(tenantId, staffId);
        List<WorkingHoursEntry> sorted = entries.stream()
                .sorted(Comparator.comparing(WorkingHoursEntry::weekday).thenComparing(WorkingHoursEntry::start))
                .toList();
        WorkingHoursEntry previous = null;
        for (WorkingHoursEntry e : sorted) {
            if (!e.end().isAfter(e.start())) throw ApiException.unprocessable("working_hours.invalid_range");
            if (previous != null && previous.weekday().equals(e.weekday()) && e.start().isBefore(previous.end())) {
                throw ApiException.unprocessable("working_hours.overlap");
            }
            previous = e;
        }
        workingHours.deleteByStaffId(staffId);
        workingHours.flush();
        sorted.forEach(e -> workingHours.save(new WorkingHours(tenantId, staffId, e.weekday(), e.start(), e.end())));
        return sorted;
    }

    // ── admin: time off ──────────────────────────────────────────────────────

    public List<TimeOffView> timeOff(UUID tenantId, Instant from, Instant to) {
        ZoneId zone = tenants.require(tenantId).zone();
        Map<UUID, String> names = names(tenantId);
        return timeOff.findForTenant(tenantId, from, to).stream().map(t -> view(t, zone, names)).toList();
    }

    @Transactional
    public TimeOffView createTimeOff(UUID tenantId, TimeOffRequest req) {
        if (!req.end().isAfter(req.start())) throw ApiException.unprocessable("time_off.invalid_range");
        if (req.staffId() != null) require(tenantId, req.staffId());
        TimeOff t = timeOff.save(new TimeOff(tenantId, req.staffId(), req.start().toInstant(), req.end().toInstant(),
                req.reason()));
        return view(t, tenants.require(tenantId).zone(), names(tenantId));
    }

    @Transactional
    public void deleteTimeOff(UUID tenantId, UUID id) {
        TimeOff t = timeOff.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> ApiException.notFound("time_off.not_found"));
        timeOff.delete(t);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    public Staff require(UUID tenantId, UUID id) {
        return staff.findByIdAndTenantId(id, tenantId).orElseThrow(() -> ApiException.notFound("staff.not_found"));
    }

    private Map<UUID, List<UUID>> assignments(UUID tenantId) {
        Map<UUID, List<UUID>> map = new HashMap<>();
        for (Object[] row : staff.findAssignments(tenantId)) {
            map.computeIfAbsent((UUID) row[0], k -> new ArrayList<>()).add((UUID) row[1]);
        }
        return map;
    }

    private Map<UUID, String> names(UUID tenantId) {
        Map<UUID, String> names = new HashMap<>();
        staff.findByTenantIdOrderBySortOrderAscNameAsc(tenantId).forEach(s -> names.put(s.getId(), s.getName()));
        return names;
    }

    private static TimeOffView view(TimeOff t, ZoneId zone, Map<UUID, String> names) {
        return new TimeOffView(t.getId(), t.getStaffId(), t.getStaffId() == null ? null : names.get(t.getStaffId()),
                t.getStartAt().atZone(zone).toOffsetDateTime(), t.getEndAt().atZone(zone).toOffsetDateTime(),
                t.getReason());
    }

    private static StaffAdminView adminView(Staff s, List<UUID> serviceIds) {
        return new StaffAdminView(s.getId(), s.getName(), s.getPhotoUrl(), s.getBioDe(), s.getBioEn(), s.isActive(),
                s.getSortOrder(), serviceIds);
    }

    private static void apply(Staff s, StaffRequest req) {
        s.setName(req.name().trim());
        s.setPhotoUrl(req.photoUrl());
        s.setBioDe(req.bioDe());
        s.setBioEn(req.bioEn());
        if (req.active() != null) s.setActive(req.active());
        if (req.sortOrder() != null) s.setSortOrder(req.sortOrder());
    }
}
