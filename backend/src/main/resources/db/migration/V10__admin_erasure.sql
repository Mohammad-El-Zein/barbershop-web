-- ─────────────────────────────────────────────────────────────────────────
-- Admin erasure (com.barbershop.erasure.ErasureService): bookings, barbers,
-- services and a customer's history can be deleted without losing money records.
--
-- * A booking with money movement (payment PAID / REFUNDED / FORFEITED) is not
--   deleted — that would cascade its payment and payment_event rows. It is
--   anonymised and marked deleted_at: hidden everywhere, the payment ledger stays.
--   Bookings without money movement are deleted for real.
-- * Such an anonymised booking may outlive its barber and its services: staff_id
--   and booking_item.service_id become nullable. The erasure sets them to NULL
--   explicitly before deleting the barber / service (no cascade, on purpose);
--   a live booking always keeps its barber (CHECK below).
-- * admin_audit: who deleted what and when — ids and counts only, no personal data.
-- Idempotent (IF [NOT] EXISTS), so a re-run is harmless.
-- ─────────────────────────────────────────────────────────────────────────

ALTER TABLE booking ADD COLUMN IF NOT EXISTS deleted_at timestamptz;

ALTER TABLE booking ALTER COLUMN staff_id DROP NOT NULL;
ALTER TABLE booking DROP CONSTRAINT IF EXISTS booking_staff_unless_deleted;
ALTER TABLE booking ADD CONSTRAINT booking_staff_unless_deleted CHECK (staff_id IS NOT NULL OR deleted_at IS NOT NULL);

ALTER TABLE booking_item ALTER COLUMN service_id DROP NOT NULL;

-- deleting a service looks up the bookings that contain it
CREATE INDEX IF NOT EXISTS ix_booking_item_service ON booking_item (service_id);

CREATE TABLE IF NOT EXISTS admin_audit (
    id                  uuid PRIMARY KEY,
    tenant_id           uuid        NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    actor_user_id       uuid REFERENCES app_user (id) ON DELETE SET NULL,
    action              varchar(40) NOT NULL,
    target_type         varchar(20) NOT NULL,
    -- the deleted booking / barber / service, or the customer's account; NULL for a guest
    -- (identified only by email, which is personal data)
    target_id           uuid,
    bookings_deleted    integer     NOT NULL DEFAULT 0 CHECK (bookings_deleted >= 0),
    bookings_anonymised integer     NOT NULL DEFAULT 0 CHECK (bookings_anonymised >= 0),
    created_at          timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_admin_audit_tenant ON admin_audit (tenant_id, created_at);
