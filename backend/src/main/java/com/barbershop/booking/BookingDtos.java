package com.barbershop.booking;

import com.barbershop.payment.PaymentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** API shapes of the booking feature. */
public final class BookingDtos {

    private BookingDtos() {}

    static final String PHONE = "^\\+?[0-9 ()/-]{6,30}$";

    // ── requests ─────────────────────────────────────────────────────────────

    /**
     * Online booking request (guest or logged-in customer).
     * <ul>
     *   <li>serviceIds: one or more services (e.g. haircut + beard) — duration and price are summed,</li>
     *   <li>staffId omitted = any barber (the server picks the least busy free one),</li>
     *   <li>start = a slot start from /availability (ISO-8601 with offset),</li>
     *   <li>guests: name, email, phone and consent=true are required; logged-in customers
     *       default to their account data (the email is always the account email),</li>
     *   <li>lang = language for all emails; defaults to the account / request language.</li>
     * </ul>
     */
    public record CreateBookingRequest(
            @NotEmpty(message = "{validation.required}") @Size(max = 10, message = "{validation.size.max}")
            List<@NotNull(message = "{validation.required}") UUID> serviceIds,
            UUID staffId,
            @NotNull(message = "{validation.required}") OffsetDateTime start,
            @Size(min = 2, max = 120, message = "{validation.size}") String customerName,
            @Pattern(regexp = PHONE, message = "{validation.phone}") String customerPhone,
            @Email(message = "{validation.email}") @Size(max = 254, message = "{validation.size.max}") String customerEmail,
            @Pattern(regexp = "de|en", message = "{validation.lang}") String lang,
            @Size(max = 500, message = "{validation.size.max}") String note,
            Boolean consent,
            /** First-touch campaign data; only sent by the website with marketing consent. */
            @Valid AttributionRequest attribution) {}

    /**
     * UTM parameters, click ids and landing page as captured by the website (first touch,
     * 30-day window). Over-long values are rejected; the backend also cleans and cuts them.
     */
    public record AttributionRequest(
            @Size(max = 300, message = "{validation.size.max}") String source,
            @Size(max = 300, message = "{validation.size.max}") String medium,
            @Size(max = 300, message = "{validation.size.max}") String campaign,
            @Size(max = 300, message = "{validation.size.max}") String term,
            @Size(max = 300, message = "{validation.size.max}") String content,
            @Size(max = 500, message = "{validation.size.max}") String gclid,
            @Size(max = 500, message = "{validation.size.max}") String fbclid,
            @Size(max = 500, message = "{validation.size.max}") String landingPath,
            OffsetDateTime capturedAt) {}

    /** Attribution as shown to admins (null fields are omitted). */
    public record AttributionView(String source, String medium, String campaign, String term, String content,
                                  boolean gclid, boolean fbclid, String landingPath) {}

    public record PricePreviewRequest(
            @NotEmpty(message = "{validation.required}") @Size(max = 10, message = "{validation.size.max}")
            List<@NotNull(message = "{validation.required}") UUID> serviceIds) {}

    /** Walk-in / phone booking by an admin: confirmed immediately, no deposit, email optional. */
    public record ManualBookingRequest(
            @NotEmpty(message = "{validation.required}") @Size(max = 10, message = "{validation.size.max}")
            List<@NotNull(message = "{validation.required}") UUID> serviceIds,
            @NotNull(message = "{validation.required}") UUID staffId,
            @NotNull(message = "{validation.required}") OffsetDateTime start,
            @NotBlank(message = "{validation.required}")
            @Size(min = 2, max = 120, message = "{validation.size}") String customerName,
            @Pattern(regexp = PHONE, message = "{validation.phone}") String customerPhone,
            @Email(message = "{validation.email}") @Size(max = 254, message = "{validation.size.max}") String customerEmail,
            @Pattern(regexp = "de|en", message = "{validation.lang}") String lang,
            @Size(max = 500, message = "{validation.size.max}") String note,
            @NotNull(message = "{validation.required}") BookingSource source) {}

    public record ReasonRequest(@Size(max = 500, message = "{validation.size.max}") String reason) {}

    /** refund: admins only — overrides the refund rule (null = apply the rule). */
    public record AdminCancelRequest(@Size(max = 500, message = "{validation.size.max}") String reason,
                                     Boolean refund) {}

    // ── views ────────────────────────────────────────────────────────────────

    public record MoneyView(int cents, String formatted) {}

    /** A booked service at its exact price (a booking never shows "from" prices). */
    public record ServiceLine(UUID serviceId, String name, int durationMin, MoneyView price) {}

    public record StaffRef(UUID id, String name) {}

    /** price_total − loyalty_discount − deposit = amount_due_at_shop; all exact amounts. */
    public record PriceView(String currency, MoneyView total, MoneyView loyaltyDiscount, MoneyView deposit,
                            MoneyView amountDueAtShop) {}

    public record PaymentInfo(PaymentStatus status, MoneyView amount, OffsetDateTime deadline, boolean demoMode) {}

