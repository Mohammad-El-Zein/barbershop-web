package com.barbershop.availability;

import com.barbershop.availability.AvailabilityService.AvailableDaysView;
import com.barbershop.availability.AvailabilityService.DayView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/{tenant}/availability")
@Tag(name = "Public: availability")
public class PublicAvailabilityController {

    private final AvailabilityService availability;

    public PublicAvailabilityController(AvailabilityService availability) {
        this.availability = availability;
    }

    @GetMapping
    @Operation(summary = "One day for ALL barbers at once: a column per barber with every start time of their "
            + "schedule (available=false = taken) plus the merged 'any barber' list (free only). "
            + "services = comma-separated service ids; duration = sum of all services.")
    public DayView day(@PathVariable("tenant") String tenant,
                       @RequestParam("services") List<UUID> serviceIds,
                       @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return availability.day(tenant, serviceIds, date);
    }

    @GetMapping("/days")
    @Operation(summary = "Dates in [from, to] with at least one free slot (max. 62 days); staff omitted = any barber")
    public AvailableDaysView days(@PathVariable("tenant") String tenant,
                                  @RequestParam("services") List<UUID> serviceIds,
                                  @RequestParam(name = "staff", required = false) UUID staffId,
                                  @RequestParam(name = "from", required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(name = "to", required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return availability.availableDays(tenant, serviceIds, staffId, from, to);
    }
}
