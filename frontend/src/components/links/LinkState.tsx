"use client";

import { useLocale, useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { ApiError } from "@/lib/api/client";
import type { Booking } from "@/lib/api/types";
import { useErrorText } from "@/lib/api/useErrorText";
import { dateTimeRange } from "@/lib/format";
import { Alert, Row, StatusBadge } from "../ui/Feedback";

/**
 * What to show when an email link can't be used: already used (410 token.used),
 * expired (410 token.expired) or unknown (404 token.invalid) — with the current
 * booking/payment status when the backend tells us.
 */
export function LinkProblem({ error }: { error: unknown }) {
  const t = useTranslations("links");
  const errorText = useErrorText();
  const code = error instanceof ApiError ? error.code : "";
  const bookingStatus = error instanceof ApiError ? (error.body.bookingStatus as string | undefined) : undefined;
  const paymentStatus = error instanceof ApiError ? (error.body.paymentStatus as string | undefined) : undefined;
  const kind = code === "token.used" ? "used" : code === "token.expired" ? "expired" : code === "token.invalid" ? "invalid" : null;

  return (
    <div className="card p-6 sm:p-8">
      <p className="eyebrow">{t("eyebrow")}</p>
      <h2 className="display mt-3 text-3xl sm:text-4xl">{kind ? t(`${kind}.title`) : t("error.title")}</h2>
      <p className="mt-3 text-muted">{kind ? t(`${kind}.text`) : errorText(error)}</p>
      {(bookingStatus || paymentStatus) && (
        <p className="mt-4 flex flex-wrap items-center gap-2 text-sm">
          {bookingStatus && (
            <>
              {t("currentStatus")} <StatusBadge status={bookingStatus} />
            </>
          )}
          {paymentStatus && <span>{t(`payment.${paymentStatus}`)}</span>}
        </p>
      )}
      <div className="mt-6 flex flex-wrap gap-3">
        <Link href="/" className="btn btn-ghost">
          {t("home")}
        </Link>
        <Link href="/booking" className="btn btn-outline-gold">
          {t("book")}
        </Link>
      </div>
    </div>
  );
}

/** Compact booking summary used on the status, manage and payment pages. */
export function BookingSummary({ booking, showPrices = true }: { booking: Booking; showPrices?: boolean }) {
  const t = useTranslations("booking.summary");
  const locale = useLocale();
  const p = booking.price;
  return (
    <dl>
      <Row label={t("services")}>{(booking.services ?? []).map((s) => s.name).join(" + ")}</Row>
      <Row label={t("barber")}>{booking.staff?.name}</Row>
      <Row label={t("when")}>{dateTimeRange(booking.start!, booking.end!, locale, booking.timezone)}</Row>
      {showPrices && p && (
        <>
          <Row label={t("total")}>{p.total?.formatted}</Row>
          {(p.loyaltyDiscount?.cents ?? 0) > 0 && <Row label={t("loyaltyDiscount")}>− {p.loyaltyDiscount?.formatted}</Row>}
          {(p.deposit?.cents ?? 0) > 0 && <Row label={t("deposit")}>{p.deposit?.formatted}</Row>}
          <Row label={t("dueAtShop")} strong>
            {p.amountDueAtShop?.formatted}
          </Row>
        </>
      )}
    </dl>
  );
}

export function ErrorBox({ error }: { error: unknown }) {
  const errorText = useErrorText();
  return error ? <Alert tone="error">{errorText(error)}</Alert> : null;
}
