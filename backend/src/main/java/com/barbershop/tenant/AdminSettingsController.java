package com.barbershop.tenant;

import com.barbershop.auth.CurrentUser;
import com.barbershop.common.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The shop's booking rules (deadlines, deposit, refund cutoff, loyalty, slot grid …) and public profile. */
@RestController
@RequestMapping("/api/admin/settings")
@Tag(name = "Admin: settings")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class AdminSettingsController {

    private final TenantRepository tenants;

    public AdminSettingsController(TenantRepository tenants) {
        this.tenants = tenants;
    }

    @GetMapping
    public PolicyDto get(CurrentUser admin) {
        return PolicyDto.of(tenants.findById(admin.tenantId()).orElseThrow().policy());
    }

    @PutMapping
    @Transactional
    @Operation(summary = "Replace the booking policy (all values in minutes / cents / percent)")
    public PolicyDto update(CurrentUser admin, @Valid @RequestBody PolicyDto req) {
        BookingPolicy p = tenants.findById(admin.tenantId()).orElseThrow().policy();
        p.setBarberResponseMin(req.barberResponseMin());
        p.setPaymentDeadlineMin(req.paymentDeadlineMin());
        p.setDeadlineBeforeStartMin(req.deadlineBeforeStartMin());
        p.setDepositCents(req.depositCents());
        p.setRefundCutoffMin(req.refundCutoffMin());
        p.setOnlineCancelCutoffMin(req.onlineCancelCutoffMin());
        p.setLoyaltyThreshold(req.loyaltyThreshold());
        p.setLoyaltyPercent(req.loyaltyPercent());
        p.setSlotStepMin(req.slotStepMin());
        p.setBufferMin(req.bufferMin());
        p.setMinLeadMin(req.minLeadMin());
        p.setMaxDaysAhead(req.maxDaysAhead());
        p.setAutoCompleteAfterMin(req.autoCompleteAfterMin());
        p.setReminderBeforeMin(req.reminderBeforeMin());
        return PolicyDto.of(p);
    }

    @GetMapping("/profile")
    @Operation(summary = "The public shop profile (address, contact) used on the website and in its structured data")
    public ProfileDto profile(CurrentUser admin) {
        return ProfileDto.of(tenants.findById(admin.tenantId()).orElseThrow().profile());
    }

    @PutMapping("/profile")
    @Transactional
    @Operation(summary = "Replace the public shop profile (empty strings clear a field)")
    public ProfileDto updateProfile(CurrentUser admin, @Valid @RequestBody ProfileDto req) {
        ShopProfile p = tenants.findById(admin.tenantId()).orElseThrow().profile();
        p.setStreet(blankToNull(req.street()));
        p.setPostalCode(blankToNull(req.postalCode()));
        p.setCity(blankToNull(req.city()));
        p.setCountry(blankToNull(req.country()));
        p.setPhone(blankToNull(req.phone()));
        p.setEmail(blankToNull(req.email()));
        p.setPriceRange(blankToNull(req.priceRange()));
        return ProfileDto.of(p);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public record ProfileDto(
            @Size(max = 200) String street,
            @Size(max = 20) String postalCode,
            @Size(max = 120) String city,
            @Pattern(regexp = "^$|^[A-Z]{2}$", message = "{validation.country}") String country,
            @Pattern(regexp = "^$|^\\+?[0-9 ()/-]{6,30}$", message = "{validation.phone}") String phone,
            @Email @Size(max = 254) String email,
            @Pattern(regexp = "^$|^€{1,4}$|^\\${1,4}$", message = "{validation.price_range}") String priceRange) {

        static ProfileDto of(ShopProfile p) {
            return new ProfileDto(p.getStreet(), p.getPostalCode(), p.getCity(), p.getCountry(), p.getPhone(),
                    p.getEmail(), p.getPriceRange());
        }
    }

    public record PolicyDto(
            @NotNull @Min(1) Integer barberResponseMin,
            @NotNull @Min(1) Integer paymentDeadlineMin,
            @NotNull @Min(0) Integer deadlineBeforeStartMin,
            @NotNull @Min(0) Integer depositCents,
            @NotNull @Min(0) Integer refundCutoffMin,
            @NotNull @Min(0) Integer onlineCancelCutoffMin,
            @NotNull @Min(1) Integer loyaltyThreshold,
            @NotNull @Min(0) @Max(100) Integer loyaltyPercent,
            @NotNull @Min(5) @Max(120) Integer slotStepMin,
            @NotNull @Min(0) @Max(120) Integer bufferMin,
            @NotNull @Min(0) Integer minLeadMin,
            @NotNull @Min(1) @Max(365) Integer maxDaysAhead,
            @NotNull @Min(0) Integer autoCompleteAfterMin,
            @NotNull @Min(0) Integer reminderBeforeMin) {

        static PolicyDto of(BookingPolicy p) {
            return new PolicyDto(p.getBarberResponseMin(), p.getPaymentDeadlineMin(), p.getDeadlineBeforeStartMin(),
                    p.getDepositCents(), p.getRefundCutoffMin(), p.getOnlineCancelCutoffMin(), p.getLoyaltyThreshold(),
                    p.getLoyaltyPercent(),
                    p.getSlotStepMin(), p.getBufferMin(), p.getMinLeadMin(), p.getMaxDaysAhead(),
                    p.getAutoCompleteAfterMin(), p.getReminderBeforeMin());
        }
    }
}
