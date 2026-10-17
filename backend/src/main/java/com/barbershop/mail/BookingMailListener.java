package com.barbershop.mail;

import com.barbershop.auth.AppUser;
import com.barbershop.auth.AppUserRepository;
import com.barbershop.auth.Role;
import com.barbershop.booking.Booking;
import com.barbershop.booking.BookingEvents.Accepted;
import com.barbershop.booking.BookingEvents.Cancelled;
import com.barbershop.booking.BookingEvents.Confirmed;
import com.barbershop.booking.BookingEvents.Declined;
import com.barbershop.booking.BookingEvents.Expired;
import com.barbershop.booking.BookingEvents.Refunded;
import com.barbershop.booking.BookingEvents.Reminder;
import com.barbershop.booking.BookingEvents.Requested;
import com.barbershop.booking.BookingRepository;
import com.barbershop.payment.PaymentProvider;
import com.barbershop.tenant.Tenant;
import com.barbershop.tenant.TenantRepository;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
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
 *
 * <p>Barber emails go to the barber's own account (linked staff profile); if the
 * barber has no login yet, to the shop's admins.
 */
@Component
public class BookingMailListener {

    private static final Logger log = LoggerFactory.getLogger(BookingMailListener.class);

    private final BookingRepository bookings;
    private final TenantRepository tenants;
    private final AppUserRepository users;
    private final BookingMails mails;
    private final MailService mail;
    private final PaymentProvider provider;

    public BookingMailListener(BookingRepository bookings, TenantRepository tenants, AppUserRepository users,
                               BookingMails mails, MailService mail, PaymentProvider provider) {
        this.bookings = bookings;
        this.tenants = tenants;
        this.users = users;
        this.mails = mails;
        this.mail = mail;
        this.provider = provider;
    }

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void on(Requested e) {
        send(e.bookingId(), "requested",
                (b, t) -> mails.requested(b, t, e.manageToken()),
                (b, t) -> u -> mails.barberRequest(b, t, u, e.decisionToken()));
    }

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void on(Accepted e) {
        send(e.bookingId(), "accepted",
                (b, t) -> mails.accepted(b, t, e.paymentUrl(), e.paymentDeadline(), provider.isDemo()), null);
    }

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void on(Declined e) {
        send(e.bookingId(), "declined", mails::declined, null);
    }

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void on(Expired e) {
        send(e.bookingId(), "expired", (b, t) -> mails.expired(b, t, e.from()), null);
    }

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void on(Confirmed e) {
        send(e.bookingId(), "confirmed",
                (b, t) -> mails.confirmed(b, t, e.manageToken(), e.depositPaid(), provider.isDemo()),
                e.depositPaid() ? (b, t) -> u -> mails.barberPaid(b, t, u, provider.isDemo()) : null);
    }

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void on(Cancelled e) {
        send(e.bookingId(), "cancelled",
                (b, t) -> mails.cancelled(b, t, e.by(), e.deposit(), provider.isDemo()),
                (b, t) -> u -> mails.barberCancelled(b, t, u, e.by(), e.deposit(), provider.isDemo()));
    }

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void on(Refunded e) {
        send(e.bookingId(), "refunded", (b, t) -> mails.refunded(b, t, provider.isDemo()), null);
    }

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void on(Reminder e) {
        send(e.bookingId(), "reminder", (b, t) -> mails.reminder(b, t, e.manageToken()), null);
    }

    /**
     * @param toCustomer customer email (skipped when the booking has no email, e.g. anonymised walk-in)
     * @param toBarber   barber email per recipient, or null
     */
    private void send(UUID bookingId, String kind, BiFunction<Booking, Tenant, MailContent> toCustomer,
                      BiFunction<Booking, Tenant, Function<AppUser, MailContent>> toBarber) {
        Booking b;
        Tenant t;
        try {
            b = bookings.findWithDetailsById(bookingId).orElseThrow();
            t = tenants.findById(b.getTenantId()).orElseThrow();
        } catch (Exception ex) {
            log.error("Could not load booking {} for {} mail: {}", bookingId, kind, ex.getMessage(), ex);
            return;
        }
        if (toCustomer != null && b.getCustomerEmail() != null) {
            deliver(toCustomer.apply(b, t), kind, bookingId);
        }
        if (toBarber != null) {
            Function<AppUser, MailContent> build = toBarber.apply(b, t);
            barberRecipients(b).forEach(u -> deliver(build.apply(u), "barber-" + kind, bookingId));
        }
    }

    private void deliver(MailContent m, String kind, UUID bookingId) {
        try {
            mail.send(m);
            log.info("Sent {} mail for booking {} ({})", kind, bookingId, m.lang().code());
        } catch (Exception ex) {
            log.error("Could not send {} mail for booking {}: {}", kind, bookingId, ex.getMessage(), ex);
        }
    }

    private List<AppUser> barberRecipients(Booking b) {
        List<AppUser> barbers = users.findByStaffId(b.getStaff().getId()).stream()
                .filter(u -> u.getRole() == Role.BARBER).toList();
        return barbers.isEmpty() ? users.findByTenantIdAndRole(b.getTenantId(), Role.ADMIN) : barbers;
    }
}
