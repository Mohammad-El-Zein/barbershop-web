"use client";

import { useEffect, useState } from "react";
import { useTranslations } from "next-intl";
import type { DeletionPreview, DeletionResult } from "@/lib/api/types";
import { useErrorText } from "@/lib/api/useErrorText";
import { ConfirmDialog } from "../ui/Dialog";
import { Alert } from "../ui/Feedback";

/**
 * Confirmation for deleting a barber, a service or a customer's history. When it opens it asks
 * the server what would happen (preview: bookings deleted / anonymised, or what blocks it) and
 * shows the numbers; the delete button only works when the server allows it. The server checks
 * again inside the deleting transaction, so a stale preview can't do harm.
 *
 * {@code mode}: "blocking" = barber / service (active bookings or history block everything);
 * "history" = a customer (active bookings stay, the rest goes).
 */
export function DeletionDialog({
  open,
  title,
  intro,
  mode,
  loadPreview,
  runDelete,
  onClose,
  onDone,
}: {
  open: boolean;
  title: string;
  intro: string;
  mode: "blocking" | "history";
  loadPreview: () => Promise<DeletionPreview | undefined>;
  runDelete: () => Promise<DeletionResult | undefined>;
  onClose: () => void;
  onDone: (result: DeletionResult) => void;
}) {
  const t = useTranslations("staff.erasure");
  const errorText = useErrorText();
  const [preview, setPreview] = useState<DeletionPreview | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!open) return;
    loadPreview().then((p) => setPreview(p ?? null), setError);
    // the loader is a new closure on every render; reload only when the dialog opens
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  // reset on the way out, so the next opening starts with "checking …"
  const close = () => {
    setPreview(null);
    setError(null);
    onClose();
  };

  const deleted = preview?.deleted ?? 0;
  const anonymised = preview?.anonymised ?? 0;
  const active = preview?.active ?? 0;
  const history = preview?.history ?? 0;

  return (
    <ConfirmDialog
      open={open}
      title={title}
      confirmLabel={t("delete")}
      danger
      busy={busy}
      confirmDisabled={!preview?.allowed}
      onConfirm={async () => {
        setBusy(true);
        setError(null);
        try {
          const r = await runDelete();
          setPreview(null);
          onDone(r ?? { deleted: 0, anonymised: 0, keptActive: 0 });
        } catch (e) {
          setError(e);
        } finally {
          setBusy(false);
        }
      }}
      onClose={close}
    >
      <p>{intro}</p>
      {!preview && !error && (
        <p role="status" className="text-muted">
          {t("checking")}
        </p>
      )}
      {preview && (
        <div role="status" className="space-y-2">
          {mode === "blocking" && !preview.allowed ? (
            <>
              {active > 0 && <p>{t("blockedActive", { count: active })}</p>}
              {history > 0 && <p>{t("blockedHistory", { count: history })}</p>}
              <p className="font-semibold">{t("useInactive")}</p>
            </>
          ) : mode === "history" && !preview.allowed ? (
            <p>{t("nothingToDelete")}</p>
          ) : (
            <>
              {deleted === 0 && anonymised === 0 && <p>{t("noBookings")}</p>}
              {deleted > 0 && <p>{t("willDelete", { count: deleted })}</p>}
              {anonymised > 0 && <p>{t("willAnonymise", { count: anonymised })}</p>}
              <p className="font-semibold">{t("irreversible")}</p>
            </>
          )}
          {mode === "history" && active > 0 && <p>{t("keptActive", { count: active })}</p>}
        </div>
      )}
      {error ? <Alert tone="error">{errorText(error)}</Alert> : null}
    </ConfirmDialog>
  );
}
