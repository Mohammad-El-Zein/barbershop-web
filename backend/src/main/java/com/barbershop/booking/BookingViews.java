package com.barbershop.booking;

import com.barbershop.booking.BookingDtos.Actions;
import com.barbershop.booking.BookingDtos.BookingAdminView;
import com.barbershop.booking.BookingDtos.BookingView;
import com.barbershop.booking.BookingDtos.MoneyView;
import com.barbershop.booking.BookingDtos.PaymentInfo;
import com.barbershop.booking.BookingDtos.PriceView;
import com.barbershop.booking.BookingDtos.ServiceLine;
import com.barbershop.booking.BookingDtos.StaffRef;
import com.barbershop.common.i18n.Lang;
import com.barbershop.payment.Payment;
import com.barbershop.payment.PaymentProvider;
import com.barbershop.payment.PaymentRepository;
import com.barbershop.payment.PaymentStatus;
import com.barbershop.service.PriceFormatter;
import com.barbershop.tenant.Tenant;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Maps bookings to API views (prices formatted per language, times in the shop's timezone). */
@Component
public class BookingViews {

    private final PriceFormatter prices;
    private final PaymentRepository payments;
    private final PaymentProvider provider;
    private final Clock clock;

    public BookingViews(PriceFormatter prices, PaymentRepository payments, PaymentProvider provider, Clock clock) {
        this.prices = prices;
        this.payments = payments;
        this.provider = provider;
        this.clock = clock;
    }

    public BookingView customer(Booking b, Tenant t, Lang lang) {
        return customer(b, t, lang, payments.findByBookingId(b.getId()).orElse(null));
    }

    public List<BookingView> customer(List<Booking> list, Tenant t, Lang lang) {
        Map<UUID, Payment> byBooking = paymentsFor(list);
        return list.stream().map(b -> customer(b, t, lang, byBooking.get(b.getId()))).toList();
    }

    public BookingView customer(Booking b, Tenant t, Lang lang, Payment payment) {
        ZoneId zone = t.zone();
        return new BookingView(b.getId(), b.getStatus(), lines(b, lang), staff(b), at(b.getStartTime(), zone),
                at(b.getEndTime(), zone), b.durationMin(), t.getTimezone(), b.getCustomerName(), b.getCustomerEmail(),
                b.getCustomerPhone(), b.getLocale(), b.getCustomerNote(), price(b, lang), b.getLoyaltyStatus(),
                payment(b, payment, zone, lang), at(b.getBarberDeadlineAt(), zone), b.getStatusReason(),
                actions(b, t, payment), at(b.getCreatedAt(), zone));
    }

    public BookingAdminView admin(Booking b, Tenant t, Lang lang, Actor viewer) {
        return admin(b, t, lang, viewer, payments.findByBookingId(b.getId()).orElse(null));
    }

    public List<BookingAdminView> admin(List<Booking> list, Tenant t, Lang lang, Actor viewer) {
        Map<UUID, Payment> byBooking = paymentsFor(list);
        return list.stream().map(b -> admin(b, t, lang, viewer, byBooking.get(b.getId()))).toList();
    }

    public BookingAdminView admin(Booking b, Tenant t, Lang lang, Actor viewer, Payment payment) {
        ZoneId zone = t.zone();
        return new BookingAdminView(b.getId(), b.getStatus(), b.getSource(), lines(b, lang), staff(b),
                at(b.getStartTime(), zone), at(b.getEndTime(), zone), b.durationMin(), b.getCustomerName(),
                b.getCustomerEmail(), b.getCustomerPhone(), b.getUserId(), b.getLocale(), b.getCustomerNote(),
                price(b, lang), b.getLoyaltyStatus(), payment(b, payment, zone, lang),
                at(b.getBarberDeadlineAt(), zone), at(b.getAcceptedAt(), zone), at(b.getConfirmedAt(), zone),
                at(b.getCompletedAt(), zone), at(b.getCancelledAt(), zone), b.getCancelledBy(), b.getStatusReason(),
                BookingStateMachine.targets(b.getStatus(), viewer), at(b.getCreatedAt(), zone));
    }

    public List<ServiceLine> lines(Booking b, Lang lang) {
        return b.getItems().stream()
                .map(i -> new ServiceLine(i.getServiceId(), i.name(lang), i.getDurationMin(),
                        money(i.getPriceCents(), b.getCurrency(), lang), i.isPriceFrom()))
                .toList();
    }

    public PriceView price(Booking b, Lang lang) {
        String c = b.getCurrency();
        return new PriceView(c, money(b.getPriceTotalCents(), c, lang), money(b.getLoyaltyDiscountCents(), c, lang),
                money(b.getDepositCents(), c, lang), money(b.getAmountDueAtShopCents(), c, lang), b.isPriceFrom());
    }

    public PriceView price(BookingPricing.Breakdown p, String currency, Lang lang) {
        return new PriceView(currency, money(p.totalCents(), currency, lang),
                money(p.loyaltyDiscountCents(), currency, lang), money(p.depositCents(), currency, lang),
                money(p.amountDueAtShopCents(), currency, lang), p.priceFrom());
    }

    public MoneyView money(int cents, String currency, Lang lang) {
        return new MoneyView(cents, prices.format(cents, currency, false, lang));
    }

    public boolean demoMode() {
        return provider.isDemo();
    }

    /** Can the customer still cancel, and would the deposit come back if they did it now? */
    public Actions actions(Booking b, Tenant t, Payment payment) {
        Instant now = Instant.now(clock);
        boolean canCancel = b.getStatus().isOpen() && now.isBefore(b.getStartTime());
        boolean paid = payment != null && payment.getStatus() == PaymentStatus.PAID;
        boolean refundNow = paid && BookingRules.depositRefundable(Actor.CUSTOMER, now, b.getStartTime(),
                t.policy().refundCutoff());
        OffsetDateTime refundableUntil = b.getDepositCents() > 0
                ? at(b.getStartTime().minus(t.policy().refundCutoff()), t.zone()) : null;
        return new Actions(canCancel, refundNow, refundableUntil);
    }

    private PaymentInfo payment(Booking b, Payment p, ZoneId zone, Lang lang) {
        if (p == null) return null;
        return new PaymentInfo(p.getStatus(), money(p.getAmountCents(), p.getCurrency(), lang),
                at(b.getPaymentDeadlineAt(), zone), provider.isDemo());
    }

    private Map<UUID, Payment> paymentsFor(List<Booking> list) {
        if (list.isEmpty()) return Map.of();
        return payments.findByBookingIdIn(list.stream().map(Booking::getId).toList()).stream()
                .collect(Collectors.toMap(Payment::getBookingId, Function.identity()));
    }

    private static StaffRef staff(Booking b) {
        return new StaffRef(b.getStaff().getId(), b.getStaff().getName());
    }

    private static OffsetDateTime at(Instant instant, ZoneId zone) {
        return instant == null ? null : instant.atZone(zone).toOffsetDateTime();
    }
}
