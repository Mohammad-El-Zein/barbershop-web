package com.barbershop.booking;

import static com.barbershop.auth.AdminPrincipal.tenantId;

import com.barbershop.booking.BookingDtos.StatsView;
import com.barbershop.common.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/stats")
@Tag(name = "Admin: dashboard")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class AdminStatsController {

    private final StatsService stats;

    public AdminStatsController(StatsService stats) {
        this.stats = stats;
    }

    @GetMapping
    @Operation(summary = "Bookings per day and per service (default: last 30 days)")
    public StatsView stats(@AuthenticationPrincipal Jwt jwt,
                           @RequestParam(name = "from", required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(name = "to", required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return stats.stats(tenantId(jwt), from, to);
    }
}
