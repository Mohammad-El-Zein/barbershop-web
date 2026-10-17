-- ─────────────────────────────────────────────────────────────────────────
-- Phase 2b: booking lifecycle (barber approval → deposit → confirmed),
-- customer/barber/admin accounts, single-use email tokens, demo payments,
-- loyalty. Phase 2 never left local development, so its simple `booking` and
-- `admin_user` tables are replaced instead of migrated (the bootstrap admin is
-- re-created from APP_ADMIN_EMAIL / APP_ADMIN_PASSWORD on startup).
-- ─────────────────────────────────────────────────────────────────────────

DROP TABLE booking;
DROP TABLE admin_user;

-- ── Booking policy per tenant (all durations in minutes) ────────────────
ALTER TABLE tenant
    ADD COLUMN barber_response_min        integer NOT NULL DEFAULT 720  CHECK (barber_response_min > 0),
    ADD COLUMN payment_deadline_min       integer NOT NULL DEFAULT 1440 CHECK (payment_deadline_min > 0),
    -- both deadlines end at the latest this long before the appointment
    ADD COLUMN deadline_before_start_min  integer NOT NULL DEFAULT 120  CHECK (deadline_before_start_min >= 0),
    ADD COLUMN deposit_cents              integer NOT NULL DEFAULT 1000 CHECK (deposit_cents >= 0),
    -- deposit refunded if the customer cancels at least this long before the appointment
    ADD COLUMN refund_cutoff_min          integer NOT NULL DEFAULT 1440 CHECK (refund_cutoff_min >= 0),
    ADD COLUMN loyalty_threshold          integer NOT NULL DEFAULT 10   CHECK (loyalty_threshold >= 1),
    ADD COLUMN loyalty_percent            integer NOT NULL DEFAULT 50   CHECK (loyalty_percent BETWEEN 0 AND 100),
    ADD COLUMN slot_step_min              integer NOT NULL DEFAULT 15   CHECK (slot_step_min BETWEEN 5 AND 120),
    ADD COLUMN buffer_min                 integer NOT NULL DEFAULT 0    CHECK (buffer_min BETWEEN 0 AND 120),
    ADD COLUMN min_lead_min               integer NOT NULL DEFAULT 120  CHECK (min_lead_min >= 0),
    ADD COLUMN max_days_ahead             integer NOT NULL DEFAULT 60   CHECK (max_days_ahead BETWEEN 1 AND 365),
    ADD COLUMN auto_complete_after_min    integer NOT NULL DEFAULT 120  CHECK (auto_complete_after_min >= 0),
    ADD COLUMN reminder_before_min        integer NOT NULL DEFAULT 1440 CHECK (reminder_before_min >= 0);

-- ── Accounts (customers, barbers, admins) ───────────────────────────────
CREATE TABLE app_user (
    id                 uuid PRIMARY KEY,
    tenant_id          uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    email              varchar(254) NOT NULL,
    password_hash      varchar(100) NOT NULL,
    role               varchar(16)  NOT NULL CHECK (role IN ('CUSTOMER', 'BARBER', 'ADMIN')),
    name               varchar(120) NOT NULL,
    phone              varchar(40),
    locale             varchar(5)   NOT NULL DEFAULT 'de' CHECK (locale IN ('de', 'en')),
    -- a BARBER account is linked to its staff profile
    staff_id           uuid UNIQUE REFERENCES staff (id) ON DELETE SET NULL,
    email_verified_at  timestamptz,
    consent_at         timestamptz,
    consent_version    varchar(40),
    failed_login_count integer      NOT NULL DEFAULT 0,
    locked_until       timestamptz,
    created_at         timestamptz  NOT NULL DEFAULT now(),
    last_login_at      timestamptz
);
-- one account per email and shop (case-insensitive)
CREATE UNIQUE INDEX ux_app_user_tenant_email ON app_user (tenant_id, lower(email));

