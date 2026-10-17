package com.barbershop.account;

import com.barbershop.auth.AuthDtos.UserView;
import com.barbershop.booking.BookingDtos.BookingView;
import com.barbershop.loyalty.LoyaltyService.LoyaltyView;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** API shapes of the account feature. */
public final class AccountDtos {

    private AccountDtos() {}

    static final String PHONE = "^\\+?[0-9 ()/-]{6,30}$";

    public record RegisterRequest(
            @NotBlank(message = "{validation.required}") @Email(message = "{validation.email}")
            @Size(max = 254, message = "{validation.size.max}") String email,
            @NotBlank(message = "{validation.required}") @Size(max = 128, message = "{validation.size.max}") String password,
            @NotBlank(message = "{validation.required}") @Size(min = 2, max = 120, message = "{validation.size}") String name,
            @NotBlank(message = "{validation.required}") @Pattern(regexp = PHONE, message = "{validation.phone}") String phone,
            @Pattern(regexp = "de|en", message = "{validation.lang}") String lang,
            @NotNull(message = "{validation.consent}") @AssertTrue(message = "{validation.consent}") Boolean consent) {}

    public record EmailRequest(
            @NotBlank(message = "{validation.required}") @Email(message = "{validation.email}")
            @Size(max = 254, message = "{validation.size.max}") String email) {}

    public record TokenRequest(@NotBlank(message = "{validation.required}") @Size(max = 100) String token) {}

    public record ResetPasswordRequest(
            @NotBlank(message = "{validation.required}") @Size(max = 100) String token,
            @NotBlank(message = "{validation.required}") @Size(max = 128, message = "{validation.size.max}")
            String newPassword) {}

    public record ChangePasswordRequest(
            @NotBlank(message = "{validation.required}") @Size(max = 200) String currentPassword,
            @NotBlank(message = "{validation.required}") @Size(max = 128, message = "{validation.size.max}")
            String newPassword) {}

    public record PasswordConfirmation(@NotBlank(message = "{validation.required}") @Size(max = 200) String password) {}

    public record UpdateProfileRequest(
            @Size(min = 2, max = 120, message = "{validation.size}") String name,
            @Pattern(regexp = PHONE, message = "{validation.phone}") String phone,
            @Pattern(regexp = "de|en", message = "{validation.lang}") String lang) {}

    /** Always the same answer, whether the email exists or not (no user enumeration). */
    public record AcceptedResponse(String message) {}

    public record VerifyResult(boolean verified, int linkedGuestBookings) {}

    public record ExportView(Instant exportedAt, UserView account, Instant consentAt, String consentVersion,
                             LoyaltyView loyalty, List<BookingView> bookings, List<PaymentExport> payments) {}

    public record PaymentExport(UUID bookingId, String status, int amountCents, String currency, Instant paidAt,
                                Instant refundedAt, Instant forfeitedAt) {}

    public record BarberAccountRequest(
            @NotBlank(message = "{validation.required}") @Email(message = "{validation.email}")
            @Size(max = 254, message = "{validation.size.max}") String email,
            @Pattern(regexp = "de|en", message = "{validation.lang}") String lang) {}

    public record BarberAccountView(UUID userId, UUID staffId, String email, String name, boolean inviteSent) {}

    /** registered=false: guest bookings grouped by email address. */
    public record CustomerRow(UUID userId, boolean registered, String name, String email, String phone,
                              long completedVisits, int visitsInCurrentCycle, boolean nextDiscountAvailable,
                              long totalBookings, OffsetDateTime lastBookingAt) {}
}
