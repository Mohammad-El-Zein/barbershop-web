package com.barbershop.service;

import com.barbershop.service.PriceFormatter.CatalogPrice;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** API shapes of the service feature. */
public final class ServiceDtos {

    private ServiceDtos() {}

    /** Public, localized view (name/description in the request's language). */
    public record ServiceView(UUID id, String name, String description, int durationMin, CatalogPrice price) {}

    /** Admin view with both languages for editing. */
    public record ServiceAdminView(UUID id, String nameDe, String nameEn, String descriptionDe, String descriptionEn,
                                   int durationMin, int priceCents, boolean priceFrom, boolean active, int sortOrder) {

        static ServiceAdminView of(BarberService s) {
            return new ServiceAdminView(s.getId(), s.getNameDe(), s.getNameEn(), s.getDescriptionDe(),
                    s.getDescriptionEn(), s.getDurationMin(), s.getPriceCents(), s.isPriceFrom(), s.isActive(),
                    s.getSortOrder());
        }
    }

    public record ServiceRequest(
            @NotBlank(message = "{validation.required}") @Size(max = 120, message = "{validation.size.max}") String nameDe,
            @NotBlank(message = "{validation.required}") @Size(max = 120, message = "{validation.size.max}") String nameEn,
            @Size(max = 1000, message = "{validation.size.max}") String descriptionDe,
            @Size(max = 1000, message = "{validation.size.max}") String descriptionEn,
            @NotNull(message = "{validation.required}") @Min(value = 5, message = "{validation.min}")
            @Max(value = 480, message = "{validation.max}") Integer durationMin,
            @NotNull(message = "{validation.required}") @Min(value = 0, message = "{validation.min}") Integer priceCents,
            boolean priceFrom,
            Boolean active,
            Integer sortOrder) {}
}
