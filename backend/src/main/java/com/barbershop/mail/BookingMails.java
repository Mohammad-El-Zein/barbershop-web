package com.barbershop.mail;

import com.barbershop.auth.AppUser;
import com.barbershop.booking.Actor;
import com.barbershop.booking.Booking;
import com.barbershop.booking.BookingSource;
import com.barbershop.booking.BookingStatus;
import com.barbershop.booking.LoyaltyNotes;
import com.barbershop.common.config.FrontendLinks;
import com.barbershop.common.i18n.Lang;
import com.barbershop.common.i18n.Messages;
import com.barbershop.payment.PaymentLedger.Settlement;
import com.barbershop.service.PriceFormatter;
import com.barbershop.tenant.Tenant;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Builds every booking email (DE/EN by the recipient's language): to the customer
 * request received / accepted (pay now) / declined / confirmed (+ .ics) /
 * cancelled (+ refund info) / refunded / expired / reminder; to the barber new
 * request (accept/decline buttons) / payment received / cancelled.
 * Payment-related emails carry a "demo mode" note while the demo provider is active.
 */
@Component
public class BookingMails {

    private final Messages messages;
    private final PriceFormatter prices;
    private final FrontendLinks links;
    private final LoyaltyNotes loyaltyNotes;
    private final Clock clock;

    public BookingMails(Messages messages, PriceFormatter prices, FrontendLinks links, LoyaltyNotes loyaltyNotes,
                        Clock clock) {
        this.messages = messages;
        this.prices = prices;
        this.links = links;
        this.loyaltyNotes = loyaltyNotes;
        this.clock = clock;
    }

    // ── to the customer ──────────────────────────────────────────────────────

    public MailContent requested(Booking b, Tenant t, String manageToken) {
        Lang l = b.lang();
        MailContent m = customer(b, t)
                .subject(msg(l, "mail.requested.subject", t.getName(), when(b, t, l)))
                .preheader(msg(l, "mail.requested.preheader"))
                .paragraph(msg(l, "mail.requested.intro", b.getStaff().getName(), instant(b.getBarberDeadlineAt(), t, l)))
                .paragraph(b.getDepositCents() > 0
                        ? msg(l, "mail.requested.next", b.getStaff().getName(), money(b.getDepositCents(), b, l))
                        : msg(l, "mail.requested.next.no_deposit", b.getStaff().getName()));
        summary(m, b, t, l, true);
        return m.button(msg(l, "mail.button.manage_request"), links.manage(l, manageToken), false);
    }

    public MailContent accepted(Booking b, Tenant t, String paymentUrl, Instant deadline, boolean demo) {
        Lang l = b.lang();
        long hours = Math.max(1, (Duration.between(Instant.now(clock), deadline).toMinutes() + 59) / 60);
        MailContent m = customer(b, t)
                .subject(msg(l, "mail.accepted.subject", b.getStaff().getName(), instant(deadline, t, l)))
                .preheader(msg(l, "mail.accepted.preheader", money(b.getDepositCents(), b, l)))
                .paragraph(msg(l, "mail.accepted.intro", b.getStaff().getName()))
                .paragraph(msg(l, "mail.accepted.pay", money(b.getDepositCents(), b, l), hours, instant(deadline, t, l)))
                .paragraph(msg(l, "mail.accepted.deducted"));
        summary(m, b, t, l, true);
        if (demo) m.notice(msg(l, "mail.demo_note"), "demo");
        return m.button(msg(l, "mail.button.pay_now"), paymentUrl, true);
    }

    public MailContent declined(Booking b, Tenant t) {
        Lang l = b.lang();
        MailContent m = customer(b, t)
                .subject(msg(l, "mail.declined.subject", t.getName()))
                .paragraph(msg(l, "mail.declined.intro", b.getStaff().getName(), when(b, t, l)));
        if (b.getStatusReason() != null) m.paragraph(msg(l, "mail.declined.reason", b.getStatusReason()));
        return m.paragraph(msg(l, "mail.declined.next"))
                .button(msg(l, "mail.button.pick_other_time"), links.book(l), true);
    }

    public MailContent confirmed(Booking b, Tenant t, String manageToken, boolean depositPaid, boolean demo) {
        Lang l = b.lang();
        boolean manual = b.getSource() != BookingSource.ONLINE;
        String intro = manual ? "mail.confirmed.intro.manual"
                : depositPaid ? "mail.confirmed.intro" : "mail.confirmed.intro.no_deposit";
        MailContent m = customer(b, t)
                .subject(msg(l, "mail.confirmed.subject", when(b, t, l)))
                .preheader(msg(l, "mail.confirmed.preheader", t.getName()))
                .paragraph(msg(l, intro));
        summary(m, b, t, l, true);
        Instant onlineCancelUntil = b.getStartTime().minus(t.policy().onlineCancelCutoff());
        if (depositPaid) {
            Instant refundUntil = b.getStartTime().minus(t.policy().refundCutoff());
            m.note(msg(l, "mail.confirmed.cancel_info", instant(refundUntil, t, l), instant(onlineCancelUntil, t, l)));
            if (demo) m.notice(msg(l, "mail.demo_note"), "demo");
        } else {
            m.note(msg(l, "mail.confirmed.cancel_info.no_deposit", instant(onlineCancelUntil, t, l)));
        }
        m.note(msg(l, "mail.confirmed.ics"))
                .button(msg(l, "mail.button.manage_booking"), links.manage(l, manageToken), false);
        return m.attach(new MailContent.Attachment(l == Lang.EN ? "appointment.ics" : "termin.ics",
                "text/calendar; charset=UTF-8; method=PUBLISH", ics(b, t, l)));
    }

    public MailContent cancelled(Booking b, Tenant t, Actor by, Settlement deposit, boolean demo) {
        Lang l = b.lang();
        MailContent m = customer(b, t)
                .subject(msg(l, "mail.cancelled.subject", t.getName(), when(b, t, l)))
                .paragraph(msg(l, by == Actor.CUSTOMER ? "mail.cancelled.intro.customer" : "mail.cancelled.intro.shop"));
        summary(m, b, t, l, false);
        refundInfo(m, b, t, l, deposit, demo, false);
        return m.button(msg(l, "mail.button.rebook"), links.book(l), true);
    }

    public MailContent refunded(Booking b, Tenant t, boolean demo) {
        Lang l = b.lang();
        MailContent m = customer(b, t)
                .subject(msg(l, "mail.refunded.subject", t.getName()))
                .paragraph(msg(l, "mail.refunded.intro", money(b.getDepositCents(), b, l), when(b, t, l)));
        if (demo) m.notice(msg(l, "mail.refund.demo"), "demo");
        return m;
    }

    public MailContent expired(Booking b, Tenant t, BookingStatus from) {
        Lang l = b.lang();
        MailContent m = customer(b, t).subject(msg(l, "mail.expired.subject", t.getName(), when(b, t, l)));
        if (from == BookingStatus.PENDING_BARBER) {
            m.paragraph(msg(l, "mail.expired.intro.barber", b.getStaff().getName(), when(b, t, l)));
        } else {
            m.paragraph(msg(l, "mail.expired.intro.payment", when(b, t, l), instant(b.getPaymentDeadlineAt(), t, l)));
        }
        return m.paragraph(msg(l, "mail.expired.next"))
                .button(msg(l, "mail.button.rebook"), links.book(l), true);
    }

    public MailContent reminder(Booking b, Tenant t, String manageToken) {
        Lang l = b.lang();
        MailContent m = customer(b, t)
                .subject(msg(l, "mail.reminder.subject", t.getName(), when(b, t, l)))
                .preheader(msg(l, "mail.reminder.preheader", when(b, t, l)))
                .paragraph(msg(l, "mail.reminder.intro", when(b, t, l)));
        summary(m, b, t, l, true);
        return m.note(msg(l, "mail.reminder.cancel"))
                .button(msg(l, "mail.button.manage_booking"), links.manage(l, manageToken), false);
    }

    // ── to the barber ────────────────────────────────────────────────────────

    /** New request: details, loyalty info, and at the end the two buttons Accept / Decline. */
    public MailContent barberRequest(Booking b, Tenant t, AppUser barber, String decisionToken) {
        Lang l = barber.lang();
        MailContent m = barber(b, t, barber)
                .subject(msg(l, "mail.barber.request.subject", b.getCustomerName(), when(b, t, l)))
                .preheader(msg(l, "mail.barber.request.preheader", b.serviceNames(l)))
                .paragraph(msg(l, "mail.barber.request.intro"))
                .strongRow(msg(l, "mail.label.customer"), b.getCustomerName())
                .row(msg(l, "mail.label.services"), b.serviceNames(l))
                .strongRow(msg(l, "mail.label.when"), when(b, t, l))
                .row(msg(l, "mail.label.duration"), msg(l, "mail.label.duration.value", b.durationMin()))
                .row(msg(l, "mail.label.note"), b.getCustomerNote() == null ? "–" : b.getCustomerNote())
                .row(msg(l, "mail.label.loyalty"), loyaltyNotes.forBarber(b, t.policy(), l))
                .row(msg(l, "mail.label.total"), money(b.getPriceTotalCents(), b, l));
        if (b.getLoyaltyDiscountCents() > 0) {
            m.row(msg(l, "mail.label.loyalty_discount"), "− " + money(b.getLoyaltyDiscountCents(), b, l));
        }
        return m.note(msg(l, "mail.barber.request.deadline", instant(b.getBarberDeadlineAt(), t, l)))
                .button(msg(l, "mail.button.accept"), links.barberDecision(l, decisionToken, "accept"), true)
                .button(msg(l, "mail.button.decline"), links.barberDecision(l, decisionToken, "decline"), false)
                .afterButtons(msg(l, "mail.barber.request.buttons_hint"));
    }

    public MailContent barberPaid(Booking b, Tenant t, AppUser barber, boolean demo) {
        Lang l = barber.lang();
        MailContent m = barber(b, t, barber)
                .subject(msg(l, "mail.barber.paid.subject", b.getCustomerName(), when(b, t, l)))
                .paragraph(msg(l, "mail.barber.paid.intro", b.getCustomerName()))
                .strongRow(msg(l, "mail.label.customer"), b.getCustomerName())
                .row(msg(l, "mail.label.services"), b.serviceNames(l))
                .strongRow(msg(l, "mail.label.time"), when(b, t, l))
                .strongRow(msg(l, "mail.label.deposit"), msg(l, "mail.barber.paid.deposit", money(b.getDepositCents(), b, l)))
                .row(msg(l, "mail.label.due_at_shop"), money(b.getAmountDueAtShopCents(), b, l));
        if (demo) m.notice(msg(l, "mail.demo_note"), "demo");
        return m;
    }

    public MailContent barberCancelled(Booking b, Tenant t, AppUser barber, Actor by, Settlement deposit, boolean demo) {
        Lang l = barber.lang();
        MailContent m = barber(b, t, barber)
                .subject(msg(l, "mail.barber.cancelled.subject", b.getCustomerName(), when(b, t, l)))
                .paragraph(msg(l, by == Actor.CUSTOMER ? "mail.barber.cancelled.intro.customer"
                        : "mail.barber.cancelled.intro.shop", b.getCustomerName()))
                .row(msg(l, "mail.label.customer"), b.getCustomerName())
                .row(msg(l, "mail.label.services"), b.serviceNames(l))
                .row(msg(l, "mail.label.when"), when(b, t, l), false, true);
        refundInfo(m, b, t, l, deposit, demo, true);
        return m;
    }

    // ── building blocks ──────────────────────────────────────────────────────

    private MailContent customer(Booking b, Tenant t) {
        Lang l = b.lang();
        return new MailContent(b.getCustomerEmail(), l, t.getName())
                .greeting(msg(l, "mail.greeting", b.getCustomerName()))
                .signature(msg(l, "mail.signature", t.getName()))
                .footer(msg(l, "mail.footer"));
    }

    private MailContent barber(Booking b, Tenant t, AppUser barber) {
        Lang l = barber.lang();
        return new MailContent(barber.getEmail(), l, t.getName())
                .greeting(msg(l, "mail.greeting", barber.getName()))
                .signature(msg(l, "mail.signature.system", t.getName()))
                .footer(msg(l, "mail.footer"));
    }

    /** Services, barber, time, duration and — with prices — total / discount / deposit / due at the shop. */
    private void summary(MailContent m, Booking b, Tenant t, Lang l, boolean withPrices) {
        m.row(msg(l, "mail.label.services"), b.serviceNames(l))
                .row(msg(l, "mail.label.barber"), b.getStaff().getName())
                .strongRow(msg(l, "mail.label.when"), when(b, t, l))
                .row(msg(l, "mail.label.duration"), msg(l, "mail.label.duration.value", b.durationMin()));
        if (!withPrices) return;
        m.row(msg(l, "mail.label.total"), money(b.getPriceTotalCents(), b, l));
        if (b.getLoyaltyDiscountCents() > 0) {
            m.row(msg(l, "mail.label.loyalty_discount"), "− " + money(b.getLoyaltyDiscountCents(), b, l));
        }
        if (b.getDepositCents() > 0) {
            boolean paid = b.getStatus() == BookingStatus.CONFIRMED || b.getStatus() == BookingStatus.COMPLETED;
            m.row(msg(l, paid ? "mail.label.deposit_paid" : "mail.label.deposit"), money(b.getDepositCents(), b, l));
        }
        m.strongRow(msg(l, "mail.label.due_at_shop"), money(b.getAmountDueAtShopCents(), b, l));
    }

    private void refundInfo(MailContent m, Booking b, Tenant t, Lang l, Settlement deposit, boolean demo,
                            boolean forBarber) {
        String amount = money(b.getDepositCents(), b, l);
        String prefix = forBarber ? "mail.barber.cancelled." : "mail.cancelled.";
        switch (deposit) {
            case REFUNDED -> {
                m.notice(msg(l, prefix + "refunded", amount), "info");
                if (demo) m.notice(msg(l, "mail.refund.demo"), "demo");
            }
            case FORFEITED -> m.notice(msg(l, prefix + "forfeited", amount, t.policy().refundCutoff().toHours()), "warn");
            case NONE -> {
                if (forBarber) m.paragraph(msg(l, "mail.barber.cancelled.unpaid"));
            }
        }
    }

    private byte[] ics(Booking b, Tenant t, Lang l) {
        String summary = b.serviceNames(l) + " – " + t.getName();
        String description = msg(l, "mail.label.barber") + ": " + b.getStaff().getName() + "\n"
                + msg(l, "mail.label.due_at_shop") + ": " + money(b.getAmountDueAtShopCents(), b, l);
        return IcsCalendar.event(b.getId(), b.getStartTime(), b.getEndTime(), Instant.now(clock), summary,
                description, t.getName());
    }

    /** "Dienstag, 20. Oktober 2026, 10:00–11:15 Uhr" / "Tuesday, October 20, 2026, 10:00 AM–11:15 AM". */
    String when(Booking b, Tenant t, Lang l) {
        Locale loc = l.locale();
        ZonedDateTime start = b.getStartTime().atZone(t.zone());
        ZonedDateTime end = b.getEndTime().atZone(t.zone());
        DateTimeFormatter date = DateTimeFormatter.ofPattern(msg(l, "mail.date.pattern"), loc);
        DateTimeFormatter time = DateTimeFormatter.ofPattern(msg(l, "mail.time.pattern"), loc);
        return msg(l, "mail.time_range", date.format(start), time.format(start), time.format(end));
    }

    private String instant(Instant i, Tenant t, Lang l) {
        if (i == null) return "–";
        return DateTimeFormatter.ofPattern(msg(l, "mail.datetime.pattern"), l.locale()).format(i.atZone(t.zone()));
    }

    /** Booking amounts are exact: never "ab"/"from" (that is only for the catalogue). */
    private String money(int cents, Booking b, Lang l) {
        return prices.format(cents, b.getCurrency(), false, l);
    }

    private String msg(Lang l, String code, Object... args) {
        return messages.get(l.locale(), code, args);
    }
}