-- Refresh tokens (httpOnly cookie). Rotated on every use; reuse of a rotated
-- token revokes its whole family (stolen-token detection). Only hashes stored.
CREATE TABLE refresh_token (
    id          uuid PRIMARY KEY,
    user_id     uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    family_id   uuid        NOT NULL,
    token_hash  varchar(64) NOT NULL UNIQUE,
    expires_at  timestamptz NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    revoked_at  timestamptz,
    replaced_by uuid
);
CREATE INDEX ix_refresh_token_user ON refresh_token (user_id);
CREATE INDEX ix_refresh_token_family ON refresh_token (family_id);

-- ── Bookings ────────────────────────────────────────────────────────────
CREATE TABLE booking (
    id                       uuid PRIMARY KEY,
    tenant_id                uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    staff_id                 uuid         NOT NULL REFERENCES staff (id),
    user_id                  uuid REFERENCES app_user (id) ON DELETE SET NULL,
    status                   varchar(20)  NOT NULL CHECK (status IN ('PENDING_BARBER', 'AWAITING_PAYMENT',
                                 'CONFIRMED', 'COMPLETED', 'DECLINED', 'EXPIRED', 'CANCELLED', 'NO_SHOW')),
    source                   varchar(10)  NOT NULL DEFAULT 'ONLINE' CHECK (source IN ('ONLINE', 'WALK_IN', 'PHONE')),
    start_time               timestamptz  NOT NULL,
    end_time                 timestamptz  NOT NULL,
    -- end + buffer: the barber is blocked until then
    blocked_until            timestamptz  NOT NULL,
    time_range               tstzrange GENERATED ALWAYS AS (tstzrange(start_time, blocked_until, '[)')) STORED,
    customer_name            varchar(120) NOT NULL,
    customer_email           varchar(254),
    customer_phone           varchar(40),
    locale                   varchar(5)   NOT NULL DEFAULT 'de' CHECK (locale IN ('de', 'en')),
    customer_note            varchar(500),
    consent_at               timestamptz,
    -- money (cents). price_total − loyalty_discount − deposit = amount_due_at_shop
    currency                 varchar(3)   NOT NULL,
    price_total_cents        integer      NOT NULL CHECK (price_total_cents >= 0),
    loyalty_discount_cents   integer      NOT NULL DEFAULT 0 CHECK (loyalty_discount_cents >= 0),
    deposit_cents            integer      NOT NULL CHECK (deposit_cents >= 0),
    amount_due_at_shop_cents integer      NOT NULL CHECK (amount_due_at_shop_cents >= 0),
    price_from               boolean      NOT NULL DEFAULT false,
    loyalty_status           varchar(10)  NOT NULL DEFAULT 'NONE'
                                 CHECK (loyalty_status IN ('NONE', 'RESERVED', 'RELEASED', 'CONSUMED')),
    barber_deadline_at       timestamptz,
    payment_deadline_at      timestamptz,
    accepted_at              timestamptz,
    confirmed_at             timestamptz,
    declined_at              timestamptz,
    expired_at               timestamptz,
    cancelled_at             timestamptz,
    completed_at             timestamptz,
    no_show_at               timestamptz,
    cancelled_by             varchar(10) CHECK (cancelled_by IN ('CUSTOMER', 'BARBER', 'ADMIN', 'SYSTEM')),
    status_reason            varchar(500),
    reminder_sent_at         timestamptz,
    created_at               timestamptz  NOT NULL DEFAULT now(),
    updated_at               timestamptz  NOT NULL DEFAULT now(),
    CHECK (end_time > start_time),
    CHECK (blocked_until >= end_time),
    CHECK (price_total_cents - loyalty_discount_cents - deposit_cents = amount_due_at_shop_cents),
    -- THE double-booking guard: a barber never has two overlapping bookings in a
    -- slot-blocking status, enforced by the database even under concurrency.
    -- DECLINED / EXPIRED / CANCELLED / NO_SHOW free the slot.
    CONSTRAINT booking_no_overlap
        EXCLUDE USING gist (staff_id WITH =, time_range WITH &&)
        WHERE (status IN ('PENDING_BARBER', 'AWAITING_PAYMENT', 'CONFIRMED', 'COMPLETED'))
);
CREATE INDEX ix_booking_tenant_start ON booking (tenant_id, start_time);
CREATE INDEX ix_booking_staff_start ON booking (staff_id, start_time);
CREATE INDEX ix_booking_user ON booking (user_id, start_time);
CREATE INDEX ix_booking_guest_email ON booking (tenant_id, lower(customer_email)) WHERE user_id IS NULL;
-- for the scheduled jobs
CREATE INDEX ix_booking_open_status ON booking (status, start_time)
    WHERE status IN ('PENDING_BARBER', 'AWAITING_PAYMENT', 'CONFIRMED');

