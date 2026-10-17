package com.barbershop.booking;

import com.barbershop.auth.CurrentUser;
import com.barbershop.booking.BookingDtos.BookingView;
import com.barbershop.booking.BookingDtos.CreateBookingRequest;
import com.barbershop.booking.BookingDtos.DecisionView;
import com.barbershop.booking.BookingDtos.PaymentLinkView;
import com.barbershop.booking.BookingDtos.PricePreview;
import com.barbershop.booking.BookingDtos.PricePreviewRequest;
import com.barbershop.booking.BookingDtos.ReasonRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Locale;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public booking endpoints. Logged-in customers send their access token (optional):
 * then name/email/phone default to the account and loyalty applies.
 * The email-link endpoints follow "GET shows, POST acts" — see {@link BookingLinkService}.
 */
@RestController
@RequestMapping("/api/public")
@Tag(name = "Public: bookings")
public class PublicBookingController {

    private final BookingService bookings;
    private final BookingLinkService links;

    public PublicBookingController(BookingService bookings, BookingLinkService links) {
        this.bookings = bookings;
        this.links = links;
    }

    @PostMapping("/{tenant}/bookings/price-preview")
    @Operation(summary = "Price before booking: total, loyalty discount (logged-in customers), deposit, due at shop")
    public PricePreview pricePreview(@PathVariable("tenant") String tenant, @Valid @RequestBody PricePreviewRequest req,
                                     CurrentUser user) {
        return bookings.pricePreview(tenant, req.serviceIds(), user);
    }

    @PostMapping("/{tenant}/bookings")
    @Operation(summary = "Request a booking (guest or logged-in) → 201, status PENDING_BARBER. "
            + "409 if the slot was just taken, 422 if it is not a valid slot. The manage link is sent by email only.")
    public ResponseEntity<BookingView> create(@PathVariable("tenant") String tenant,
                                              @Valid @RequestBody CreateBookingRequest req, CurrentUser user,
                                              Locale locale) {
        BookingView created = bookings.create(tenant, req, user, locale);
        return ResponseEntity.created(URI.create("/api/me/bookings/" + created.id())).body(created);
    }

    // ── status page right after booking (/[locale]/booking/status/[token]) ──

    @GetMapping("/booking-status/{token}")
    @Operation(summary = "Live status for the status page (statusToken from the creation response; changes nothing)")
    public BookingView status(@PathVariable("token") String token) {
        return links.statusPage(token);
    }

    @PostMapping("/booking-status/{token}/payment-link")
    @Operation(summary = "Fresh single-use link to the deposit payment page (only while AWAITING_PAYMENT)")
    public PaymentLinkView statusPaymentLink(@PathVariable("token") String token) {
        return links.paymentLinkFromStatus(token);
    }

    // ── customer manage link (/[locale]/booking/manage/[token]) ─────────────

    @GetMapping("/manage/{token}")
    @Operation(summary = "Booking behind a manage link (changes nothing). 410 token.used / token.expired")
    public BookingView manage(@PathVariable("token") String token) {
        return links.managePage(token);
    }

    @PostMapping("/manage/{token}/cancel")
    @Operation(summary = "Cancel via the manage link (single use). Deposit refunded if ≥ refund cutoff before")
    public BookingView cancel(@PathVariable("token") String token,
                              @Valid @RequestBody(required = false) ReasonRequest req) {
        return links.cancelViaManageLink(token, req == null ? null : req.reason());
    }

    // ── barber decision link (/[locale]/barber/requests/[token]) ────────────

    @GetMapping("/barber-decision/{token}")
    @Operation(summary = "Request behind the barber's accept/decline link (changes nothing)")
    public DecisionView decision(@PathVariable("token") String token) {
        return links.decisionPage(token);
    }

    @PostMapping("/barber-decision/{token}/accept")
    @Operation(summary = "Accept → AWAITING_PAYMENT, customer gets the payment link (single use)")
    public DecisionView accept(@PathVariable("token") String token) {
        return links.accept(token);
    }

    @PostMapping("/barber-decision/{token}/decline")
    @Operation(summary = "Decline → DECLINED, customer is asked to pick another time (single use)")
    public DecisionView decline(@PathVariable("token") String token,
                                @Valid @RequestBody(required = false) ReasonRequest req) {
        return links.decline(token, req == null ? null : req.reason());
    }
}
