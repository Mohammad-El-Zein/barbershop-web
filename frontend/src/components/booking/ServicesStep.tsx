"use client";

import { useLocale, useTranslations } from "next-intl";
import type { Service } from "@/lib/api/types";
import { minutes, money } from "@/lib/format";
import { Skeleton } from "../ui/Feedback";

/** Step 1: one or more services, with duration, price and a running total. */
export function ServicesStep({
  services,
  loading,
  selected,
  onToggle,
}: {
  services: Service[] | null;
  loading: boolean;
  selected: string[];
  onToggle: (id: string) => void;
}) {
  const t = useTranslations("booking.services");
  const locale = useLocale();
  const chosen = (services ?? []).filter((s) => selected.includes(s.id!));
  const totalCents = chosen.reduce((sum, s) => sum + (s.price?.amountCents ?? 0), 0);
  const totalMin = chosen.reduce((sum, s) => sum + (s.durationMin ?? 0), 0);
  const from = chosen.some((s) => s.price?.from);

  return (
    <div>
      <p className="mb-4 text-sm text-muted">{t("hint")}</p>
      <fieldset>
        <legend className="sr-only">{t("legend")}</legend>
        <ul className="grid gap-3 sm:grid-cols-2">
          {loading || !services
            ? [0, 1, 2].map((i) => (
                <li key={i}>
                  <Skeleton className="h-[7.25rem]" />
                </li>
              ))
            : services.map((s) => {
                const on = selected.includes(s.id!);
                return (
                  <li key={s.id}>
                    <label
                      className={`flex h-full min-h-[7.25rem] cursor-pointer gap-4 rounded-2xl border p-4 transition-colors focus-within:outline focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-gold-text ${
                        on ? "border-gold bg-gold/10" : "border-line hover:border-gold/60"
                      }`}
                    >
                      <input
                        type="checkbox"
                        className="mt-1 h-5 w-5 shrink-0 accent-[var(--gold)]"
                        checked={on}
                        onChange={() => onToggle(s.id!)}
                      />
                      <span className="flex min-w-0 flex-1 flex-col gap-1">
                        <span className="flex items-baseline justify-between gap-3">
                          <span className="display text-xl">{s.name}</span>
                          <span className="shrink-0 text-sm font-semibold text-gold-text">{s.price?.formatted}</span>
                        </span>
                        <span className="text-xs text-muted">{minutes(s.durationMin ?? 0, locale)}</span>
                        {s.description && <span className="text-sm leading-snug text-ink/75">{s.description}</span>}
                      </span>
                    </label>
                  </li>
                );
              })}
        </ul>
      </fieldset>

      {/* running total — always rendered (no layout jump), empty state when nothing is chosen */}
      <div
        className="mt-5 flex min-h-14 items-center justify-between gap-4 rounded-2xl border border-line px-4 py-3"
        aria-live="polite"
      >
        {chosen.length ? (
          <>
            <span className="text-sm text-muted">
              {t("selected", { count: chosen.length })} · {minutes(totalMin, locale)}
            </span>
            <span className="text-base font-semibold">
              {from ? t("from") + " " : ""}
              {money(totalCents, locale)}
            </span>
          </>
        ) : (
          <span className="text-sm text-muted">{t("none")}</span>
        )}
      </div>
    </div>
  );
}