    /**
     * What the caller may do next (drives buttons in the frontend).
     * canCancel: online cancellation is still possible (open status, before cancellableUntil).
     * cancellableUntil: start − online cancel cutoff (default 2 h); later: contact the shop.
     * refundableUntil: start − refund cutoff (default 24 h); later the deposit is kept.
     */
    public record Actions(boolean canCancel, OffsetDateTime cancellableUntil, boolean refundIfCancelledNow,
                          OffsetDateTime refundableUntil) {}

    /** Customer view (manage link, "my bookings", creation response). */
    public record BookingView(UUID id, BookingStatus status, List<ServiceLine> services, StaffRef staff,
                              OffsetDateTime start, OffsetDateTime end, int durationMin, String timezone,
                              String customerName, String customerEmail, String customerPhone, String lang,
                              String note, PriceView price, LoyaltyStatus loyalty, PaymentInfo payment,
                              OffsetDateTime barberDeadline, String statusReason, Actions actions,
                              OffsetDateTime createdAt, String statusToken) {

        /** The creation response carries the read-only status link token (only then; it is never stored raw). */
        public BookingView withStatusToken(String token) {
            return new BookingView(id, status, services, staff, start, end, durationMin, timezone, customerName,
                    customerEmail, customerPhone, lang, note, price, loyalty, payment, barberDeadline, statusReason,
                    actions, createdAt, token);
        }
    }

    /** A fresh single-use link to the deposit payment page. */
    public record PaymentLinkView(String token, String url) {}

    /** Barber / admin view: everything incl. contact data, lifecycle timestamps and allowed transitions. */
    public record BookingAdminView(UUID id, BookingStatus status, BookingSource source, List<ServiceLine> services,
                                   StaffRef staff, OffsetDateTime start, OffsetDateTime end, int durationMin,
                                   String customerName, String customerEmail, String customerPhone, UUID userId,
                                   String lang, String note, PriceView price, LoyaltyStatus loyalty,
                                   PaymentInfo payment, OffsetDateTime barberDeadline, OffsetDateTime acceptedAt,
                                   OffsetDateTime confirmedAt, OffsetDateTime completedAt, OffsetDateTime cancelledAt,
                                   Actor cancelledBy, String statusReason, Set<BookingStatus> allowedTransitions,
                                   OffsetDateTime createdAt, AttributionView attribution,
                                   List<TimelineEntry> timeline) {}

    /**
     * One step in the history of a booking (admin detail), oldest first: CREATED, ACCEPTED, PAID,
     * CONFIRMED, DECLINED, EXPIRED, CANCELLED, REFUNDED, FORFEITED, REMINDER_SENT, COMPLETED, NO_SHOW.
     */
    public record TimelineEntry(String event, OffsetDateTime at) {}

    public record LoyaltyInfo(int visitsInCurrentCycle, int threshold, int discountPercent,
                              boolean nextDiscountAvailable, boolean appliesToThisBooking) {}

    /** Price preview: the exact numbers the booking would get (loyalty only for logged-in customers). */
    public record PricePreview(List<ServiceLine> services, int durationMin, PriceView price, LoyaltyInfo loyalty) {}

    /** The barber's accept/decline page (email link). Shows, never changes. */
    public record DecisionView(UUID bookingId, BookingStatus status, String customerName, List<ServiceLine> services,
                               StaffRef staff, OffsetDateTime start, OffsetDateTime end, int durationMin, String note,
                               String loyaltyNote, PriceView price, OffsetDateTime respondUntil,
                               List<String> actions) {}

    /** The demo payment page /[locale]/pay/[token]. */
    public record PaymentPageView(boolean demoMode, PaymentStatus status, MoneyView amount, OffsetDateTime payUntil,
                                  BookingView booking, List<String> actions) {}

    public record PaymentResult(PaymentStatus paymentStatus, BookingStatus bookingStatus, boolean alreadyProcessed,
                                boolean demoMode) {}

    public record PageView<T>(List<T> items, int page, int size, long totalItems, int totalPages) {}

    public record DayCount(LocalDate date, long booked, long cancelled) {}

    public record ServiceCount(UUID serviceId, String name, long booked, long cancelled) {}

    public record StatsView(LocalDate from, LocalDate to, String timezone, long totalBooked, long totalCancelled,
                            List<DayCount> perDay, List<ServiceCount> perService) {}

    /**
     * Online bookings per campaign. source / medium / campaign fall back to the click id
     * ("google" / "cpc" for gclid, "facebook" / "paid_social" for fbclid), the ad landing page
     * ("lp:<slug>"), or "(direct)" / "(none)" / "(not set)". "confirmed" = the booking reached
     * confirmation (even if cancelled later); revenue = their price after loyalty discount.
     */
    public record CampaignRow(String source, String medium, String campaign, long bookings, long confirmed,
                              long confirmedRevenueCents) {}

    public record CampaignStatsView(LocalDate from, LocalDate to, String timezone, String currency,
                                    long totalBookings, long attributedBookings, List<CampaignRow> campaigns) {}
}
