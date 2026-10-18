package com.barbershop.tenant;

import com.barbershop.payment.PaymentProvider;
import com.barbershop.staff.OpeningHours;
import com.barbershop.staff.Staff;
import com.barbershop.staff.StaffRepository;
import com.barbershop.staff.WorkingHoursRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/{tenant}")
@Tag(name = "Public: shop")
public class TenantController {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final TenantService tenants;
    private final PaymentProvider payments;
    private final StaffRepository staff;
    private final WorkingHoursRepository hours;

    public TenantController(TenantService tenants, PaymentProvider payments, StaffRepository staff,
                            WorkingHoursRepository hours) {
        this.tenants = tenants;
        this.payments = payments;
        this.staff = staff;
        this.hours = hours;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "Shop info (name, timezone, currency, theme, address, opening hours) and the booking rules "
            + "customers see")
    public TenantView get(@PathVariable("tenant") String slug) {
        Tenant t = tenants.requireBySlug(slug);
        List<UUID> active = staff.findByTenantIdAndActiveTrueOrderBySortOrderAscNameAsc(t.getId()).stream()
                .map(Staff::getId).toList();
        List<OpeningRange> opening = OpeningHours.of(active.isEmpty() ? List.of()
                        : hours.findByStaffIdInOrderByWeekdayAscStartTimeAsc(active)).stream()
                .map(r -> new OpeningRange(r.weekday(), r.opens().format(HH_MM), r.closes().format(HH_MM)))
                .toList();
        return TenantView.of(t, payments.isDemo(), opening);
    }

    /** The customer-relevant part of the booking policy (for texts like "10 € deposit, free cancellation up to 24 h"). */
    public record BookingRules(int depositCents, int refundCutoffMin, int onlineCancelCutoffMin,
                               int barberResponseMin, int paymentDeadlineMin, int minLeadMin, int maxDaysAhead,
                               int slotStepMin, int loyaltyThreshold, int loyaltyPercent, boolean demoPayments) {}

    /** Public address and contact data; null fields are not set yet. */
    public record ProfileView(String street, String postalCode, String city, String country, String phone,
                              String email, String priceRange) {
        static ProfileView of(ShopProfile p) {
            return new ProfileView(p.getStreet(), p.getPostalCode(), p.getCity(), p.getCountry(), p.getPhone(),
                    p.getEmail(), p.getPriceRange());
        }
    }

    /** Open on ISO weekday (1 = Monday … 7 = Sunday) from opens to closes ("HH:mm", shop time). */
    public record OpeningRange(int weekday, String opens, String closes) {}

    public record TenantView(String slug, String name, String timezone, String defaultLocale, String currency,
                             String logoUrl, Map<String, Object> theme, BookingRules bookingRules,
                             ProfileView profile, List<OpeningRange> openingHours) {
        static TenantView of(Tenant t, boolean demo, List<OpeningRange> openingHours) {
            BookingPolicy p = t.policy();
            return new TenantView(t.getSlug(), t.getName(), t.getTimezone(), t.getDefaultLocale(), t.getCurrency(),
                    t.getLogoUrl(), t.getTheme(), new BookingRules(p.getDepositCents(), p.getRefundCutoffMin(),
                    p.getOnlineCancelCutoffMin(), p.getBarberResponseMin(), p.getPaymentDeadlineMin(),
                    p.getMinLeadMin(), p.getMaxDaysAhead(), p.getSlotStepMin(), p.getLoyaltyThreshold(),
                    p.getLoyaltyPercent(), demo), ProfileView.of(t.profile()), openingHours);
        }
    }
}
