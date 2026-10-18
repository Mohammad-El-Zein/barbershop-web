-- ─────────────────────────────────────────────────────────────────────────
-- Public shop profile: postal address and contact data. Used for the website's
-- schema.org LocalBusiness data (JSON-LD), and editable by the shop admin.
-- All optional: a new tenant starts empty and fills it in the admin settings.
-- Opening hours are not stored here: they are derived from the barbers'
-- working hours (the shop is open while at least one active barber works).
-- ─────────────────────────────────────────────────────────────────────────
ALTER TABLE tenant
    ADD COLUMN profile_street      varchar(200),
    ADD COLUMN profile_postal_code varchar(20),
    ADD COLUMN profile_city        varchar(120),
    ADD COLUMN profile_country     varchar(2),
    ADD COLUMN profile_phone       varchar(40),
    ADD COLUMN profile_email       varchar(254),
    ADD COLUMN profile_price_range varchar(10);

ALTER TABLE tenant
    ADD CONSTRAINT tenant_profile_country_check CHECK (profile_country IS NULL OR profile_country ~ '^[A-Z]{2}$');
