package com.barbershop.staff;

import com.barbershop.common.i18n.Lang;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A barber. The bio is customer-facing and exists in German and English. */
@Entity
@Table(name = "staff")
public class Staff {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Column(name = "photo_url")
    private String photoUrl;

    @Column(name = "bio_de")
    private String bioDe;

    @Column(name = "bio_en")
    private String bioEn;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Staff() {}

    public Staff(UUID tenantId) {
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

    public String bio(Lang lang) {
        return lang.pick(bioDe, bioEn);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
    public String getBioDe() { return bioDe; }
    public void setBioDe(String bioDe) { this.bioDe = bioDe; }
    public String getBioEn() { return bioEn; }
    public void setBioEn(String bioEn) { this.bioEn = bioEn; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
