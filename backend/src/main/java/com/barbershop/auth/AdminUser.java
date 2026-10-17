package com.barbershop.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A shop admin. Passwords are stored as BCrypt hashes only. */
@Entity
@Table(name = "admin_user")
public class AdminUser {

    public static final String ROLE_ADMIN = "ADMIN";

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected AdminUser() {}

    public AdminUser(UUID tenantId, String email, String passwordHash) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = ROLE_ADMIN;
        this.createdAt = Instant.now();
    }

    public void recordLogin(Instant now) {
        this.lastLoginAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }
    public Instant getLastLoginAt() { return lastLoginAt; }
}
