package com.barbershop.booking;

import com.barbershop.auth.CurrentUser;
import com.barbershop.booking.BookingDtos.BookingAdminView;
import com.barbershop.booking.BookingDtos.ReasonRequest;
import com.barbershop.booking.BookingLifecycle.Scope;
import com.barbershop.common.config.OpenApiConfig;
import com.barbershop.common.error.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** A barber's own area: only bookings of the staff profile linked to the account. */
@RestController
@RequestMapping("/api/barber")
@Tag(name = "Barber")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class BarberController {

    private final BookingService bookings;
    private final BookingLifecycle lifecycle;

    public BarberController(BookingService bookings, BookingLifecycle lifecycle) {
        this.bookings = bookings;
        this.lifecycle = lifecycle;
    }

    @GetMapping("/calendar")
    @Operation(summary = "My calendar: view=day (default) or week (Mon–Sun containing date)")
    public List<BookingAdminView> calendar(CurrentUser me,
                                           @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                           LocalDate date,
                                           @RequestParam(name = "view", defaultValue = "day") String view) {
        return switch (view) {
            case "day" -> bookings.calendar(me, date, date);
            case "week" -> {
                LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield bookings.calendar(me, monday, monday.plusDays(6));
            }
            default -> throw ApiException.badRequest("request.invalid_parameter", "view");
        };
    }

    @GetMapping("/requests")
    @Operation(summary = "My pending requests (PENDING_BARBER), soonest first")
    public List<BookingAdminView> requests(CurrentUser me) {
        return bookings.pendingRequests(me);
    }

    @GetMapping("/bookings/{id}")
    public BookingAdminView get(CurrentUser me, @PathVariable("id") UUID id) {
        return bookings.barberGet(me, id);
    }

    @PostMapping("/bookings/{id}/accept")
    public BookingAdminView accept(CurrentUser me, @PathVariable("id") UUID id) {
        lifecycle.accept(id, Actor.BARBER, scope(me));
        return bookings.adminView(id, Actor.BARBER);
    }

    @PostMapping("/bookings/{id}/decline")
    public BookingAdminView decline(CurrentUser me, @PathVariable("id") UUID id,
                                    @Valid @RequestBody(required = false) ReasonRequest req) {
        lifecycle.decline(id, Actor.BARBER, scope(me), req == null ? null : req.reason());
        return bookings.adminView(id, Actor.BARBER);
    }

    @PostMapping("/bookings/{id}/complete")
    public BookingAdminView complete(CurrentUser me, @PathVariable("id") UUID id) {
        lifecycle.complete(id, Actor.BARBER, scope(me));
        return bookings.adminView(id, Actor.BARBER);
    }

    @PostMapping("/bookings/{id}/no-show")
    public BookingAdminView noShow(CurrentUser me, @PathVariable("id") UUID id) {
        lifecycle.noShow(id, Actor.BARBER, scope(me));
        return bookings.adminView(id, Actor.BARBER);
    }

    @PostMapping("/bookings/{id}/cancel")
    @Operation(summary = "Cancel by the shop: a paid deposit is always refunded")
    public BookingAdminView cancel(CurrentUser me, @PathVariable("id") UUID id,
                                   @Valid @RequestBody(required = false) ReasonRequest req) {
        lifecycle.cancel(id, Actor.BARBER, scope(me), req == null ? null : req.reason(), null);
        return bookings.adminView(id, Actor.BARBER);
    }

    private static Scope scope(CurrentUser me) {
        return Scope.staff(me.tenantId(), me.requireStaffId());
    }
}
