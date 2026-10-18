package com.barbershop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.barbershop.payment.PaymentProvider;
import com.barbershop.support.AbstractIntegrationTest;
import com.barbershop.support.DbRoles;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Safety guard: the real application (same Postgres container) refuses to start
 * with the {@code prod} profile while payment.provider=demo — unless
 * payment.demo.allow-in-prod=true is set explicitly.
 */
class PaymentProviderGuardIT extends AbstractIntegrationTest {

    @Test
    void prodProfileWithTheDemoProviderDoesNotStart() {
        assertThatThrownBy(() -> start(false).close())
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Refusing to start")
                .hasMessageContaining("payment.demo.allow-in-prod=true");
    }

    @Test
    void prodProfileStartsWhenTheDemoIsAllowedExplicitly() {
        try (ConfigurableApplicationContext ctx = start(true)) {
            assertThat(ctx.getBean(PaymentProvider.class).isDemo()).isTrue();
            assertThat(ctx.getEnvironment().getActiveProfiles()).contains("prod");
        }
    }

    @Test
    void theNormalTestContextRunsTheDemoProvider(@org.springframework.beans.factory.annotation.Autowired
                                                 PaymentProvider provider) {
        assertThat(provider.id()).isEqualTo("demo");
    }

    private static ConfigurableApplicationContext start(boolean allowDemoInProd) {
        // command-line args: highest precedence (builder .properties() would lose against application.yml)
        return new SpringApplicationBuilder(BarbershopApplication.class)
                .profiles("prod", "demo")
                .run("--server.port=0",
                        "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        // like production: application role only, no migration (validate the schema)
                        "--spring.datasource.username=" + DbRoles.APP,
                        "--spring.datasource.password=" + DbRoles.APP_PASSWORD,
                        "--spring.flyway.user=" + DbRoles.APP,
                        "--spring.flyway.password=" + DbRoles.APP_PASSWORD,
                        "--app.db.migrate=false",
                        "--spring.mail.host=localhost",
                        "--spring.mail.port=" + MAIL.getSmtp().getPort(),
                        "--app.jwt.secret=" + UUID.randomUUID() + UUID.randomUUID(),
                        "--app.jobs.enabled=false",
                        // the other prod guards (ProductionSafetyChecks, captcha) are satisfied here: this test is about payments
                        "--app.frontend-base-url=https://shop.example",
                        "--app.cors.allowed-origins=https://shop.example",
                        "--app.auth.cookie-secure=true",
                        "--app.captcha.allow-none-in-prod=true",
                        "--payment.provider=demo",
                        "--payment.demo.allow-in-prod=" + allowDemoInProd);
    }
}
