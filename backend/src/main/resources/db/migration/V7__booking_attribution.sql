-- ─────────────────────────────────────────────────────────────────────────
-- Marketing attribution of online bookings: the first-touch campaign data the
-- website captured (UTM parameters, Google/Meta click ids, landing page). Only
-- sent by the browser when the visitor gave MARKETING consent; always optional.
-- Used for the admin campaign stats ("which ad produced which booking").
-- ─────────────────────────────────────────────────────────────────────────
ALTER TABLE booking
    ADD COLUMN attr_source       varchar(100),
    ADD COLUMN attr_medium       varchar(100),
    ADD COLUMN attr_campaign     varchar(150),
    ADD COLUMN attr_term         varchar(150),
    ADD COLUMN attr_content      varchar(150),
    ADD COLUMN attr_gclid        varchar(255),
    ADD COLUMN attr_fbclid       varchar(255),
    ADD COLUMN attr_landing_path varchar(300),
    ADD COLUMN attr_captured_at  timestamptz;

-- campaign stats filter by creation date
CREATE INDEX ix_booking_tenant_created ON booking (tenant_id, created_at);
