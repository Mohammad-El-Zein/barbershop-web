"use client";

import { useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { api, call } from "@/lib/api/client";
import type { AdminBooking } from "@/lib/api/types";
import { useErrorText } from "@/lib/api/useErrorText";
import { useAuth } from "@/lib/auth/AuthProvider";
import { dateTime, shortDate, time } from "@/lib/format";
import { Alert, StatusBadge } from "../ui/Feedback";
import { ConfirmDialog } from "../ui/Dialog";
import { TextArea } from "../ui/Field";

type Action = "accept" | "decline" | "complete" | "no-show" | "cancel" | "refund";

/** Which action buttons a status transition maps to. */
const BY_TARGET: Record<string, Action> = {
  AWAITING_PAYMENT: "accept",
  DECLINED: "decline",
  COMPLETED: "complete",
  NO_SHOW: "no-show",
  CANCELLED: "cancel",
};

/**
 * A booking card for the staff area with the actions the backend allows
 * (allowedTransitions). Barbers use /api/barber/…, admins /api/admin/….
 * Destructive actions ask for confirmation first.
 */
export function StaffBookingCard({
  booking: b,
  onChanged,
  withDate = false,
}: {
  booking: AdminBooking;
  onChanged: () => void;
  /** In lists spanning several days (requests, all bookings) the card shows the date too. */
  withDate?: boolean;
}) {
  const t = useTranslations("staff.booking");
  const locale = useLocale();
  const { user } = useAuth();
  const errorText = useErrorText();
  const admin = user?.role === "ADMIN";
  const [pending, setPending] = useState<Action | null>(null);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  const [now] = useState(() => Date.now());
  const started = new Date(b.start!).getTime() <= now;
  const actions = (b.allowedTransitions ?? [])
    .map((s) => BY_TARGET[s])
    .filter((a): a is Action => !!a)
    .filter((a) => (a === "complete" || a === "no-show" ? started : true));
  // refund override: give back a kept deposit (late cancellation / no-show)
  if (admin && b.payment?.status === "FORFEITED") {
    actions.push("refund");
  }

  const run = async (a: Action) => {
    setBusy(true);
    setError(null);
    const id = b.id!;
    const body = a === "decline" || a === "cancel" || a === "refund" ? { reason: reason.trim() || undefined } : undefined;
    try {
      if (admin) {
        const path = `/api/admin/bookings/{id}/${a}` as "/api/admin/bookings/{id}/accept";
        await call(() => api.POST(path, { params: { path: { id } }, body } as never));
      } else {
        const path = `/api/barber/bookings/{id}/${a}` as "/api/barber/bookings/{id}/accept";
        await call(() => api.POST(path, { params: { path: { id } }, body } as never));
      }
      setPending(null);
      setReason("");
      onChanged();
    } catch (e) {
      setError(e);
      setPending(null);
    } finally {
      setBusy(false);
    }
  };

  const confirmNeeded = (a: Action) => a !== "accept" && a !== "complete";

  return (
    <article className="card p-4" data-booking-id={b.id}>
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="font-semibold tabular-nums">
            {withDate && `${shortDate(b.start!, locale)} · `}
            {time(b.start!, locale)}–{time(b.end!, locale)} · {b.customerName}
          </p>
          <p className="text-sm text-muted">
            {(b.services ?? []).map((s) => s.name).join(" + ")}
            {admin && ` · ${b.staff?.name}`}
          </p>
        </div>
        <StatusBadge status={b.status} />
      </div>
      <dl className="mt-2 grid gap-x-4 gap-y-0.5 text-xs text-muted sm:grid-cols-2">
        {b.customerPhone && <div>☎ {b.customerPhone}</div>}
        {b.customerEmail && <div className="truncate">✉ {b.customerEmail}</div>}
        <div>
          {t("dueAtShop")}: <span className="text-ink">{b.price?.amountDueAtShop?.formatted}</span>
        </div>
        {b.payment && <div>{t("payment", { status: t(`paymentStatus.${b.payment.status}`) })}</div>}
        {b.status === "PENDING_BARBER" && b.barberDeadline && <div>{t("respondUntil", { time: dateTime(b.barberDeadline, locale) })}</div>}
        {b.source && b.source !== "ONLINE" && <div>{t(`source.${b.source}`)}</div>}
      </dl>
      {b.note && <p className="mt-2 rounded-lg bg-ink/5 px-3 py-2 text-sm">“{b.note}”</p>}
      {error ? <Alert tone="error" className="mt-3">{errorText(error)}</Alert> : null}
      {actions.length > 0 && (
        <div className="mt-3 flex flex-wrap gap-2">
          {actions.map((a) => (
            <button
              key={a}
              type="button"
              className={`btn btn-sm ${a === "accept" || a === "complete" ? "btn-primary" : "btn-ghost"}`}
              onClick={() => (confirmNeeded(a) ? setPending(a) : run(a))}
              disabled={busy}
            >
              {t(`actions.${a}`)}
            </button>
          ))}
        </div>
      )}
      <ConfirmDialog
        open={pending !== null}
        title={pending ? t(`confirm.${pending}`) : ""}
        confirmLabel={pending ? t(`actions.${pending}`) : ""}
        danger={pending === "cancel" || pending === "decline" || pending === "no-show"}
        busy={busy}
        onConfirm={() => pending && run(pending)}
        onClose={() => setPending(null)}
      >
        <p>
          {b.customerName} · {dateTime(b.start!, locale)}
        </p>
        {pending === "cancel" && b.payment?.status === "PAID" && <p>{t("cancelRefund")}</p>}
        {(pending === "decline" || pending === "cancel" || pending === "refund") && (
          <TextArea label={t("reason")} value={reason} maxLength={500} onChange={(e) => setReason(e.target.value)} />
        )}
      </ConfirmDialog>
    </article>
  );
}
