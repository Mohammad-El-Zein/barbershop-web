package com.barbershop.availability;

import com.barbershop.availability.AvailabilityService.AvailabilityView;
import com.barbershop.availability.AvailabilityService.AvailableDaysView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
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
    @Operation(summary = "Free slots for a service on a date; staff optional (omit = any barber)")
    public AvailabilityView slots(@PathVariable("tenant") String tenant,
                                  @RequestParam("service") UUID serviceId,
                                  @RequestParam(name = "staff", required = false) UUID staffId,
                                  @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return availability.availability(tenant, serviceId, staffId, date);
    }

    @GetMapping("/days")
    @Operation(summary = "Dates in [from, to] with at least one free slot (max. 62 days)")
    public AvailableDaysView days(@PathVariable("tenant") String tenant,
                                  @RequestParam("service") UUID serviceId,
                                  @RequestParam(name = "staff", required = false) UUID staffId,
                                  @RequestParam(name = "from", required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(name = "to", required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return availability.availableDays(tenant, serviceId, staffId, from, to);
    }
}
