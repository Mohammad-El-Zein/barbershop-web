package com.barbershop.booking;

import com.barbershop.service.PriceFormatter.PriceView;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** API shapes of the booking feature. */
public final class BookingDtos {

    private BookingDtos() {}

    /**
     * staffId omitted = any barber (the server picks a free one).
     * start = a slot start from /availability (ISO-8601 with offset).
     * lang = language for emails; defaults to the request language.
     */
    public record CreateBookingRequest(
            @NotNull(message = "{validation.required}") UUID serviceId,
            UUID staffId,
            @NotNull(message = "{validation.required}") OffsetDateTime start,
            @NotBlank(message = "{validation.required}")
            @Size(min = 2, max = 120, message = "{validation.size}") String customerName,
            @NotBlank(message = "{validation.required}")
            @Pattern(regexp = "^\\+?[0-9 ()/-]{6,30}$", message = "{validation.phone}") String customerPhone,
            @NotBlank(message = "{validation.required}") @Email(message = "{validation.email}")
            @Size(max = 254, message = "{validation.size.max}") String customerEmail,
            @Pattern(regexp = "de|en", message = "{validation.lang}") String lang,
            @Size(max = 500, message = "{validation.size.max}") String notes) {}

    public record ServiceRef(UUID id, String name, int durationMin) {}

    public record StaffRef(UUID id, String name) {}

    /**
     * Customer view. cancelToken is only included in the creation response
     * (afterwards it only exists in the confirmation email).
     */
    public record BookingView(UUID id, BookingStatus status, ServiceRef service, StaffRef staff, OffsetDateTime start,
                              OffsetDateTime end, String timezone, String customerName, String customerEmail,
                              String lang, PriceView price, OffsetDateTime cancellableUntil, String cancelToken) {}

    public record BookingAdminView(UUID id, BookingStatus status, ServiceRef service, StaffRef staff,
                                   OffsetDateTime start, OffsetDateTime end, String customerName,
                                   String customerPhone, String customerEmail, String lang, String notes,
                                   OffsetDateTime createdAt, OffsetDateTime cancelledAt) {}

    public record PageView<T>(List<T> items, int page, int size, long totalItems, int totalPages) {}

    public record DayCount(LocalDate date, long booked, long cancelled) {}

    public record ServiceCount(UUID serviceId, String name, long booked, long cancelled) {}

    public record StatsView(LocalDate from, LocalDate to, String timezone, long totalBooked, long totalCancelled,
                            List<DayCount> perDay, List<ServiceCount> perService) {}
}
