"use client";

import { useEffect, useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { api, call } from "@/lib/api/client";
import type { AdminBooking, DeletionResult } from "@/lib/api/types";
import { useErrorText } from "@/lib/api/useErrorText";
import { dateTime, time } from "@/lib/format";
import { ConfirmDialog, Dialog } from "../ui/Dialog";
import { Alert, Skeleton, StatusBadge } from "../ui/Feedback";

/** Requested, payment open, confirmed: never deleted (cancel first) — the server enforces it too. */
const ACTIVE = ["PENDING_BARBER", "AWAITING_PAYMENT", "CONFIRMED"];

/**
 * Admin → Bookings: everything about one booking (customer, contact, barber, services, time,
 * status, money, note, history) and deleting it. A booking with a paid / refunded / kept deposit
 * is anonymised instead (the payment records stay); the dialog says which of the two happens.
 */
export function AdminBookingDetail({
  id,
  onClose,
  onDeleted,
}: {
  id: string | null;
  onClose: () => void;
  onDeleted: (result: DeletionResult) => void;
}) {
  const t = useTranslations("staff.bookings");
  const tb = useTranslations("staff.booking");
  const locale = useLocale();
  const errorText = useErrorText();
  const [b, setB] = useState<AdminBooking | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [confirm, setConfirm] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!id) return;
    call(() => api.GET("/api/admin/bookings/{id}", { params: { path: { id } } })).then((r) => setB(r ?? null), setError);
  }, [id]);

  // reset on the way out, so the next booking never shows the previous one while loading
  const close = () => {
    setB(null);
    setError(null);
    onClose();
  };

  const active = !!b && ACTIVE.includes(b.status ?? "");
  const moneyMoved = !!b?.payment && b.payment.status !== "UNPAID";

  const remove = async () => {
    if (!b) return;
    setBusy(true);
    setError(null);
    try {
      const r = await call(() => api.DELETE("/api/admin/bookings/{id}", { params: { path: { id: b.id! } } }));
      setConfirm(false);
      setB(null);
      onDeleted(r ?? { deleted: 0, anonymised: 0, keptActive: 0 });
    } catch (e) {
      setConfirm(false);
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open={id !== null} onClose={close} title={t("detailTitle")}>
      {error ? <Alert tone="error" className="mb-4">{errorText(error)}</Alert> : null}
      {!b ? (
        !error && <Skeleton className="h-64" />
      ) : (
        <div className="space-y-6" data-booking-detail={b.id}>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-lg font-semibold tabular-nums">
              {dateTime(b.start!, locale)}–{time(b.end!, locale)}
            </p>
            <StatusBadge status={b.status} />
          </div>

          <dl className="grid gap-x-6 gap-y-3 text-sm sm:grid-cols-[10rem_1fr]">
            <Fact label={t("customer")}>{b.customerName}</Fact>
            {b.customerEmail && (
              <Fact label={t("email")}>
                <a className="break-all underline underline-offset-2 hover:text-gold-text" href={`mailto:${b.customerEmail}`}>
                  {b.customerEmail}
                </a>
              </Fact>
            )}
            {b.customerPhone && (
              <Fact label={t("phone")}>
                <a className="underline underline-offset-2 hover:text-gold-text" href={`tel:${b.customerPhone.replace(/\s/g, "")}`}>
                  {b.customerPhone}
                </a>
              </Fact>
            )}
            <Fact label={t("barber")}>{b.staff?.name}</Fact>
            <Fact label={t("services")}>
              <ul>
                {(b.services ?? []).map((s, i) => (
                  <li key={`${s.serviceId}-${i}`}>
                    {s.name} · {s.durationMin} min · {s.price?.formatted}
                  </li>
                ))}
              </ul>
            </Fact>
            <Fact label={t("appointment")}>
              {dateTime(b.start!, locale)}–{time(b.end!, locale)} ({b.durationMin} min)
            </Fact>
            {b.source && b.source !== "ONLINE" && <Fact label={t("source")}>{tb(`source.${b.source}`)}</Fact>}
            <Fact label={t("total")}>{b.price?.total?.formatted}</Fact>
            {(b.price?.loyaltyDiscount?.cents ?? 0) > 0 && (
              <Fact label={t("loyaltyDiscount")}>−{b.price?.loyaltyDiscount?.formatted}</Fact>
            )}
            <Fact label={t("deposit")}>{b.price?.deposit?.formatted}</Fact>
            <Fact label={t("dueAtShop")}>{b.price?.amountDueAtShop?.formatted}</Fact>
            <Fact label={t("payment")}>
              {b.payment
                ? `${b.payment.amount?.formatted} · ${tb(`paymentStatus.${b.payment.status}`)}`
                : t("noPayment")}
            </Fact>
            {b.note && <Fact label={t("note")}>“{b.note}”</Fact>}
            {b.statusReason && <Fact label={t("reason")}>{b.statusReason}</Fact>}
          </dl>

          {(b.timeline ?? []).length > 0 && (
            <section aria-labelledby={`timeline-${b.id}`}>
              <h3 id={`timeline-${b.id}`} className="label">
                {t("timeline")}
              </h3>
              <ol className="space-y-1 border-l border-line pl-4 text-sm">
                {(b.timeline ?? []).map((e, i) => (
                  <li key={`${e.event}-${i}`}>
                    <span className="tabular-nums text-muted">{dateTime(e.at!, locale)}</span> · {t(`event.${e.event}`)}
                  </li>
                ))}
              </ol>
            </section>
          )}

          <section className="border-t border-line pt-4">
            {active ? (
              <p className="text-sm text-muted">{t("activeHint")}</p>
            ) : (
              <button type="button" className="btn btn-ghost btn-sm btn-danger-text" onClick={() => setConfirm(true)}>
                {t("delete")}
              </button>
            )}
          </section>

          <ConfirmDialog
            open={confirm}
            title={t("deleteConfirmTitle")}
            confirmLabel={t("delete")}
            danger
            busy={busy}
            onConfirm={remove}
            onClose={() => setConfirm(false)}
          >
            <p>
              {b.customerName} · {dateTime(b.start!, locale)}
            </p>
            <p>{moneyMoved ? t("deletePaid") : t("deleteFull")}</p>
          </ConfirmDialog>
        </div>
      )}
    </Dialog>
  );
}

function Fact({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <>
      <dt className="text-xs uppercase tracking-wider text-muted sm:pt-0.5">{label}</dt>
      <dd className="min-w-0">{children}</dd>
    </>
  );
}
