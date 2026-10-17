package com.barbershop.mail;

import com.barbershop.booking.Booking;
import com.barbershop.booking.BookingEvents.BookingCancelled;
import com.barbershop.booking.BookingEvents.BookingConfirmed;
import com.barbershop.booking.BookingEvents.BookingCreated;
import com.barbershop.booking.BookingRepository;
import com.barbershop.booking.BookingStatus;
import com.barbershop.mail.MailService.Kind;
import com.barbershop.tenant.Tenant;
import com.barbershop.tenant.TenantRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends booking emails after the booking transaction has committed, on a
 * separate (virtual) thread, so SMTP latency or failures never affect the
 * booking itself. Failures are logged; the booking stays valid.
 */
@Component
public class BookingMailListener {

    private static final Logger log = LoggerFactory.getLogger(BookingMailListener.class);

    private final BookingRepository bookings;
    private final TenantRepository tenants;
    private final MailService mail;

    public BookingMailListener(BookingRepository bookings, TenantRepository tenants, MailService mail) {
        this.bookings = bookings;
        this.tenants = tenants;
        this.mail = mail;
    }

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onCreated(BookingCreated event) {
        send(event.bookingId(), b -> b.getStatus() == BookingStatus.PENDING ? Kind.RECEIVED : Kind.CONFIRMED,
                event.rawCancelToken());
    }

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onConfirmed(BookingConfirmed event) {
        // the raw cancel token is not stored; the cancel link was in the "request received" email
        send(event.bookingId(), b -> Kind.CONFIRMED, null);
    }

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onCancelled(BookingCancelled event) {
        send(event.bookingId(), b -> event.byCustomer() ? Kind.CANCELLED_BY_CUSTOMER : Kind.CANCELLED_BY_SHOP, null);
    }

    private void send(UUID bookingId, java.util.function.Function<Booking, Kind> kindOf, String rawToken) {
        try {
            Booking b = bookings.findWithDetailsById(bookingId).orElseThrow();
            Tenant tenant = tenants.findById(b.getTenantId()).orElseThrow();
            Kind kind = kindOf.apply(b);
            mail.sendBookingMail(b, tenant, kind, rawToken);
            log.info("Sent {} mail for booking {} ({})", kind, bookingId, b.getLocale());
        } catch (Exception e) {
            log.error("Could not send mail for booking {}: {}", bookingId, e.getMessage(), e);
        }
    }
}
