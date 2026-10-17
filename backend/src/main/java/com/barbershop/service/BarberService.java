package com.barbershop.service;

import com.barbershop.common.i18n.Lang;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * A bookable service ("Haircut"). Named BarberService to avoid clashing with
 * Spring's @Service. Customer-facing texts exist in German and English.
 */
@Entity
@Table(name = "service")
public class BarberService {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "name_de", nullable = false)
    private String nameDe;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(name = "description_de")
    private String descriptionDe;

    @Column(name = "description_en")
    private String descriptionEn;

    @Column(name = "duration_min", nullable = false)
    private int durationMin;

    @Column(name = "price_cents", nullable = false)
    private int priceCents;

    /** Displayed as "from €35" / "ab 35 €". */
    @Column(name = "price_from", nullable = false)
    private boolean priceFrom;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BarberService() {}

    public BarberService(UUID tenantId) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
    }

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public String name(Lang lang) {
        return lang.pick(nameDe, nameEn);
    }

    public String description(Lang lang) {
        return lang.pick(descriptionDe, descriptionEn);
    }

    public Duration duration() {
        return Duration.ofMinutes(durationMin);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getNameDe() { return nameDe; }
    public void setNameDe(String nameDe) { this.nameDe = nameDe; }
    public String getNameEn() { return nameEn; }
    public void setNameEn(String nameEn) { this.nameEn = nameEn; }
    public String getDescriptionDe() { return descriptionDe; }
    public void setDescriptionDe(String descriptionDe) { this.descriptionDe = descriptionDe; }
    public String getDescriptionEn() { return descriptionEn; }
    public void setDescriptionEn(String descriptionEn) { this.descriptionEn = descriptionEn; }
    public int getDurationMin() { return durationMin; }
    public void setDurationMin(int durationMin) { this.durationMin = durationMin; }
    public int getPriceCents() { return priceCents; }
    public void setPriceCents(int priceCents) { this.priceCents = priceCents; }
    public boolean isPriceFrom() { return priceFrom; }
    public void setPriceFrom(boolean priceFrom) { this.priceFrom = priceFrom; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
