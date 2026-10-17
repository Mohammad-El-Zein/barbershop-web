-- ─────────────────────────────────────────────────────────────────────────
-- Read-only status link for the booking status page right after booking
-- ("waiting for the barber" → "accepted: pay deposit" → "confirmed"). Unlike the
-- other purposes it is not single-use; it only reads, and can mint a fresh
-- single-use payment link while the booking awaits payment.
-- ─────────────────────────────────────────────────────────────────────────
ALTER TABLE action_token DROP CONSTRAINT action_token_purpose_check;
ALTER TABLE action_token ADD CONSTRAINT action_token_purpose_check CHECK (purpose IN (
    'EMAIL_VERIFY', 'PASSWORD_RESET', 'BARBER_DECISION', 'BOOKING_MANAGE', 'PAYMENT', 'BOOKING_STATUS'));
