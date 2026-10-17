package com.barbershop.booking;

import com.barbershop.auth.CurrentUser;
import com.barbershop.booking.BookingDtos.BookingView;
import com.barbershop.booking.BookingDtos.PaymentLinkView;
import com.barbershop.booking.BookingDtos.ReasonRequest;
import com.barbershop.common.config.OpenApiConfig;
import com.barbershop.loyalty.LoyaltyService;
import com.barbershop.loyalty.LoyaltyService.LoyaltyView;
import com.barbershop.tenant.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@Tag(name = "Customer: my bookings")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class MyBookingsController {

    private final BookingService bookings;
    private final BookingLinkService links;
    private final LoyaltyService loyalty;
    private final TenantService tenants;

    public MyBookingsController(BookingService bookings, BookingLinkService links, LoyaltyService loyalty,
                               TenantService tenants) {
        this.bookings = bookings;
        this.links = links;
        this.loyalty = loyalty;
        this.tenants = tenants;
    }

    @GetMapping("/bookings")
    @Operation(summary = "My bookings: scope=upcoming (default, soonest first) or past (latest first)")
    public List<BookingView> list(CurrentUser user,
                                  @RequestParam(name = "scope", defaultValue = "upcoming") String scope) {
        return bookings.myBookings(user, !"past".equalsIgnoreCase(scope));
    }

    @GetMapping("/bookings/{id}")
    public BookingView get(CurrentUser user, @PathVariable("id") UUID id) {
        return bookings.myBooking(user, id);
    }

    @PostMapping("/bookings/{id}/cancel")
    @Operation(summary = "Cancel my booking (deposit refunded if ≥ refund cutoff before the appointment)")
    public BookingView cancel(CurrentUser user, @PathVariable("id") UUID id,
                              @Valid @RequestBody(required = false) ReasonRequest req) {
        return bookings.cancelMine(user, id, req == null ? null : req.reason());
    }

    @PostMapping("/bookings/{id}/payment-link")
    @Operation(summary = "\"Pay now\": fresh single-use link to the deposit payment page (only while AWAITING_PAYMENT)")
    public PaymentLinkView paymentLink(CurrentUser user, @PathVariable("id") UUID id) {
        return links.paymentLinkForOwner(user.tenantId(), user.userId(), id);
    }

    @GetMapping("/loyalty")
    @Operation(summary = "visits_in_current_cycle, threshold, next_discount_available …")
    public LoyaltyView loyalty(CurrentUser user) {
        return loyalty.view(user.userId(), tenants.require(user.tenantId()).policy());
    }
}
