"use client";

import { useEffect, useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { api, call } from "@/lib/api/client";
import { TENANT } from "@/lib/api/config";
import type { BookingRules, PricePreview, Service } from "@/lib/api/types";
import { useErrorText } from "@/lib/api/useErrorText";
import { useAuth } from "@/lib/auth/AuthProvider";
import { dateTimeRange, minutes, money } from "@/lib/format";
import { Alert, Row, Skeleton } from "../ui/Feedback";
import type { SlotChoice } from "./SlotStep";

/** Step 4: everything at a glance, with the exact numbers from the price-preview endpoint. */
export function SummaryStep({
  services,
  choice,
  rules,
  note,
}: {
  services: Service[];
  choice: SlotChoice;
  rules: Required<BookingRules>;
  note: string;
}) {
  const t = useTranslations("booking.summary");
  const locale = useLocale();
  const errorText = useErrorText();
  const { status, user } = useAuth();
  const [preview, setPreview] = useState<PricePreview | null>(null);
  const [error, setError] = useState<unknown>(null);
  const ids = services.map((s) => s.id!).join(",");

  useEffect(() => {
    let alive = true;
    call(() =>
      api.POST("/api/public/{tenant}/bookings/price-preview", {
        params: { path: { tenant: TENANT } },
        body: { serviceIds: ids.split(",") },
      }),
    ).then(
      (p) => alive && setPreview(p as PricePreview),
      (e) => alive && setError(e),
    );
    return () => {
      alive = false;
    };
    // re-run when the login state changes (loyalty only for logged-in customers)
  }, [ids, status, user?.id, locale]);

  const deposit = money(rules.depositCents, locale);
  const p = preview?.price;
  const loyalty = preview?.loyalty;

  return (
    <div className="grid gap-6 md:grid-cols-[1.2fr_1fr]">
      <div className="card p-5">
        <dl>
          <Row label={t("services")}>{services.map((s) => s.name).join(" + ")}</Row>
          <Row label={t("barber")}>{choice.staffName ?? t("anyBarber")}</Row>
          <Row label={t("when")}>{dateTimeRange(choice.start, choice.end, locale)}</Row>
          <Row label={t("duration")}>{minutes(preview?.durationMin ?? services.reduce((n, s) => n + (s.durationMin ?? 0), 0), locale)}</Row>
          {note.trim() && <Row label={t("note")}>{note.trim()}</Row>}
        </dl>
        <div className="mt-4 border-t border-line pt-2" aria-live="polite">
          {error ? (
            <Alert tone="error">{errorText(error)}</Alert>
          ) : !p ? (
            <div className="space-y-2 py-1">
              {[0, 1, 2, 3].map((i) => (
                <Skeleton key={i} className="h-6" />
              ))}
            </div>
          ) : (
            <dl>
              <Row label={t("total")}>{p.total?.formatted}</Row>
              {(p.loyaltyDiscount?.cents ?? 0) > 0 && <Row label={t("loyaltyDiscount")}>− {p.loyaltyDiscount?.formatted}</Row>}
              <Row label={t("deposit")}>{p.deposit?.formatted}</Row>
              <Row label={t("dueAtShop")} strong>
                {p.amountDueAtShop?.formatted}
              </Row>
            </dl>
          )}
        </div>
      </div>

      <div className="space-y-4">
        {loyalty && (
          <div className="card card-gold p-5">
            <p className="eyebrow">{t("loyaltyTitle")}</p>
            <p className="mt-2 text-sm">
              {loyalty.appliesToThisBooking
                ? t("loyaltyApplies", { percent: loyalty.discountPercent ?? rules.loyaltyPercent })
                : t("loyaltyProgress", { visits: loyalty.visitsInCurrentCycle ?? 0, threshold: loyalty.threshold ?? rules.loyaltyThreshold })}
            </p>
            <LoyaltyBar visits={loyalty.visitsInCurrentCycle ?? 0} threshold={loyalty.threshold ?? rules.loyaltyThreshold} />
          </div>
        )}
        {rules.depositCents > 0 && (
          <Alert tone="warn" title={t("depositTitle", { amount: deposit })}>
            {t("depositText", { amount: deposit, hours: Math.round(rules.paymentDeadlineMin / 60) })}
          </Alert>
        )}
        <Alert title={t("cancelTitle")}>
          {t("cancelText", {
            refundHours: Math.round(rules.refundCutoffMin / 60),
            onlineHours: Math.round(rules.onlineCancelCutoffMin / 60),
          })}
        </Alert>
        <p className="text-xs leading-relaxed text-muted">{t("nextSteps", { hours: Math.round(rules.barberResponseMin / 60) })}</p>
      </div>
    </div>
  );
}

export function LoyaltyBar({ visits, threshold }: { visits: number; threshold: number }) {
  const t = useTranslations("booking.summary");
  const pct = Math.min(100, Math.round((visits / Math.max(1, threshold)) * 100));
  return (
    <div className="mt-3">
      <div
        role="progressbar"
        aria-valuemin={0}
        aria-valuemax={threshold}
        aria-valuenow={Math.min(visits, threshold)}
        aria-label={t("loyaltyProgress", { visits, threshold })}
        className="h-2 overflow-hidden rounded-full bg-ink/10"
      >
        <div className="h-full rounded-full bg-gradient-to-r from-gold-deep to-gold" style={{ width: `${pct}%` }} />
      </div>
      <p className="mt-1.5 text-xs text-muted tabular-nums">
        {Math.min(visits, threshold)}/{threshold}
      </p>
    </div>
  );
}
