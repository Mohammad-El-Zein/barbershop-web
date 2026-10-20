-- ─────────────────────────────────────────────────────────────────────────
-- A start password set or reset by the shop admin is temporary: the barber
-- has to choose an own one before working in the staff area. Only the hash of
-- any password is ever stored (password_hash).
-- ─────────────────────────────────────────────────────────────────────────
ALTER TABLE app_user ADD COLUMN must_change_password boolean NOT NULL DEFAULT false;
