-- ─────────────────────────────────────────────────────────────────────────
-- Barbershop booking schema. Multi-tenant ready: every table carries tenant_id.
-- All instants are timestamptz (stored as UTC); working hours are local times
-- in the tenant's timezone.
-- ─────────────────────────────────────────────────────────────────────────

-- needed for the "no double booking" exclusion constraint (uuid = combined with range &&)
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE tenant (
    id             uuid PRIMARY KEY,
    name           varchar(120) NOT NULL,
    slug           varchar(60)  NOT NULL UNIQUE,
    domain         varchar(255) UNIQUE,
    timezone       varchar(64)  NOT NULL DEFAULT 'Europe/Berlin',
    default_locale varchar(5)   NOT NULL DEFAULT 'de',
    currency       varchar(3)   NOT NULL DEFAULT 'EUR',
    logo_url       varchar(500),
    theme          jsonb        NOT NULL DEFAULT '{}'::jsonb,
    created_at     timestamptz  NOT NULL DEFAULT now()
);

-- A bookable service ("Haircut"). Customer-facing texts in DE and EN.
CREATE TABLE service (
    id             uuid PRIMARY KEY,
    tenant_id      uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    name_de        varchar(120) NOT NULL,
    name_en        varchar(120) NOT NULL,
    description_de varchar(1000),
    description_en varchar(1000),
    duration_min   integer      NOT NULL CHECK (duration_min BETWEEN 5 AND 480),
    price_cents    integer      NOT NULL CHECK (price_cents >= 0),
    price_from     boolean      NOT NULL DEFAULT false, -- "from €35"
    active         boolean      NOT NULL DEFAULT true,
    sort_order     integer      NOT NULL DEFAULT 0,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX ix_service_tenant ON service (tenant_id, active, sort_order);

CREATE TABLE staff (
    id         uuid PRIMARY KEY,
    tenant_id  uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    name       varchar(120) NOT NULL,
    photo_url  varchar(500),
    bio_de     varchar(1000),
    bio_en     varchar(1000),
    active     boolean      NOT NULL DEFAULT true,
    sort_order integer      NOT NULL DEFAULT 0,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX ix_staff_tenant ON staff (tenant_id, active, sort_order);

-- Which staff member offers which service.
CREATE TABLE staff_service (
    tenant_id  uuid NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    staff_id   uuid NOT NULL REFERENCES staff (id) ON DELETE CASCADE,
    service_id uuid NOT NULL REFERENCES service (id) ON DELETE CASCADE,
    PRIMARY KEY (staff_id, service_id)
);
CREATE INDEX ix_staff_service_service ON staff_service (service_id);

-- Weekly working hours per staff member (ISO weekday 1 = Monday … 7 = Sunday).
-- Several ranges per day are allowed (e.g. a lunch break between two ranges).
CREATE TABLE working_hours (
    id         uuid PRIMARY KEY,
    tenant_id  uuid    NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    staff_id   uuid    NOT NULL REFERENCES staff (id) ON DELETE CASCADE,
    weekday    integer NOT NULL CHECK (weekday BETWEEN 1 AND 7),
    start_time time    NOT NULL,
    end_time   time    NOT NULL,
    CHECK (end_time > start_time)
);
CREATE INDEX ix_working_hours_staff ON working_hours (staff_id, weekday);

-- Absences / blocked slots. staff_id NULL = the whole shop is closed (holiday).
CREATE TABLE time_off (
    id         uuid PRIMARY KEY,
    tenant_id  uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    staff_id   uuid REFERENCES staff (id) ON DELETE CASCADE,
    start_at   timestamptz  NOT NULL,
    end_at     timestamptz  NOT NULL,
    reason     varchar(255),
    created_at timestamptz  NOT NULL DEFAULT now(),
    CHECK (end_at > start_at)
);
CREATE INDEX ix_time_off_lookup ON time_off (tenant_id, start_at, end_at);

CREATE TABLE booking (
    id                uuid PRIMARY KEY,
    tenant_id         uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    service_id        uuid         NOT NULL REFERENCES service (id),
    staff_id          uuid         NOT NULL REFERENCES staff (id),
    start_time        timestamptz  NOT NULL,
    end_time          timestamptz  NOT NULL,
    -- derived by the database, so it can never drift from start/end
    time_range        tstzrange GENERATED ALWAYS AS (tstzrange(start_time, end_time, '[)')) STORED,
    customer_name     varchar(120) NOT NULL,
    customer_phone    varchar(40)  NOT NULL,
    customer_email    varchar(254) NOT NULL,
    -- language the customer booked in; confirmation/cancellation mails use it
    locale            varchar(5)   NOT NULL DEFAULT 'de' CHECK (locale IN ('de', 'en')),
    status            varchar(16)  NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED')),
    -- SHA-256 of the cancel token; the raw token only exists in the customer's email/response
    cancel_token_hash varchar(64)  NOT NULL UNIQUE,
    notes             varchar(500),
    created_at        timestamptz  NOT NULL DEFAULT now(),
    cancelled_at      timestamptz,
    CHECK (end_time > start_time),
    -- THE double-booking guard: one staff member can never have two active,
    -- overlapping bookings — enforced by the database even under concurrency.
    CONSTRAINT booking_no_overlap
        EXCLUDE USING gist (staff_id WITH =, time_range WITH &&)
        WHERE (status <> 'CANCELLED')
);
CREATE INDEX ix_booking_tenant_start ON booking (tenant_id, start_time);
CREATE INDEX ix_booking_staff_start ON booking (staff_id, start_time);

CREATE TABLE admin_user (
    id            uuid PRIMARY KEY,
    tenant_id     uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    email         varchar(254) NOT NULL,
    password_hash varchar(100) NOT NULL,
    role          varchar(20)  NOT NULL DEFAULT 'ADMIN' CHECK (role IN ('ADMIN')),
    created_at    timestamptz  NOT NULL DEFAULT now(),
    last_login_at timestamptz
);
CREATE UNIQUE INDEX ux_admin_user_email ON admin_user (lower(email));
