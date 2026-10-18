"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { useLocale, useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { api, call } from "@/lib/api/client";
import type { PaymentPage } from "@/lib/api/types";
import { dateTime } from "@/lib/format";
import { bookingParams, trackOnce } from "@/lib/tracking";
import { Alert, DemoBanner, Skeleton, StatusBadge } from "../ui/Feedback";
import { ConfirmDialog } from "../ui/Dialog";
import { BookingSummary, ErrorBox, LinkProblem } from "./LinkState";

type Outcome = { kind: "paid"; bookingStatus?: string; already: boolean } | { kind: "cancelled" };

/**
 * /pay/[token] — DEMO payment page. A clearly visible "DEMO MODE: no real
 * payment" banner, the amount, the booking summary and exactly two buttons:
 * "Simulate payment" and "Cancel". There are NO card or card-like fields.
 * Opening the page changes nothing; both buttons POST.
 */
export function PayView() {
  const { token } = useParams<{ token: string }>();
  const t = useTranslations("pay");
  const locale = useLocale();
  const [page, setPage] = useState<PaymentPage | null>(null);
  const [loadError, setLoadError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const [confirmCancel, setConfirmCancel] = useState(false);

  useEffect(() => {
    call(() => api.GET("/api/public/payments/{token}", { params: { path: { token } } })).then(
      (p) => setPage(p as PaymentPage),
      setLoadError,
    );
  }, [token]);

  const simulate = async () => {
    setBusy(true);
    setError(null);
    try {
      const r = await call(() => api.POST("/api/public/payments/{token}/simulate", { params: { path: { token } } }));
      setOutcome({ kind: "paid", bookingStatus: r?.bookingStatus, already: !!r?.alreadyProcessed });
      if (r?.bookingStatus === "CONFIRMED" && page?.booking?.id) {
        trackOnce(`confirmed:${page.booking.id}`, "booking_confirmed", bookingParams(page.booking));
      }
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  const cancel = async () => {
    setBusy(true);
    setError(null);
    try {
      await call(() => api.POST("/api/public/payments/{token}/cancel", { params: { path: { token } } }));
      setOutcome({ kind: "cancelled" });
      setConfirmCancel(false);
    } catch (e) {
      setError(e);
      setConfirmCancel(false);
    } finally {
      setBusy(false);
    }
  };

  if (loadError) return <LinkProblem error={loadError} />;
  if (!page)
    return (
      <div className="space-y-4">
        <Skeleton className="h-14" />
        <Skeleton className="h-80" />
      </div>
    );

  const booking = page.booking!;

  if (outcome) {
    const statusToken = readStatusToken(); // client-only: outcome exists only after a click
    return (
      <div className="space-y-6">
        {page.demoMode && <DemoBanner />}
        <header>
          <p className="eyebrow">{t("eyebrow")}</p>
          <h1 className="display mt-3 text-4xl sm:text-5xl">
            {outcome.kind === "paid" ? t("successTitle") : t("cancelledTitle")}
          </h1>
          <p className="mt-3 text-muted">
            {outcome.kind === "paid"
              ? t("successText", { email: booking.customerEmail ?? "" })
              : t("cancelledText")}
          </p>
        </header>
        <div className="card p-5">
          <div className="mb-3 flex justify-end">
            <StatusBadge status={outcome.kind === "paid" ? (outcome.bookingStatus ?? "CONFIRMED") : "CANCELLED"} />
          </div>
          <BookingSummary booking={booking} />
        </div>
        {outcome.kind === "paid" && <Alert tone="success">{t("depositDeducted")}</Alert>}
        <div className="flex flex-wrap gap-3">
          {statusToken && (
            <Link href={`/booking/status/${statusToken}`} className="btn btn-primary">
              {t("toStatus")}
            </Link>
          )}
          <Link href="/" className="btn btn-ghost">
            {t("home")}
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {page.demoMode && <DemoBanner />}
      <header>
        <p className="eyebrow">{t("eyebrow")}</p>
        <h1 className="display mt-3 text-4xl sm:text-5xl">{t("title")}</h1>
        <p className="mt-3 text-muted">{t("lead")}</p>
      </header>

      <div className="card card-gold flex flex-col items-start gap-1 p-6">
        <span className="label !mb-0">{t("amount")}</span>
        <span className="display text-5xl text-gold-text">{page.amount?.formatted}</span>
        {page.payUntil && <span className="text-sm text-muted">{t("payUntil", { time: dateTime(page.payUntil, locale) })}</span>}
        <span className="mt-2 text-sm text-ink/80">{t("deducted")}</span>
      </div>

      <div className="card p-5">
        <BookingSummary booking={booking} />
      </div>

      <ErrorBox error={error} />

      <div className="flex flex-col gap-3 sm:flex-row">
        <button type="button" className="btn btn-primary btn-lg" onClick={simulate} disabled={busy} aria-busy={busy}>
          {page.demoMode ? t("simulate") : t("pay")}
        </button>
        <button type="button" className="btn btn-ghost btn-lg" onClick={() => setConfirmCancel(true)} disabled={busy}>
          {t("cancel")}
        </button>
      </div>
      {page.demoMode && <p className="text-xs text-muted">{t("demoNote")}</p>}

      <ConfirmDialog
        open={confirmCancel}
        title={t("cancelConfirmTitle")}
        confirmLabel={t("cancelConfirm")}
        danger
        busy={busy}
        onConfirm={cancel}
        onClose={() => setConfirmCancel(false)}
      >
        <p>{t("cancelConfirmText")}</p>
      </ConfirmDialog>
    </div>
  );
}

/** The status link remembered by the booking flow (sessionStorage), to lead back after paying. */
function readStatusToken(): string | null {
  try {
    return sessionStorage.getItem("booking:status");
  } catch {
    return null;
  }
}
