package com.barbershop.booking;

import com.barbershop.auth.CurrentUser;
import com.barbershop.booking.BookingDtos.CampaignStatsView;
import com.barbershop.booking.BookingDtos.StatsView;
import com.barbershop.common.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
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
    public StatsView stats(CurrentUser admin,
                           @RequestParam(name = "from", required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(name = "to", required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return stats.stats(admin.tenantId(), from, to);
    }

    @GetMapping("/campaigns")
    @Operation(summary = "Online bookings and confirmed bookings per source / medium / campaign "
            + "(by booking creation date; default: last 30 days)")
    public CampaignStatsView campaigns(CurrentUser admin,
                                       @RequestParam(name = "from", required = false)
                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(name = "to", required = false)
                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return stats.campaigns(admin.tenantId(), from, to);
    }
}
