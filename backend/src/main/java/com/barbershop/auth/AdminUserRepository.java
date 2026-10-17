package com.barbershop.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminUserRepository extends JpaRepository<AdminUser, UUID> {

    /** Emails are unique case-insensitively (ux_admin_user_email on lower(email)). */
    @Query("select a from AdminUser a where lower(a.email) = lower(:email)")
    Optional<AdminUser> findByEmailIgnoreCase(@Param("email") String email);
}
