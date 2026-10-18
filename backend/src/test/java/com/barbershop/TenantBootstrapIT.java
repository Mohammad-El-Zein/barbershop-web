package com.barbershop;

import static org.assertj.core.api.Assertions.assertThat;

import com.barbershop.auth.AdminBootstrap;
import com.barbershop.auth.AppUserRepository;
import com.barbershop.common.config.AppProperties;
import com.barbershop.support.AbstractIntegrationTest;
import com.barbershop.tenant.TenantRepository;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

/** Production first start without the demo seed: APP_TENANT_NAME creates the shop, then the first admin. */
class TenantBootstrapIT extends AbstractIntegrationTest {

    private static final String SLUG = "salon-nord";

    @Autowired private AppProperties props;
    @Autowired private AppUserRepository users;
    @Autowired private TenantRepository tenants;
    @Autowired private PasswordEncoder encoder;
    @Autowired private Clock clock;
    @Autowired private TransactionTemplate tx;

    @AfterEach
    void removeShop() {
        jdbc.update("delete from tenant where slug = ?", SLUG); // cascades its users
    }

    private void bootstrap(String slug, String name, String zone) {
        AppProperties p = new AppProperties(props.frontendBaseUrl(), props.cors(), props.jwt(), props.auth(),
                props.tokens(), props.links(),
                new AppProperties.AdminBootstrap("owner@salon-nord.example", "a-long-owner-password", slug, name, zone),
                props.mail(), props.booking(), props.jobs(), props.rateLimit(), props.captcha(), props.retention());
        AdminBootstrap bootstrap = new AdminBootstrap(p, users, tenants, encoder, clock);
        tx.executeWithoutResult(s -> bootstrap.run(null));
    }

    @Test
    void createsTheShopAndItsFirstAdmin_secondStartChangesNothing() {
        bootstrap(SLUG, "Salon Nord", "Europe/Vienna");
        bootstrap(SLUG, "Other Name", "Europe/Berlin"); // idempotent: the existing shop is kept

        Map<String, Object> shop = jdbc.queryForMap(
                "select name, timezone, default_locale, deposit_cents, slot_step_min from tenant where slug = ?", SLUG);
        assertThat(shop).containsEntry("name", "Salon Nord").containsEntry("timezone", "Europe/Vienna")
                .containsEntry("default_locale", "de").containsEntry("deposit_cents", 1000)
                .containsEntry("slot_step_min", 15);
        assertThat(jdbc.queryForObject("""
                select count(*) from app_user u join tenant t on t.id = u.tenant_id
                where t.slug = ? and u.role = 'ADMIN' and u.email_verified_at is not null""", Long.class, SLUG))
                .isEqualTo(1);
        // the public API knows the new shop (empty catalogue until the admin adds services)
        assertThat(get("/api/public/" + SLUG, "de").getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void invalidSlugOrZone_noShop() {
        bootstrap("Salon Nord!", "Salon Nord", "Europe/Berlin");
        bootstrap(SLUG, "Salon Nord", "Mars/Olympus");
        assertThat(jdbc.queryForObject("select count(*) from tenant where slug in (?, ?)", Long.class,
                SLUG, "Salon Nord!")).isZero();
    }
}
