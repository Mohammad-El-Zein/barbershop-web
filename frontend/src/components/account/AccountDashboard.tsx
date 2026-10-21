"use client";

import { useCallback, useEffect, useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { Link, useRouter } from "@/i18n/navigation";
import { siteConfig } from "@/config/site.config";
import { api, call } from "@/lib/api/client";
import type { Booking, Loyalty } from "@/lib/api/types";
import { useErrorText } from "@/lib/api/useErrorText";
import { useAuth, useLogout } from "@/lib/auth/AuthProvider";
import { openBooking } from "@/lib/booking";
import { dateTime, dateTimeRange } from "@/lib/format";
import { LoyaltyBar } from "../booking/SummaryStep";
import { Alert, Skeleton, StatusBadge } from "../ui/Feedback";
import { ConfirmDialog } from "../ui/Dialog";

/** /account — loyalty progress + my bookings (upcoming / past) with pay, cancel and rebook. */
export function AccountDashboard() {
  const t = useTranslations("account.dashboard");
  const { user } = useAuth();
  const logout = useLogout("/");
  const [scope, setScope] = useState<"upcoming" | "past">("upcoming");
  const [bookings, setBookings] = useState<Booking[] | null>(null);
  const [loyalty, setLoyalty] = useState<Loyalty | null>(null);
  const [error, setError] = useState<unknown>(null);
  const errorText = useErrorText();

  const load = useCallback(() => {
    call(() => api.GET("/api/me/bookings", { params: { query: { scope } } })).then(
      (b) => setBookings(b as Booking[]),
      setError,
    );
    call(() => api.GET("/api/me/loyalty")).then((l) => setLoyalty(l as Loyalty), () => undefined);
  }, [scope]);

  useEffect(load, [load]);

  return (
    <div className="space-y-8">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="eyebrow">{t("eyebrow")}</p>
          <h1 className="display mt-3 text-4xl sm:text-5xl">{t("hello", { name: user?.name ?? "" })}</h1>
        </div>
        <div className="flex gap-3">
          <Link href="/account/profile" className="btn btn-ghost btn-sm">
            {t("profile")}
          </Link>
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            onClick={logout}
          >
            {t("logout")}
          </button>
        </div>
      </header>

      <section className="card card-gold p-5 sm:p-6" aria-labelledby="loyalty-h">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <h2 id="loyalty-h" className="eyebrow">
              {t("loyaltyTitle")}
            </h2>
            {loyalty ? (
              <p className="mt-2 text-lg">
                {loyalty.discountReserved
                  ? t("loyaltyReserved", { percent: loyalty.discountPercent ?? 50 })
                  : loyalty.nextDiscountAvailable
                    ? t("loyaltyReady", { percent: loyalty.discountPercent ?? 50 })
                    : t("loyaltyProgress", {
                        visits: loyalty.visitsInCurrentCycle ?? 0,
                        threshold: loyalty.threshold ?? 10,
                        left: Math.max(0, (loyalty.threshold ?? 10) - (loyalty.visitsInCurrentCycle ?? 0)),
                        percent: loyalty.discountPercent ?? 50,
                      })}
              </p>
            ) : (
              <Skeleton className="mt-2 h-7 w-72" />
            )}
          </div>
          <button type="button" className="btn btn-primary btn-sm" onClick={() => openBooking("account")}>
            {t("book")}
          </button>
        </div>
        {loyalty && <LoyaltyBar visits={loyalty.visitsInCurrentCycle ?? 0} threshold={loyalty.threshold ?? 10} />}
      </section>

      <section aria-labelledby="bookings-h">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <h2 id="bookings-h" className="display text-3xl">
            {t("bookings")}
          </h2>
          <div role="tablist" className="inline-flex rounded-full border border-line p-1">
            {(["upcoming", "past"] as const).map((s) => (
              <button
                key={s}
                type="button"
                role="tab"
                aria-selected={scope === s}
                onClick={() => {
                  setBookings(null);
                  setScope(s);
                }}
                className={`rounded-full px-4 py-2 text-xs font-semibold uppercase tracking-[0.14em] ${scope === s ? "bg-gold text-[#120d05]" : "text-ink/70"}`}
              >
                {t(s)}
              </button>
            ))}
          </div>
        </div>
        {error ? <Alert tone="error">{errorText(error)}</Alert> : null}
        {!bookings ? (
          <div className="space-y-3">
            <Skeleton className="h-32" />
            <Skeleton className="h-32" />
          </div>
        ) : bookings.length === 0 ? (
          <div className="card p-6 text-center text-muted">{t(scope === "upcoming" ? "emptyUpcoming" : "emptyPast")}</div>
        ) : (
          <ul className="space-y-3">
            {bookings.map((b) => (
              <BookingCard key={b.id} booking={b} onChanged={load} />
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

function BookingCard({ booking: b, onChanged }: { booking: Booking; onChanged: () => void }) {
  const t = useTranslations("account.dashboard");
  const tm = useTranslations("manage");
  const locale = useLocale();
  const router = useRouter();
  const errorText = useErrorText();
  const [confirm, setConfirm] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const a = b.actions ?? {};
  const paid = b.payment?.status === "PAID";
  const open = ["PENDING_BARBER", "AWAITING_PAYMENT", "CONFIRMED"].includes(b.status ?? "");
  const refundLine = !paid
    ? tm("noPayment")
    : a.refundIfCancelledNow
      ? tm("refund", { amount: b.payment?.amount?.formatted ?? "" })
      : tm("noRefund", { amount: b.payment?.amount?.formatted ?? "" });

  const pay = async () => {
    setBusy(true);
    setError(null);
    try {
      const link = await call(() => api.POST("/api/me/bookings/{id}/payment-link", { params: { path: { id: b.id! } } }));
      router.push(`/pay/${link?.token}`);
    } catch (e) {
      setError(e);
      setBusy(false);
    }
  };
  const cancel = async () => {
    setBusy(true);
    setError(null);
    try {
      await call(() => api.POST("/api/me/bookings/{id}/cancel", { params: { path: { id: b.id! } }, body: {} }));
      setConfirm(false);
      onChanged();
    } catch (e) {
      setError(e);
      setConfirm(false);
    } finally {
      setBusy(false);
    }
  };

  return (
    <li className="card p-5" data-booking-id={b.id}>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="font-semibold">{(b.services ?? []).map((s) => s.name).join(" + ")}</p>
          <p className="mt-1 text-sm text-muted">
            {dateTimeRange(b.start!, b.end!, locale, b.timezone)} · {b.staff?.name}
          </p>
          <p className="mt-1 text-sm">
            {t("dueAtShop")}: <span className="font-semibold text-gold-text">{b.price?.amountDueAtShop?.formatted}</span>
            {(b.price?.loyaltyDiscount?.cents ?? 0) > 0 && (
              <span className="text-muted"> · {t("discount", { amount: b.price?.loyaltyDiscount?.formatted ?? "" })}</span>
            )}
          </p>
        </div>
        <StatusBadge status={b.status} />
      </div>

      {b.status === "AWAITING_PAYMENT" && b.payment?.deadline && (
        <p className="mt-3 text-sm text-ink/85">{t("payUntil", { amount: b.payment?.amount?.formatted ?? "", time: dateTime(b.payment.deadline, locale) })}</p>
      )}
      {error ? <Alert tone="error" className="mt-3">{errorText(error)}</Alert> : null}

      <div className="mt-4 flex flex-wrap gap-2">
        {b.status === "AWAITING_PAYMENT" && (
          <button type="button" className="btn btn-primary btn-sm" onClick={pay} disabled={busy}>
            {t("payNow")}
          </button>
        )}
        {a.canCancel && (
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => setConfirm(true)} disabled={busy}>
            {t("cancel")}
          </button>
        )}
        {open && !a.canCancel && <span className="text-xs text-muted">{tm("tooLateText", { phone: siteConfig.contact.phone })}</span>}
        <button
          type="button"
          className="btn btn-ghost btn-sm"
          onClick={() =>
            openBooking("rebook", {
              serviceIds: (b.services ?? []).map((s) => s.serviceId!).filter(Boolean),
              staffId: b.staff?.id,
            })
          }
        >
          {t("rebook")}
        </button>
      </div>

      <ConfirmDialog
        open={confirm}
        title={tm("confirmTitle")}
        confirmLabel={tm("confirmButton")}
        danger
        busy={busy}
        onConfirm={cancel}
        onClose={() => setConfirm(false)}
      >
        <p>{refundLine}</p>
      </ConfirmDialog>
    </li>
  );
}
