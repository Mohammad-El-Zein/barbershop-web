package com.barbershop.auth;

import com.barbershop.common.i18n.Lang;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * An account of a shop: customer, barber (linked to a staff profile) or admin.
 * Passwords are stored as BCrypt hashes only. Emails are unique per shop
 * (case-insensitive, index ux_app_user_tenant_email).
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private String name;

    private String phone;

    @Column(nullable = false)
    private String locale;

    @Column(name = "staff_id")
    private UUID staffId;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "consent_at")
    private Instant consentAt;

    @Column(name = "consent_version")
    private String consentVersion;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected AppUser() {}

    public AppUser(UUID tenantId, Role role, String email, String passwordHash, String name, String phone, Lang lang,
                   Instant now) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.role = role;
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.phone = phone;
        this.locale = lang.code();
        this.createdAt = now;
    }

    // ── login / lockout ──────────────────────────────────────────────────────

    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /** Counts a failed login; locks the account for {@code lockout} after {@code max} failures in a row. */
    public void recordFailedLogin(Instant now, int max, Duration lockout) {
        failedLoginCount++;
        if (failedLoginCount >= max) {
            lockedUntil = now.plus(lockout);
            failedLoginCount = 0;
        }
    }

    public void recordLogin(Instant now) {
        failedLoginCount = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    // ── profile ──────────────────────────────────────────────────────────────

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public void verifyEmail(Instant now) {
        if (emailVerifiedAt == null) emailVerifiedAt = now;
    }

    public void recordConsent(Instant now, String version) {
        this.consentAt = now;
        this.consentVersion = version;
    }

    public void changePassword(String newHash) {
        this.passwordHash = newHash;
        this.failedLoginCount = 0;
        this.lockedUntil = null;
    }

    public void linkStaff(UUID staffId) {
        this.staffId = staffId;
    }

    public Lang lang() {
        Lang lang = Lang.parse(locale);
        return lang != null ? lang : Lang.DEFAULT;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getLocale() { return locale; }
    public void setLang(Lang lang) { this.locale = lang.code(); }
    public UUID getStaffId() { return staffId; }
    public Instant getEmailVerifiedAt() { return emailVerifiedAt; }
    public Instant getConsentAt() { return consentAt; }
    public String getConsentVersion() { return consentVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
}
