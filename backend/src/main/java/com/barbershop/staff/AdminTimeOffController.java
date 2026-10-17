package com.barbershop.staff;

import com.barbershop.auth.CurrentUser;
import com.barbershop.common.config.OpenApiConfig;
import com.barbershop.staff.StaffDtos.TimeOffRequest;
import com.barbershop.staff.StaffDtos.TimeOffView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Time off / blocked slots. staffId = null closes the whole shop. */
@RestController
@RequestMapping("/api/admin/time-off")
@Tag(name = "Admin: time off")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class AdminTimeOffController {

    private final StaffDirectory directory;
    private final Clock clock;

    public AdminTimeOffController(StaffDirectory directory, Clock clock) {
        this.directory = directory;
        this.clock = clock;
    }

    @GetMapping
    @Operation(summary = "Time off overlapping [from, to) — default: now … +90 days")
    public List<TimeOffView> list(
            CurrentUser admin,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        Instant f = from != null ? from.toInstant() : Instant.now(clock).truncatedTo(ChronoUnit.DAYS);
        Instant t = to != null ? to.toInstant() : f.plus(90, ChronoUnit.DAYS);
        return directory.timeOff(admin.tenantId(), f, t);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TimeOffView create(CurrentUser admin, @Valid @RequestBody TimeOffRequest req) {
        return directory.createTimeOff(admin.tenantId(), req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(CurrentUser admin, @PathVariable("id") UUID id) {
        directory.deleteTimeOff(admin.tenantId(), id);
    }
}
