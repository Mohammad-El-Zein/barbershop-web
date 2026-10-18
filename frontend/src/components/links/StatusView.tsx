"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { useLocale, useTranslations } from "next-intl";
import { Link, useRouter } from "@/i18n/navigation";
import { api, call } from "@/lib/api/client";
import type { Booking, BookingStatus } from "@/lib/api/types";
import { useErrorText } from "@/lib/api/useErrorText";
import { dateTime } from "@/lib/format";
import { bookingParams, trackOnce } from "@/lib/tracking";
import { Alert, Skeleton, Spinner, StatusBadge } from "../ui/Feedback";
import { BookingSummary, LinkProblem } from "./LinkState";

const POLL_MS = 20_000;

/**
 * /booking/status/[token]: right after booking. Polls every 20 s (and when the
 * tab becomes visible again): "waiting for the barber" → "accepted: pay the
 * deposit" (Pay button → fresh payment link → demo payment page) → "confirmed".
 * Everything shown comes from the backend's booking status.
 */
export function StatusView() {
  const { token } = useParams<{ token: string }>();
  const t = useTranslations("statusPage");
  const locale = useLocale();
  const router = useRouter();
  const errorText = useErrorText();
  const [booking, setBooking] = useState<Booking | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [checkedAt, setCheckedAt] = useState<Date | null>(null);
  const [paying, setPaying] = useState(false);
  const [payError, setPayError] = useState<unknown>(null);

  const load = useCallback(
    () =>
      call(() => api.GET("/api/public/booking-status/{token}", { params: { path: { token } } })).then(
        (b) => {
          setBooking(b as Booking);
          setError(null);
          setCheckedAt(new Date());
        },
        (e) => setError(e),
      ),
    [token],
  );

  useEffect(() => {
    load();
    const id = setInterval(() => document.visibilityState === "visible" && load(), POLL_MS);
    const onVisible = () => document.visibilityState === "visible" && load();
    document.addEventListener("visibilitychange", onVisible);
    return () => {
      clearInterval(id);
      document.removeEventListener("visibilitychange", onVisible);
    };
  }, [load]);

  // conversion: the booking reached "confirmed" (once per booking, also if seen on the payment page)
  useEffect(() => {
    if (booking?.id && (booking.status === "CONFIRMED" || booking.status === "COMPLETED")) {
      trackOnce(`confirmed:${booking.id}`, "booking_confirmed", bookingParams(booking));
    }
  }, [booking]);

  const pay = async () => {
    setPaying(true);
    setPayError(null);
    try {
      const link = await call(() =>
        api.POST("/api/public/booking-status/{token}/payment-link", { params: { path: { token } } }),
      );
      router.push(`/pay/${link?.token}`);
    } catch (e) {
      setPayError(e);
      setPaying(false);
      load();
    }
  };

  if (error && !booking) return <LinkProblem error={error} />;
  if (!booking)
    return (
      <div className="space-y-4">
        <Skeleton className="h-16 w-2/3" />
        <Skeleton className="h-72" />
      </div>
    );

  const status = booking.status as BookingStatus;
  const live = status === "PENDING_BARBER" || status === "AWAITING_PAYMENT";

  return (
    <div className="space-y-6">
      <header>
        <p className="eyebrow">{t("eyebrow")}</p>
        <h1 className="display mt-3 text-4xl sm:text-5xl" aria-live="polite">
          {t(`${status}.title`)}
        </h1>
        <p className="mt-3 max-w-2xl text-muted">
          {t(`${status}.text`, {
            barber: booking.staff?.name ?? "",
            deadline: booking.barberDeadline ? dateTime(booking.barberDeadline, locale) : "",
            payUntil: booking.payment?.deadline ? dateTime(booking.payment.deadline, locale) : "",
            deposit: booking.price?.deposit?.formatted ?? "",
            email: booking.customerEmail ?? "",
          })}
        </p>
      </header>

      <Timeline status={status} />

      {status === "AWAITING_PAYMENT" && (
        <div className="card card-gold flex flex-col gap-4 p-5 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="font-semibold">{t("payNowTitle", { amount: booking.price?.deposit?.formatted ?? "" })}</p>
            <p className="text-sm text-muted">{t("payNowText")}</p>
          </div>
          <button type="button" className="btn btn-primary" onClick={pay} disabled={paying} aria-busy={paying}>
            {t("payNow")}
          </button>
        </div>
      )}
      {payError ? <Alert tone="error">{errorText(payError)}</Alert> : null}

      <div className="card p-5">
        <div className="mb-3 flex items-center justify-between gap-3">
          <h2 className="text-sm font-semibold uppercase tracking-[0.14em] text-muted">{t("summary")}</h2>
          <StatusBadge status={status} />
        </div>
        <BookingSummary booking={booking} />
      </div>

      <div className="flex flex-wrap items-center justify-between gap-4 text-sm text-muted">
        {live ? (
          <span className="flex items-center gap-3">
            <Spinner />
            {t("autoRefresh")} {checkedAt && t("checkedAt", { time: checkedAt.toLocaleTimeString(locale, { hour: "2-digit", minute: "2-digit", second: "2-digit" }) })}
          </span>
        ) : (
          <span />
        )}
        <div className="flex gap-3">
          {live && (
            <button type="button" className="btn btn-ghost btn-sm" onClick={load}>
              {t("refresh")}
            </button>
          )}
          {["DECLINED", "EXPIRED", "CANCELLED"].includes(status) ? (
            <Link href={`/booking?services=${(booking.services ?? []).map((s) => s.serviceId).join(",")}`} className="btn btn-primary btn-sm">
              {t("rebook")}
            </Link>
          ) : (
            <Link href="/" className="btn btn-ghost btn-sm">
              {t("home")}
            </Link>
          )}
        </div>
      </div>
    </div>
  );
}

/** Request sent → accepted → paid & confirmed. */
function Timeline({ status }: { status: BookingStatus }) {
  const t = useTranslations("statusPage.timeline");
  const reached =
    status === "PENDING_BARBER" ? 1 : status === "AWAITING_PAYMENT" ? 2 : status === "CONFIRMED" || status === "COMPLETED" ? 3 : 0;
  const steps = ["sent", "accepted", "confirmed"] as const;
  if (reached === 0) return null;
  return (
    <ol className="grid grid-cols-3 gap-2">
      {steps.map((s, i) => (
        <li key={s} aria-current={i + 1 === reached ? "step" : undefined}>
          <span className={`block h-1.5 rounded-full ${i < reached ? "bg-gold" : "bg-ink/15"}`} />
          <span className={`mt-2 block text-xs font-semibold uppercase tracking-[0.12em] ${i < reached ? "text-gold-text" : "text-muted"}`}>
            {t(s)}
          </span>
        </li>
      ))}
    </ol>
  );
}
