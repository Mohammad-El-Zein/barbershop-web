package com.barbershop.staff;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** API shapes of the staff feature. */
public final class StaffDtos {

    private StaffDtos() {}

    /** Public, localized view. */
    public record StaffView(UUID id, String name, String photoUrl, String bio, List<UUID> serviceIds) {}

    /** Admin view with both bio languages. */
    public record StaffAdminView(UUID id, String name, String photoUrl, String bioDe, String bioEn, boolean active,
                                 int sortOrder, List<UUID> serviceIds) {}

    public record StaffRequest(
            @NotBlank(message = "{validation.required}") @Size(max = 120, message = "{validation.size.max}") String name,
            @Size(max = 500, message = "{validation.size.max}") String photoUrl,
            @Size(max = 1000, message = "{validation.size.max}") String bioDe,
            @Size(max = 1000, message = "{validation.size.max}") String bioEn,
            Boolean active,
            Integer sortOrder) {}

    public record StaffServicesRequest(@NotNull(message = "{validation.required}") Set<UUID> serviceIds) {}

    /** One working range; weekday ISO 1 = Monday … 7 = Sunday, local times ("09:00"). */
    public record WorkingHoursEntry(
            @NotNull(message = "{validation.required}") @Min(value = 1, message = "{validation.min}")
            @Max(value = 7, message = "{validation.max}") Integer weekday,
            @NotNull(message = "{validation.required}") LocalTime start,
            @NotNull(message = "{validation.required}") LocalTime end) {}

    /** Replaces the full weekly schedule of a staff member. */
    public record WorkingHoursRequest(@NotNull(message = "{validation.required}") List<@Valid WorkingHoursEntry> entries) {}

    public record TimeOffView(UUID id, UUID staffId, String staffName, OffsetDateTime start, OffsetDateTime end,
                              String reason) {}

    /** staffId null = whole shop closed. Times as ISO-8601 with offset. */
    public record TimeOffRequest(
            UUID staffId,
            @NotNull(message = "{validation.required}") OffsetDateTime start,
            @NotNull(message = "{validation.required}") OffsetDateTime end,
            @Size(max = 255, message = "{validation.size.max}") String reason) {}
}
