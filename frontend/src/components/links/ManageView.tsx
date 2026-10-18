"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { useLocale, useTranslations } from "next-intl";
import { siteConfig } from "@/config/site.config";
import { api, call } from "@/lib/api/client";
import type { Booking } from "@/lib/api/types";
import { dateTime } from "@/lib/format";
import { rulesOf, useShop } from "@/lib/useShop";
import { Alert, Skeleton, StatusBadge } from "../ui/Feedback";
import { ConfirmDialog } from "../ui/Dialog";
import { TextArea } from "../ui/Field";
import { BookingSummary, ErrorBox, LinkProblem } from "./LinkState";

/**
 * /booking/manage/[token] — the guest's (and customer's) manage link. Opening it
 * changes nothing; cancelling needs the button + confirmation (POST). Before
 * confirming it says whether the deposit would come back.
 */
export function ManageView() {
  const { token } = useParams<{ token: string }>();
  const t = useTranslations("manage");
  const locale = useLocale();
  const rules = rulesOf(useShop());
  const [booking, setBooking] = useState<Booking | null>(null);
  const [loadError, setLoadError] = useState<unknown>(null);
  const [confirming, setConfirming] = useState(false);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [done, setDone] = useState(false);

  useEffect(() => {
    call(() => api.GET("/api/public/manage/{token}", { params: { path: { token } } })).then(
      (b) => setBooking(b as Booking),
      setLoadError,
    );
  }, [token]);

  const cancel = async () => {
    setBusy(true);
    setError(null);
    try {
      const b = await call(() =>
        api.POST("/api/public/manage/{token}/cancel", {
          params: { path: { token } },
          body: { reason: reason.trim() || undefined },
        }),
      );
      setBooking(b as Booking);
      setDone(true);
      setConfirming(false);
    } catch (e) {
      setError(e);
      setConfirming(false);
    } finally {
      setBusy(false);
    }
  };

  if (loadError) return <LinkProblem error={loadError} />;
  if (!booking)
    return (
      <div className="space-y-4">
        <Skeleton className="h-16 w-2/3" />
        <Skeleton className="h-72" />
      </div>
    );

  const a = booking.actions ?? {};
  const paid = booking.payment?.status === "PAID";
  const open = ["PENDING_BARBER", "AWAITING_PAYMENT", "CONFIRMED"].includes(booking.status ?? "");
  const refundLine = !paid
    ? t("noPayment")
    : a.refundIfCancelledNow
      ? t("refund", { amount: booking.payment?.amount?.formatted ?? "" })
      : t("noRefund", { amount: booking.payment?.amount?.formatted ?? "" });

  return (
    <div className="space-y-6">
      <header>
        <p className="eyebrow">{t("eyebrow")}</p>
        <h1 className="display mt-3 text-4xl sm:text-5xl">{done ? t("cancelledTitle") : t("title")}</h1>
        {done && <p className="mt-3 text-muted">{t("cancelledText")}</p>}
      </header>

      <div className="card p-5">
        <div className="mb-3 flex items-center justify-between gap-3">
          <p className="text-sm text-muted">{booking.customerName}</p>
          <StatusBadge status={booking.status} />
        </div>
        <BookingSummary booking={booking} />
      </div>

      {done && booking.payment && (
        <Alert tone={booking.payment.status === "REFUNDED" ? "success" : "info"}>
          {t(`after.${booking.payment.status}`, { amount: booking.payment.amount?.formatted ?? "" })}
          {booking.payment.demoMode && booking.payment.status === "REFUNDED" ? ` ${t("demoRefund")}` : ""}
        </Alert>
      )}

      <ErrorBox error={error} />

      {!done && open && (
        <div className="card p-5">
          {a.canCancel ? (
            <>
              <h2 className="font-semibold">{t("cancelHeading")}</h2>
              <p className="mt-2 text-sm text-muted">{refundLine}</p>
              {a.refundableUntil && paid && (
                <p className="mt-1 text-xs text-muted">{t("refundableUntil", { time: dateTime(a.refundableUntil, locale) })}</p>
              )}
              <button type="button" className="btn btn-danger mt-4" onClick={() => setConfirming(true)}>
                {t("cancelButton")}
              </button>
            </>
          ) : (
            <Alert tone="warn" title={t("tooLateTitle")}>
              {t("tooLateText", { hours: Math.round(rules.onlineCancelCutoffMin / 60), phone: siteConfig.contact.phone })}
            </Alert>
          )}
        </div>
      )}

      <ConfirmDialog
        open={confirming}
        title={t("confirmTitle")}
        confirmLabel={t("confirmButton")}
        danger
        busy={busy}
        onConfirm={cancel}
        onClose={() => setConfirming(false)}
      >
        <p>{refundLine}</p>
        <TextArea label={t("reason")} value={reason} maxLength={500} onChange={(e) => setReason(e.target.value)} />
      </ConfirmDialog>
    </div>
  );
}