-- The services of a booking, with name/duration/price frozen at booking time.
CREATE TABLE booking_item (
    id           uuid PRIMARY KEY,
    booking_id   uuid         NOT NULL REFERENCES booking (id) ON DELETE CASCADE,
    service_id   uuid         NOT NULL REFERENCES service (id),
    name_de      varchar(120) NOT NULL,
    name_en      varchar(120) NOT NULL,
    duration_min integer      NOT NULL CHECK (duration_min > 0),
    price_cents  integer      NOT NULL CHECK (price_cents >= 0),
    price_from   boolean      NOT NULL DEFAULT false,
    position     integer      NOT NULL
);
CREATE INDEX ix_booking_item_booking ON booking_item (booking_id);

-- ── Payments (deposit). Provider "demo" only for now. ───────────────────
CREATE TABLE payment (
    id           uuid PRIMARY KEY,
    tenant_id    uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    booking_id   uuid         NOT NULL UNIQUE REFERENCES booking (id) ON DELETE CASCADE,
    provider     varchar(20)  NOT NULL,
    provider_ref varchar(120) NOT NULL UNIQUE,
    amount_cents integer      NOT NULL CHECK (amount_cents > 0),
    currency     varchar(3)   NOT NULL,
    status       varchar(10)  NOT NULL CHECK (status IN ('UNPAID', 'PAID', 'REFUNDED', 'FORFEITED')),
    created_at   timestamptz  NOT NULL DEFAULT now(),
    paid_at      timestamptz,
    refunded_at  timestamptz,
    forfeited_at timestamptz,
    updated_at   timestamptz  NOT NULL DEFAULT now()
);

-- Audit trail of everything that happened to a payment (incl. simulated refunds).
CREATE TABLE payment_event (
    id                uuid PRIMARY KEY,
    payment_id        uuid        NOT NULL REFERENCES payment (id) ON DELETE CASCADE,
    type              varchar(32) NOT NULL,
    provider_event_id varchar(120) UNIQUE,
    actor             varchar(10),
    detail            varchar(500),
    created_at        timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_payment_event_payment ON payment_event (payment_id, created_at);

-- ── Single-use email-link tokens. Only a keyed hash is stored. ──────────
CREATE TABLE action_token (
    id         uuid PRIMARY KEY,
    tenant_id  uuid        NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    purpose    varchar(20) NOT NULL CHECK (purpose IN ('EMAIL_VERIFY', 'PASSWORD_RESET', 'BARBER_DECISION',
                               'BOOKING_MANAGE', 'PAYMENT')),
    token_hash varchar(64) NOT NULL UNIQUE,
    booking_id uuid REFERENCES booking (id) ON DELETE CASCADE,
    user_id    uuid REFERENCES app_user (id) ON DELETE CASCADE,
    payment_id uuid REFERENCES payment (id) ON DELETE CASCADE,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_action_token_booking ON action_token (booking_id, purpose);
CREATE INDEX ix_action_token_user ON action_token (user_id, purpose);

-- ── Loyalty (accounts only) ─────────────────────────────────────────────
CREATE TABLE loyalty_account (
    user_id             uuid PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    tenant_id           uuid        NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    visits_in_cycle     integer     NOT NULL DEFAULT 0 CHECK (visits_in_cycle >= 0),
    total_visits        integer     NOT NULL DEFAULT 0 CHECK (total_visits >= 0),
    rewards_used        integer     NOT NULL DEFAULT 0 CHECK (rewards_used >= 0),
    -- the booking currently holding the discount (reserved at creation)
    reserved_booking_id uuid UNIQUE REFERENCES booking (id) ON DELETE SET NULL,
    updated_at          timestamptz NOT NULL DEFAULT now()
);
