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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
 * <p>Recipients per event:
 * <ul>
 *   <li>the customer (when the booking has an email address);</li>
 *   <li>the barber: their own account (linked staff profile); without a login yet, the
 *       shop's admins instead;</li>
 *   <li>the admins (short information): every admin account of the shop plus the
 *       configured APP_ADMIN_EMAIL account of the same shop — each once, and not when the
 *       address already got the barber's mail for this event.</li>
 * </ul>
 *
 * <table>
 *   <tr><th>Event</th><th>Customer</th><th>Barber</th><th>Admins</th></tr>
 *   <tr><td>request</td><td>received</td><td>new request (accept/decline)</td><td>new request (accept/decline)</td></tr>
 *   <tr><td>accepted (deposit due)</td><td>pay now</td><td>–</td><td>accepted, deposit due by</td></tr>
 *   <tr><td>confirmed (paid / 0 €)</td><td>confirmation + .ics</td><td>deposit paid / confirmed</td><td>paid / confirmed</td></tr>
 *   <tr><td>manual booking (staff)</td><td>confirmation + .ics</td><td>–</td><td>–</td></tr>
 *   <tr><td>declined, expired</td><td>✓</td><td>–</td><td>✓</td></tr>
 *   <tr><td>cancelled</td><td>✓</td><td>✓</td><td>✓</td></tr>
 *   <tr><td>refunded, reminder</td><td>✓</td><td>–</td><td>–</td></tr>
 * </table>
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

    /** Who gets which mail of one event; null = nobody in that role. */
    private record Mails(BiFunction<Booking, Tenant, MailContent> customer,
                         BiFunction<Booking, Tenant, Function<AppUser, MailContent>> barber,
                         BiFunction<Booking, Tenant, Function<AppUser, MailContent>> admin) {}

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void on(Requested e) {
        send(e.bookingId(), "requested", new Mails(
                (b, t) -> mails.requested(b, t, e.manageToken()),
                (b, t) -> u -> mails.barberRequest(b, t, u, e.decisionToken()),
                (b, t) -> u -> mails.adminRequested(b, t, u, e.decisionToken())));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void on(Accepted e) {
        send(e.bookingId(), "accepted", new Mails(
                (b, t) -> mails.accepted(b, t, e.paymentUrl(), e.paymentDeadline(), provider.isDemo()),
                null,
                (b, t) -> u -> mails.adminAccepted(b, t, u, e.paymentDeadline())));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void on(Declined e) {
        send(e.bookingId(), "declined", new Mails(mails::declined, null, (b, t) -> u -> mails.adminDeclined(b, t, u)));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void on(Expired e) {
        send(e.bookingId(), "expired", new Mails(
                (b, t) -> mails.expired(b, t, e.from()),
                null,
                (b, t) -> u -> mails.adminExpired(b, t, u, e.from())));
    }

    /** Online: paid deposit or none due → barber and admins are told. A manual booking was made by the staff. */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void on(Confirmed e) {
        send(e.bookingId(), "confirmed", new Mails(
                (b, t) -> mails.confirmed(b, t, e.manageToken(), e.depositPaid(), provider.isDemo()),
                e.manual() ? null : e.depositPaid()
                        ? (b, t) -> u -> mails.barberPaid(b, t, u, provider.isDemo())
                        : (b, t) -> u -> mails.barberConfirmed(b, t, u),
                e.manual() ? null : (b, t) -> u -> mails.adminConfirmed(b, t, u, e.depositPaid())));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void on(Cancelled e) {
        send(e.bookingId(), "cancelled", new Mails(
                (b, t) -> mails.cancelled(b, t, e.by(), e.deposit(), provider.isDemo()),
                (b, t) -> u -> mails.barberCancelled(b, t, u, e.by(), e.deposit(), provider.isDemo()),
                (b, t) -> u -> mails.adminCancelled(b, t, u, e.by())));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void on(Refunded e) {
        send(e.bookingId(), "refunded", new Mails((b, t) -> mails.refunded(b, t, provider.isDemo()), null, null));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void on(Reminder e) {
        send(e.bookingId(), "reminder", new Mails((b, t) -> mails.reminder(b, t, e.manageToken()), null, null));
    }

    private void send(UUID bookingId, String kind, Mails m) {
        Booking b;
        Tenant t;
        try {
            b = bookings.findWithDetailsById(bookingId).orElseThrow();
            t = tenants.findById(b.getTenantId()).orElseThrow();
        } catch (Exception ex) {
            log.error("Could not load booking {} for {} mail: {}", bookingId, kind, ex.getMessage(), ex);
            return;
        }
        // skipped when the booking has no email (e.g. anonymised walk-in)
        if (m.customer() != null && b.getCustomerEmail() != null) {
            deliver(() -> m.customer().apply(b, t), kind, b);
        }
        Set<String> told = new HashSet<>(); // staff addresses that already got a mail about this event
        if (m.barber() != null) {
            Function<AppUser, MailContent> build = m.barber().apply(b, t);
            for (AppUser u : barberRecipients(b)) {
                if (told.add(key(u))) deliver(() -> build.apply(u), "barber-" + kind, b);
            }
        }
        if (m.admin() != null) {
            Function<AppUser, MailContent> build = m.admin().apply(b, t);
            for (AppUser u : adminRecipients(b)) {
                if (told.add(key(u))) deliver(() -> build.apply(u), "admin-" + kind, b);
            }
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

    /** The barber's own login; without one yet, the shop's admins. */
    private List<AppUser> barberRecipients(Booking b) {
        List<AppUser> barbers = users.findByStaffId(b.getStaff().getId()).stream()
                .filter(u -> u.getRole() == Role.BARBER).toList();
        return barbers.isEmpty() ? users.findByTenantIdAndRole(b.getTenantId(), Role.ADMIN) : barbers;
    }

    /** Every admin account of the shop, plus the configured admin account (APP_ADMIN_EMAIL) if it is of this shop. */
    private List<AppUser> adminRecipients(Booking b) {
        List<AppUser> admins = new ArrayList<>(users.findByTenantIdAndRole(b.getTenantId(), Role.ADMIN));
        if (adminEmail != null && !adminEmail.isBlank()) {
            users.findByTenantAndEmail(b.getTenantId(), adminEmail.trim())
                    .filter(u -> u.getRole() == Role.ADMIN)
                    .filter(admin -> admins.stream().noneMatch(u -> u.getId().equals(admin.getId())))
                    .ifPresent(admins::add);
        }
        return admins;
    }

    private static String key(AppUser u) {
        return u.getEmail().trim().toLowerCase(Locale.ROOT);
    }
}
