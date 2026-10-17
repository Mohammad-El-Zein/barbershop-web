-- ─────────────────────────────────────────────────────────────────────────
-- Booking lead time 4 h (was 2 h): the barber always has time to answer
-- before the 2 h-before-start deadline. Shops still on the old default move
-- to the new one; customised values are kept.
--
-- Online cancellation by the customer is possible until 2 h before the
-- appointment; after that the API asks to contact the shop. The deposit
-- refund rule (24 h) is unchanged.
-- ─────────────────────────────────────────────────────────────────────────
ALTER TABLE tenant ALTER COLUMN min_lead_min SET DEFAULT 240;
UPDATE tenant SET min_lead_min = 240 WHERE min_lead_min = 120;

ALTER TABLE tenant
    ADD COLUMN online_cancel_cutoff_min integer NOT NULL DEFAULT 120 CHECK (online_cancel_cutoff_min >= 0);
