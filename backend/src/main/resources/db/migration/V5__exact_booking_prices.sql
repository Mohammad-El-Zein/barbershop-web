-- ─────────────────────────────────────────────────────────────────────────
-- A booking's price is exact: the summary, payment page, My bookings and every
-- email show the amount without "ab"/"from". Only the service catalogue keeps
-- its "from" flag (barber_service.price_from) for the landing-page cards, so the
-- booking-side copies of that flag are no longer read or written.
-- ─────────────────────────────────────────────────────────────────────────
ALTER TABLE booking      DROP COLUMN price_from;
ALTER TABLE booking_item DROP COLUMN price_from;
