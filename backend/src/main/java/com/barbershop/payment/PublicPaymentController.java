package com.barbershop.payment;

import com.barbershop.booking.BookingDtos.BookingView;
import com.barbershop.booking.BookingDtos.PaymentPageView;
import com.barbershop.booking.BookingDtos.PaymentResult;
import com.barbershop.booking.BookingService;
import com.barbershop.booking.BookingViews;
import com.barbershop.common.i18n.Messages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The deposit payment page /[locale]/pay/[token]. In demo mode it offers
 * "Simulate payment" and "Cancel" — there are no card fields anywhere.
 */
@RestController
@RequestMapping("/api/public/payments")
@Tag(name = "Public: payments (demo)")
public class PublicPaymentController {

    private final PaymentService payments;
    private final BookingService bookings;
    private final BookingViews views;

    public PublicPaymentController(PaymentService payments, BookingService bookings, BookingViews views) {
        this.payments = payments;
        this.bookings = bookings;
        this.views = views;
    }

    @GetMapping("/{token}")
    @Operation(summary = "Payment page data: amount, booking summary, demoMode flag (changes nothing). "
            + "410 token.used (with paymentStatus) / token.expired")
    public PaymentPageView page(@PathVariable("token") String token) {
        PaymentService.PageData data = payments.page(token);
        Payment p = data.payment();
        BookingView booking = bookings.customerView(p.getBookingId());
        List<String> actions = payments.isDemo() ? List.of("SIMULATE_PAYMENT", "CANCEL") : List.of("PAY", "CANCEL");
        return new PaymentPageView(payments.isDemo(), p.getStatus(),
                views.money(p.getAmountCents(), p.getCurrency(), Messages.currentLang()),
                booking.payment() == null ? null : booking.payment().deadline(), booking, actions);
    }

    @PostMapping("/{token}/simulate")
    @Operation(summary = "DEMO ONLY: simulate a successful payment → CONFIRMED. Goes through the same "
            + "PaymentService.handlePaymentSucceeded a real webhook uses. Idempotent.")
    public PaymentResult simulate(@PathVariable("token") String token) {
        PaymentService.Result r = payments.simulateSuccess(token);
        return new PaymentResult(r.payment().getStatus(), r.booking().getStatus(),
                r.outcome() == PaymentService.Outcome.ALREADY_PROCESSED, payments.isDemo());
    }

    @PostMapping("/{token}/cancel")
    @Operation(summary = "Customer cancels the booking from the payment page (nothing was paid)")
    public BookingView cancel(@PathVariable("token") String token) {
        return bookings.customerView(payments.cancelFromPaymentPage(token).booking().getId());
    }

    @PostMapping("/webhook/{provider}")
    @Operation(summary = "Provider webhook (real providers only; the demo provider has none → 404)")
    public ResponseEntity<Void> webhook(@PathVariable("provider") String provider, @RequestBody String payload,
                                        @RequestHeader HttpHeaders headers) {
        Map<String, String> flat = new HashMap<>();
        headers.forEach((k, v) -> flat.put(k.toLowerCase(), String.join(",", v)));
        payments.handleWebhook(provider, payload, flat);
        return ResponseEntity.ok().build();
    }
}
