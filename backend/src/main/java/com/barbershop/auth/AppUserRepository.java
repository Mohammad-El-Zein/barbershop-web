package com.barbershop.auth;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    /** Emails are unique per tenant case-insensitively (ux_app_user_tenant_email). */
    @Query("select u from AppUser u where u.tenantId = :tenantId and lower(u.email) = lower(:email)")
    Optional<AppUser> findByTenantAndEmail(@Param("tenantId") UUID tenantId, @Param("email") String email);

    /** Row lock for the login counter (concurrent failed logins must all be counted). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.tenantId = :tenantId and lower(u.email) = lower(:email)")
    Optional<AppUser> findByTenantAndEmailForUpdate(@Param("tenantId") UUID tenantId, @Param("email") String email);

    Optional<AppUser> findByIdAndTenantId(UUID id, UUID tenantId);

    List<AppUser> findByStaffId(UUID staffId);

    List<AppUser> findByTenantIdAndRole(UUID tenantId, Role role);

    boolean existsByRole(Role role);
}
