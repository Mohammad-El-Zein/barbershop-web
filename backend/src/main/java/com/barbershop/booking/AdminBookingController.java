package com.barbershop.booking;

import static com.barbershop.auth.AdminPrincipal.tenantId;

import com.barbershop.booking.BookingDtos.BookingAdminView;
import com.barbershop.booking.BookingDtos.PageView;
import com.barbershop.common.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/bookings")
@Tag(name = "Admin: bookings")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class AdminBookingController {

    private final BookingService bookings;

    public AdminBookingController(BookingService bookings) {
        this.bookings = bookings;
    }

    @GetMapping
    @Operation(summary = "List/filter bookings. Dates are local days in the shop timezone; "
            + "q searches name, email, phone; sort = startTime|createdAt|customerName|status, prefix '-' for desc")
    public PageView<BookingAdminView> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "staffId", required = false) UUID staffId,
            @RequestParam(name = "status", required = false) BookingStatus status,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size,
            @RequestParam(name = "sort", defaultValue = "startTime") String sort) {
        return bookings.adminList(tenantId(jwt), from, to, staffId, status, q, page, size, sort);
    }

    @GetMapping("/{id}")
    public BookingAdminView get(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        return bookings.adminGet(tenantId(jwt), id);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel (no cutoff for admins); the customer is notified by email")
    public BookingAdminView cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        return bookings.adminCancel(tenantId(jwt), id);
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Confirm a PENDING booking (only used when auto-confirm is off)")
    public BookingAdminView confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        return bookings.adminConfirm(tenantId(jwt), id);
    }
}
