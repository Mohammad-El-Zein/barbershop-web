package com.barbershop.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogSafeTest {

    @Test
    void tokensInPathsAreRedacted_entityIdsStay() {
        assertThat(LogSafe.path("/api/public/manage/Xk3_9fQ2LmZpR7tYvB1nC8wD4eHs/cancel"))
                .isEqualTo("/api/public/manage/***/cancel");
        assertThat(LogSafe.path("/api/public/payments/Xk3_9fQ2LmZpR7tYvB1nC8wD4eHs"))
                .isEqualTo("/api/public/payments/***");
        assertThat(LogSafe.path("/api/admin/bookings/3f2b1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d/confirm"))
                .isEqualTo("/api/admin/bookings/3f2b1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d/confirm");
        assertThat(LogSafe.path("/api/public/the-barber-news/services")).isEqualTo("/api/public/the-barber-news/services");
        assertThat(LogSafe.path(null)).isNull();
    }

    @Test
    void emailsInTextAreMasked() {
        assertThat(LogSafe.text("Key (lower(email))=(kim.meyer@example.com) already exists"))
                .isEqualTo("Key (lower(email))=(k***@example.com) already exists");
        assertThat(LogSafe.text("no address here")).isEqualTo("no address here");
    }
}
