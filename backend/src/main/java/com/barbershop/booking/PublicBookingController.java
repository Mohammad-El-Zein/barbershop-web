package com.barbershop.booking;

import com.barbershop.booking.BookingDtos.BookingView;
import com.barbershop.booking.BookingDtos.CreateBookingRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Locale;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
@Tag(name = "Public: bookings")
public class PublicBookingController {

    private final BookingService bookings;

    public PublicBookingController(BookingService bookings) {
        this.bookings = bookings;
    }

    @PostMapping("/{tenant}/bookings")
    @Operation(summary = "Book a slot (201). 409 if it was just taken, 422 if it is not a valid slot. "
            + "The response contains the cancel token (also sent by email).")
    public ResponseEntity<BookingView> create(@PathVariable("tenant") String tenant,
                                              @Valid @RequestBody CreateBookingRequest req, Locale locale) {
        BookingView created = bookings.create(tenant, req, locale);
        return ResponseEntity.created(URI.create("/api/public/bookings/" + created.cancelToken())).body(created);
    }

    @GetMapping("/bookings/{token}")
    @Operation(summary = "Booking details via the cancel token from the confirmation email")
    public BookingView get(@PathVariable("token") String token) {
        return bookings.getByToken(token);
    }

    @DeleteMapping("/bookings/{token}")
    @Operation(summary = "Cancel via the cancel token (until the cancellation cutoff; idempotent)")
    public BookingView cancel(@PathVariable("token") String token) {
        return bookings.cancelByToken(token);
    }
}
