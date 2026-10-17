package com.barbershop.staff;

import static com.barbershop.auth.AdminPrincipal.tenantId;

import com.barbershop.common.config.OpenApiConfig;
import com.barbershop.staff.StaffDtos.StaffAdminView;
import com.barbershop.staff.StaffDtos.StaffRequest;
import com.barbershop.staff.StaffDtos.StaffServicesRequest;
import com.barbershop.staff.StaffDtos.WorkingHoursEntry;
import com.barbershop.staff.StaffDtos.WorkingHoursRequest;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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

    public AdminStaffController(StaffDirectory directory) {
        this.directory = directory;
    }

    @GetMapping
    public List<StaffAdminView> list(@AuthenticationPrincipal Jwt jwt) {
        return directory.adminList(tenantId(jwt));
    }

    @GetMapping("/{id}")
    public StaffAdminView get(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        return directory.adminGet(tenantId(jwt), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StaffAdminView create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody StaffRequest req) {
        return directory.create(tenantId(jwt), req);
    }

    @PutMapping("/{id}")
    public StaffAdminView update(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id,
                                 @Valid @RequestBody StaffRequest req) {
        return directory.update(tenantId(jwt), id, req);
    }

    /** Deactivates the staff member (soft delete). */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        directory.deactivate(tenantId(jwt), id);
    }

    /** Replaces the services this staff member offers. */
    @PutMapping("/{id}/services")
    public StaffAdminView setServices(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id,
                                      @Valid @RequestBody StaffServicesRequest req) {
        return directory.setServices(tenantId(jwt), id, req.serviceIds());
    }

    @GetMapping("/{id}/working-hours")
    public List<WorkingHoursEntry> workingHours(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        return directory.workingHours(tenantId(jwt), id);
    }

    /** Replaces the full weekly schedule. */
    @PutMapping("/{id}/working-hours")
    public List<WorkingHoursEntry> replaceWorkingHours(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id,
                                                       @Valid @RequestBody WorkingHoursRequest req) {
        return directory.replaceWorkingHours(tenantId(jwt), id, req.entries());
    }
}
