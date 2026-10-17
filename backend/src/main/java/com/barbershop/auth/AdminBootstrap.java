package com.barbershop.auth;

import com.barbershop.common.config.AppProperties;
import com.barbershop.tenant.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first admin from APP_ADMIN_EMAIL / APP_ADMIN_PASSWORD on startup
 * if it does not exist yet — credentials never live in the repository.
 * An existing admin is never modified (its password is not overwritten).
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    static final int MIN_PASSWORD_LENGTH = 12;

    private final AppProperties.AdminBootstrap config;
    private final AdminUserRepository admins;
    private final TenantRepository tenants;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrap(AppProperties props, AdminUserRepository admins, TenantRepository tenants,
                          PasswordEncoder passwordEncoder) {
        this.config = props.admin();
        this.admins = admins;
        this.tenants = tenants;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = config.email();
        String password = config.password();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.info("No bootstrap admin configured (APP_ADMIN_EMAIL / APP_ADMIN_PASSWORD not set)");
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            log.error("APP_ADMIN_PASSWORD must have at least {} characters — bootstrap admin NOT created",
                    MIN_PASSWORD_LENGTH);
            return;
        }
        if (admins.findByEmailIgnoreCase(email).isPresent()) {
            log.info("Bootstrap admin {} already exists (password unchanged)", email);
            return;
        }
        tenants.findBySlug(config.tenant()).ifPresentOrElse(tenant -> {
            admins.save(new AdminUser(tenant.getId(), email.trim(), passwordEncoder.encode(password)));
            log.info("Created bootstrap admin {} for tenant {}", email, tenant.getSlug());
        }, () -> log.warn("Bootstrap admin not created: tenant '{}' does not exist", config.tenant()));
    }
}
