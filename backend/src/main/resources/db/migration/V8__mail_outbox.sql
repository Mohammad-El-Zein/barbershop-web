-- ─────────────────────────────────────────────────────────────────────────
-- Transactional email outbox. Mails are rendered and stored in the SAME
-- transaction as the change that causes them (a booking request, an
-- acceptance, a password reset …): a mail exists exactly when the change
-- committed. A worker sends them after the commit, retries with exponential
-- backoff and marks them DEAD after the maximum number of attempts (visible to
-- the shop admin, who can retry). After a successful send the content is
-- cleared; only the delivery log (recipient, kind, times) stays until the
-- retention job deletes it.
-- ─────────────────────────────────────────────────────────────────────────
CREATE TABLE mail_outbox (
    id              uuid PRIMARY KEY,
    tenant_id       uuid REFERENCES tenant (id) ON DELETE CASCADE,
    kind            varchar(40)  NOT NULL,           -- requested, barber-requested, verify, reset …
    booking_id      uuid REFERENCES booking (id) ON DELETE SET NULL,
    user_id         uuid REFERENCES app_user (id) ON DELETE SET NULL,
    recipient       varchar(254) NOT NULL,
    subject         varchar(300) NOT NULL,
    lang            varchar(5)   NOT NULL,
    html            text,                             -- NULL once sent
    plain_text      text,
    attachments     jsonb,                            -- [{filename, contentType, data(base64)}]
    status          varchar(10)  NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING', 'SENDING', 'RETRY', 'SENT', 'DEAD')),
    attempts        integer      NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at timestamptz  NOT NULL DEFAULT now(),
    claimed_at      timestamptz,
    last_error      varchar(500),
    created_at      timestamptz  NOT NULL DEFAULT now(),
    sent_at         timestamptz
);

-- the worker's query: due PENDING / RETRY rows (and SENDING rows of a crashed worker)
CREATE INDEX ix_mail_outbox_due ON mail_outbox (next_attempt_at) WHERE status IN ('PENDING', 'RETRY', 'SENDING');
-- admin view of failures, retention
CREATE INDEX ix_mail_outbox_tenant_status ON mail_outbox (tenant_id, status, created_at);
