package com.barbershop.auth;

import com.barbershop.common.config.AppProperties;
import com.barbershop.common.i18n.Lang;
import com.barbershop.tenant.TenantRepository;
import java.time.Clock;
import java.time.Instant;
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
 * An existing account is never modified (its password is not overwritten).
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    static final int MIN_PASSWORD_LENGTH = 12;

    private final AppProperties.AdminBootstrap config;
    private final AppUserRepository users;
    private final TenantRepository tenants;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AdminBootstrap(AppProperties props, AppUserRepository users, TenantRepository tenants,
                          PasswordEncoder passwordEncoder, Clock clock) {
        this.config = props.admin();
        this.users = users;
        this.tenants = tenants;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
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
        tenants.findBySlug(config.tenant()).ifPresentOrElse(tenant -> {
            if (users.findByTenantAndEmail(tenant.getId(), email.trim()).isPresent()) {
                log.info("Bootstrap admin {} already exists (password unchanged)", email);
                return;
            }
            Instant now = Instant.now(clock);
            AppUser admin = new AppUser(tenant.getId(), Role.ADMIN, email.trim(), passwordEncoder.encode(password),
                    "Admin", null, Lang.parse(tenant.getDefaultLocale()) != null
                            ? Lang.parse(tenant.getDefaultLocale()) : Lang.DEFAULT, now);
            admin.verifyEmail(now);
            users.save(admin);
            log.info("Created bootstrap admin {} for tenant {}", email, tenant.getSlug());
        }, () -> log.warn("Bootstrap admin not created: tenant '{}' does not exist", config.tenant()));
    }
}
