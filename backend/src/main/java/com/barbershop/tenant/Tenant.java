package com.barbershop.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A barbershop using the platform. Every other table is scoped by tenant_id. */
@Entity
@Table(name = "tenant")
public class Tenant {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    private String domain;

    /** IANA zone, e.g. Europe/Berlin. Working hours are interpreted in this zone. */
    @Column(nullable = false)
    private String timezone;

    @Column(name = "default_locale", nullable = false)
    private String defaultLocale;

    @Column(nullable = false)
    private String currency;

    @Column(name = "logo_url")
    private String logoUrl;

    /** Free-form theme settings for the frontend (colors etc.). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> theme = new HashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Booking rules of this shop (deadlines, deposit, loyalty, slot grid …). */
    @Embedded
    private BookingPolicy policy = new BookingPolicy();

    /** Public address / contact data (website, schema.org). Null when all its columns are empty. */
    @Embedded
    private ShopProfile profile = new ShopProfile();

    protected Tenant() {}

    public Tenant(UUID id, String name, String slug, String timezone) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.timezone = timezone;
        this.defaultLocale = "de";
        this.currency = "EUR";
        this.createdAt = Instant.now();
    }

    public ZoneId zone() {
        return ZoneId.of(timezone);
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getDomain() { return domain; }
    public String getTimezone() { return timezone; }
    public String getDefaultLocale() { return defaultLocale; }
    public String getCurrency() { return currency; }
    public String getLogoUrl() { return logoUrl; }
    public Map<String, Object> getTheme() { return theme; }
    public Instant getCreatedAt() { return createdAt; }
    public BookingPolicy policy() { return policy; }

    public ShopProfile profile() {
        if (profile == null) profile = new ShopProfile(); // Hibernate loads an all-NULL embeddable as null
        return profile;
    }
}
