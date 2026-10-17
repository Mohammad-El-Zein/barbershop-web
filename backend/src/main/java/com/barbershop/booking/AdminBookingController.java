package com.barbershop.booking;

import com.barbershop.auth.CurrentUser;
import com.barbershop.booking.BookingDtos.AdminCancelRequest;
import com.barbershop.booking.BookingDtos.BookingAdminView;
import com.barbershop.booking.BookingDtos.ManualBookingRequest;
import com.barbershop.booking.BookingDtos.PageView;
import com.barbershop.booking.BookingDtos.ReasonRequest;
import com.barbershop.booking.BookingLifecycle.Scope;
import com.barbershop.common.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Everything per tenant: list/filter, manual bookings, every transition, refund override. */
@RestController
@RequestMapping("/api/admin/bookings")
@Tag(name = "Admin: bookings")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class AdminBookingController {

    private final BookingService bookings;
    private final BookingLifecycle lifecycle;

    public AdminBookingController(BookingService bookings, BookingLifecycle lifecycle) {
        this.bookings = bookings;
        this.lifecycle = lifecycle;
    }

    @GetMapping
    @Operation(summary = "List/filter bookings. Dates are local days in the shop timezone; "
            + "q searches name, email, phone; sort = startTime|createdAt|customerName|status, prefix '-' for desc")
    public PageView<BookingAdminView> list(
            CurrentUser admin,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "staffId", required = false) UUID staffId,
            @RequestParam(name = "status", required = false) BookingStatus status,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size,
            @RequestParam(name = "sort", defaultValue = "startTime") String sort) {
        return bookings.adminList(admin.tenantId(), from, to, staffId, status, q, page, size, sort);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Manual booking (walk-in / phone): CONFIRMED immediately, no deposit")
    public BookingAdminView createManual(CurrentUser admin, @Valid @RequestBody ManualBookingRequest req) {
        return bookings.createManual(admin.tenantId(), req);
    }

    @GetMapping("/{id}")
    public BookingAdminView get(CurrentUser admin, @PathVariable("id") UUID id) {
        return bookings.adminGet(admin.tenantId(), id);
    }

    @PostMapping("/{id}/accept")
    public BookingAdminView accept(CurrentUser admin, @PathVariable("id") UUID id) {
        lifecycle.accept(id, Actor.ADMIN, Scope.tenant(admin.tenantId()));
        return bookings.adminView(id, Actor.ADMIN);
    }

    @PostMapping("/{id}/decline")
    public BookingAdminView decline(CurrentUser admin, @PathVariable("id") UUID id,
                                    @Valid @RequestBody(required = false) ReasonRequest req) {
        lifecycle.decline(id, Actor.ADMIN, Scope.tenant(admin.tenantId()), req == null ? null : req.reason());
        return bookings.adminView(id, Actor.ADMIN);
    }

    @PostMapping("/{id}/complete")
    public BookingAdminView complete(CurrentUser admin, @PathVariable("id") UUID id) {
        lifecycle.complete(id, Actor.ADMIN, Scope.tenant(admin.tenantId()));
        return bookings.adminView(id, Actor.ADMIN);
    }

    @PostMapping("/{id}/no-show")
    public BookingAdminView noShow(CurrentUser admin, @PathVariable("id") UUID id) {
        lifecycle.noShow(id, Actor.ADMIN, Scope.tenant(admin.tenantId()));
        return bookings.adminView(id, Actor.ADMIN);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel by the shop. A paid deposit is refunded by default; refund=false keeps it")
    public BookingAdminView cancel(CurrentUser admin, @PathVariable("id") UUID id,
                                   @Valid @RequestBody(required = false) AdminCancelRequest req) {
        lifecycle.cancel(id, Actor.ADMIN, Scope.tenant(admin.tenantId()), req == null ? null : req.reason(),
                req == null ? null : req.refund());
        return bookings.adminView(id, Actor.ADMIN);
    }

    @PostMapping("/{id}/refund")
    @Operation(summary = "Refund override: refunds a PAID or FORFEITED deposit (simulated in demo mode)")
    public BookingAdminView refund(CurrentUser admin, @PathVariable("id") UUID id,
                                   @Valid @RequestBody(required = false) ReasonRequest req) {
        lifecycle.refundOverride(id, Scope.tenant(admin.tenantId()), req == null ? null : req.reason());
        return bookings.adminView(id, Actor.ADMIN);
    }
}
