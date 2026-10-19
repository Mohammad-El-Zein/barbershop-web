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
import com.barbershop.common.config.AppProperties;
import com.barbershop.payment.PaymentProvider;
import com.barbershop.tenant.Tenant;
import com.barbershop.tenant.TenantRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Booking emails: rendered and put into the {@link MailOutbox} inside the booking's
 * own transaction (BEFORE_COMMIT), so a mail exists exactly when the booking change
 * commits. Sending happens after the commit (outbox worker, with retries), so SMTP
 * latency or failures never affect the booking. A rendering error is logged and never
 * rolls the booking back (see {@link MailOutbox#enqueue}).
 *
 * <p>Barber emails go to the barber's own account (linked staff profile); if the
 * barber has no login yet, to the shop's admins. A new booking request also goes, as a
 * copy, to the configured admin account (APP_ADMIN_EMAIL) of the same shop.
 */
@Component
public class BookingMailListener {

    private static final Logger log = LoggerFactory.getLogger(BookingMailListener.class);

    private final BookingRepository bookings;
    private final TenantRepository tenants;
    private final AppUserRepository users;
    private final BookingMails mails;
    private final MailOutbox outbox;
    private final PaymentProvider provider;
    private final String adminEmail;

    public BookingMailListener(BookingRepository bookings, TenantRepository tenants, AppUserRepository users,
                               BookingMails mails, MailOutbox outbox, PaymentProvider provider, AppProperties props) {
        this.bookings = bookings;
        this.tenants = tenants;
        this.users = users;
        this.mails = mails;
        this.outbox = outbox;
        this.provider = provider;
        this.adminEmail = props.admin().email();
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void on(Requested e) {
        send(e.bookingId(), "requested",
                (b, t) -> mails.requested(b, t, e.manageToken()),
                (b, t) -> u -> mails.barberRequest(b, t, u, e.decisionToken()), true);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void on(Accepted e) {
        send(e.bookingId(), "accepted",
                (b, t) -> mails.accepted(b, t, e.paymentUrl(), e.paymentDeadline(), provider.isDemo()), null);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void on(Declined e) {
        send(e.bookingId(), "declined", mails::declined, null);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void on(Expired e) {
        send(e.bookingId(), "expired", (b, t) -> mails.expired(b, t, e.from()), null);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void on(Confirmed e) {
        send(e.bookingId(), "confirmed",
                (b, t) -> mails.confirmed(b, t, e.manageToken(), e.depositPaid(), provider.isDemo()),
                e.depositPaid() ? (b, t) -> u -> mails.barberPaid(b, t, u, provider.isDemo()) : null);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void on(Cancelled e) {
        send(e.bookingId(), "cancelled",
                (b, t) -> mails.cancelled(b, t, e.by(), e.deposit(), provider.isDemo()),
                (b, t) -> u -> mails.barberCancelled(b, t, u, e.by(), e.deposit(), provider.isDemo()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void on(Refunded e) {
        send(e.bookingId(), "refunded", (b, t) -> mails.refunded(b, t, provider.isDemo()), null);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void on(Reminder e) {
        send(e.bookingId(), "reminder", (b, t) -> mails.reminder(b, t, e.manageToken()), null);
    }

    private void send(UUID bookingId, String kind, BiFunction<Booking, Tenant, MailContent> toCustomer,
                      BiFunction<Booking, Tenant, Function<AppUser, MailContent>> toBarber) {
        send(bookingId, kind, toCustomer, toBarber, false);
    }

    /**
     * @param toCustomer customer email (skipped when the booking has no email, e.g. anonymised walk-in)
     * @param toBarber   barber email per recipient, or null
     * @param adminCopy  also send the barber email to the configured admin
     */
    private void send(UUID bookingId, String kind, BiFunction<Booking, Tenant, MailContent> toCustomer,
                      BiFunction<Booking, Tenant, Function<AppUser, MailContent>> toBarber, boolean adminCopy) {
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
            deliver(() -> toCustomer.apply(b, t), kind, b);
        }
        if (toBarber != null) {
            Function<AppUser, MailContent> build = toBarber.apply(b, t);
            barberRecipients(b, adminCopy).forEach(u -> deliver(() -> build.apply(u), "barber-" + kind, b));
        }
    }

    private void deliver(Supplier<MailContent> content, String kind, Booking b) {
        MailContent m;
        try {
            m = content.get();
        } catch (Exception ex) {
            // a mail that can't be built must not fail the booking change
            log.error("Could not build {} mail for booking {}: {}", kind, b.getId(), ex.getMessage(), ex);
            return;
        }
        UUID id = outbox.enqueue(m, kind, b.getTenantId(), b.getId(), null); // storing fails the transaction (outbox)
        if (id != null) log.info("Queued {} mail {} for booking {} ({})", kind, id, b.getId(), m.lang().code());
    }

    private List<AppUser> barberRecipients(Booking b, boolean adminCopy) {
        List<AppUser> barbers = users.findByStaffId(b.getStaff().getId()).stream()
                .filter(u -> u.getRole() == Role.BARBER).toList();
        List<AppUser> recipients = new ArrayList<>(
                barbers.isEmpty() ? users.findByTenantIdAndRole(b.getTenantId(), Role.ADMIN) : barbers);
        if (adminCopy && adminEmail != null && !adminEmail.isBlank()) {
            // only the admin account of this shop; never twice to the same account
            users.findByTenantAndEmail(b.getTenantId(), adminEmail.trim())
                    .filter(u -> u.getRole() == Role.ADMIN)
                    .filter(admin -> recipients.stream().noneMatch(u -> u.getId().equals(admin.getId())))
                    .ifPresent(recipients::add);
        }
        return recipients;
    }
}
