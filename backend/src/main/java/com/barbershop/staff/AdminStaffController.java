package com.barbershop.staff;

import com.barbershop.auth.CurrentUser;
import com.barbershop.common.config.OpenApiConfig;
import com.barbershop.staff.StaffDtos.StaffAdminView;
import com.barbershop.staff.StaffDtos.StaffRequest;
import com.barbershop.staff.StaffDtos.StaffServicesRequest;
import com.barbershop.staff.StaffDtos.WorkingHoursEntry;
import com.barbershop.staff.StaffDtos.WorkingHoursRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/staff")
@Tag(name = "Admin: staff")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class AdminStaffController {

    private final StaffDirectory directory;
    private final StaffRemoval removal;

    public AdminStaffController(StaffDirectory directory, StaffRemoval removal) {
        this.directory = directory;
        this.removal = removal;
    }

    @GetMapping
    public List<StaffAdminView> list(CurrentUser admin) {
        return directory.adminList(admin.tenantId());
    }

    @GetMapping("/{id}")
    public StaffAdminView get(CurrentUser admin, @PathVariable("id") UUID id) {
        return directory.adminGet(admin.tenantId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StaffAdminView create(CurrentUser admin, @Valid @RequestBody StaffRequest req) {
        return directory.create(admin.tenantId(), req);
    }

    @PutMapping("/{id}")
    public StaffAdminView update(CurrentUser admin, @PathVariable("id") UUID id,
                                 @Valid @RequestBody StaffRequest req) {
        return directory.update(admin.tenantId(), id, req);
    }

    /** Deactivates the staff member (soft delete). */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(CurrentUser admin, @PathVariable("id") UUID id) {
        directory.deactivate(admin.tenantId(), id);
    }

    @DeleteMapping("/{id}/permanently")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deletes a staff member for good, with services, working hours, time off and login — only "
            + "without any booking (also past or cancelled ones); otherwise 409 staff.has_bookings: deactivate instead.")
    public void deletePermanently(CurrentUser admin, @PathVariable("id") UUID id) {
        removal.delete(admin.tenantId(), id);
    }

    /** Replaces the services this staff member offers. */
    @PutMapping("/{id}/services")
    public StaffAdminView setServices(CurrentUser admin, @PathVariable("id") UUID id,
                                      @Valid @RequestBody StaffServicesRequest req) {
        return directory.setServices(admin.tenantId(), id, req.serviceIds());
    }

    @GetMapping("/{id}/working-hours")
    public List<WorkingHoursEntry> workingHours(CurrentUser admin, @PathVariable("id") UUID id) {
        return directory.workingHours(admin.tenantId(), id);
    }

    /** Replaces the full weekly schedule. */
    @PutMapping("/{id}/working-hours")
    public List<WorkingHoursEntry> replaceWorkingHours(CurrentUser admin, @PathVariable("id") UUID id,
                                                       @Valid @RequestBody WorkingHoursRequest req) {
        return directory.replaceWorkingHours(admin.tenantId(), id, req.entries());
    }
}
