package com.barbershop.auth;

import com.barbershop.common.config.AppProperties;
import com.barbershop.common.i18n.Lang;
import com.barbershop.tenant.Tenant;
import com.barbershop.tenant.TenantRepository;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * First start of a fresh installation:
 * <ol>
 *   <li>the shop (tenant APP_ADMIN_TENANT) is created when it does not exist and APP_TENANT_NAME
 *       is set — production runs without the demo seed; profile, services, barbers and hours are
 *       then maintained in the admin area;</li>
 *   <li>the first admin is created from APP_ADMIN_EMAIL / APP_ADMIN_PASSWORD if it does not exist
 *       yet — credentials never live in the repository.</li>
 * </ol>
 * Existing shops and accounts are never modified (an admin's password is not overwritten).
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    static final int MIN_PASSWORD_LENGTH = 12;
    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

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
        createTenantIfMissing();
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
                log.info("Bootstrap admin {} already exists (password unchanged)", masked(email));
                return;
            }
            Instant now = Instant.now(clock);
            AppUser admin = new AppUser(tenant.getId(), Role.ADMIN, email.trim(), passwordEncoder.encode(password),
                    "Admin", null, Lang.parse(tenant.getDefaultLocale()) != null
                            ? Lang.parse(tenant.getDefaultLocale()) : Lang.DEFAULT, now);
            admin.verifyEmail(now);
            users.save(admin);
            log.info("Created bootstrap admin {} for tenant {}", masked(email), tenant.getSlug());
        }, () -> log.warn("Bootstrap admin not created: tenant '{}' does not exist", config.tenant()));
    }

    private void createTenantIfMissing() {
        String name = config.tenantName();
        String slug = config.tenant();
        if (name == null || name.isBlank() || slug == null || tenants.findBySlug(slug).isPresent()) return;
        if (!SLUG.matcher(slug).matches() || slug.length() > 60) {
            log.error("APP_ADMIN_TENANT '{}' is not a valid slug (a-z, 0-9, dashes) — shop NOT created", slug);
            return;
        }
        String zone = config.tenantTimezone() == null || config.tenantTimezone().isBlank()
                ? "Europe/Berlin" : config.tenantTimezone().trim();
        try {
            ZoneId.of(zone);
        } catch (DateTimeException ex) {
            log.error("APP_TENANT_TIMEZONE '{}' is not an IANA time zone — shop NOT created", zone);
            return;
        }
        tenants.save(new Tenant(UUID.randomUUID(), name.trim(), slug, zone));
        log.info("Created shop '{}' ({}, {}) — add services, barbers and opening hours in the admin area",
                name.trim(), slug, zone);
    }

    /** "a***@example.com": enough to recognise the account, no full address in the logs. */
    static String masked(String email) {
        String e = email.trim();
        int at = e.indexOf('@');
        return at <= 0 ? "***" : e.charAt(0) + "***" + e.substring(at);
    }
}
