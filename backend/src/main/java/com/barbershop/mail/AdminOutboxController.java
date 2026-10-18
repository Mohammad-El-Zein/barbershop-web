package com.barbershop.mail;

import com.barbershop.auth.CurrentUser;
import com.barbershop.common.config.OpenApiConfig;
import com.barbershop.common.error.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Email delivery problems of the shop: mails still retrying or given up (DEAD), with a manual retry. */
@RestController
@RequestMapping("/api/admin/outbox")
@Tag(name = "Admin: email delivery")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class AdminOutboxController {

    private final JdbcClient jdbc;
    private final MailOutbox outbox;

    public AdminOutboxController(JdbcClient jdbc, MailOutbox outbox) {
        this.jdbc = jdbc;
        this.outbox = outbox;
    }

    public record OutboxProblem(UUID id, String kind, String recipient, String subject, String status, int attempts,
                                String lastError, OffsetDateTime createdAt, OffsetDateTime nextAttemptAt,
                                UUID bookingId) {}

    @GetMapping
    @Operation(summary = "Mails that failed: RETRY (will be tried again) and DEAD (given up), newest first")
    public List<OutboxProblem> problems(CurrentUser admin) {
        return jdbc.sql("""
                        select id, kind, recipient, subject, status, attempts, last_error, created_at,
                               next_attempt_at, booking_id
                        from mail_outbox
                        where tenant_id = :tenant and status in ('RETRY', 'DEAD')
                        order by created_at desc
                        limit 100""")
                .param("tenant", admin.tenantId())
                .query((rs, i) -> new OutboxProblem(rs.getObject("id", UUID.class), rs.getString("kind"),
                        rs.getString("recipient"), rs.getString("subject"), rs.getString("status"),
                        rs.getInt("attempts"), rs.getString("last_error"), at(rs.getTimestamp("created_at")),
                        at(rs.getTimestamp("next_attempt_at")), rs.getObject("booking_id", UUID.class)))
                .list();
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "Send a failed mail again (fresh attempt budget)")
    public void retry(CurrentUser admin, @PathVariable("id") UUID id) {
        if (!outbox.retry(admin.tenantId(), id)) throw ApiException.notFound("outbox.not_found");
    }

    private static OffsetDateTime at(Timestamp t) {
        return t == null ? null : t.toInstant().atOffset(ZoneOffset.UTC);
    }
}
