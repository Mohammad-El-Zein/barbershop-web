"use client";

import { useLocale, useTranslations } from "next-intl";
import { useMemo, useState } from "react";
import type { DayView, StaffMember } from "@/lib/api/types";
import { addDays, dayParts, shopDay } from "@/lib/format";
import { useErrorText } from "@/lib/api/useErrorText";
import { Alert, Avatar, Skeleton } from "../ui/Feedback";
import { useDay, useDays } from "./data";

export type SlotChoice = { staffId: string | null; staffName: string | null; start: string; end: string; time: string };

const STRIP = 14;

/**
 * Step 2: a date strip (days without free slots are disabled), then the chosen
 * day for ALL barbers side by side — one column per barber with every slot of
 * their schedule (taken ones greyed out) — plus an "any barber" column.
 * Fixed-height columns and skeletons keep the layout from jumping.
 */
export function SlotStep({
  serviceIds,
  maxDaysAhead,
  staff,
  day,
  onDay,
  choice,
  onChoose,
  refreshNonce,
  preferStaffId,
}: {
  serviceIds: string[];
  maxDaysAhead: number;
  staff: StaffMember[] | null;
  day: string | null;
  onDay: (d: string) => void;
  choice: SlotChoice | null;
  onChoose: (c: SlotChoice) => void;
  refreshNonce: number;
  /** "Rebook": this barber's column comes first. */
  preferStaffId?: string;
}) {
  const t = useTranslations("booking.slots");
  const locale = useLocale();
  const errorText = useErrorText();
  const today = useMemo(() => shopDay(new Date()), []);
  const lastDay = addDays(today, maxDaysAhead);
  const [page, setPage] = useState(0);
  const from = addDays(today, page * STRIP);
  const to = addDays(from, STRIP - 1) > lastDay ? lastDay : addDays(from, STRIP - 1);
  const strip = useMemo(() => {
    const out: string[] = [];
    for (let d = from; d <= to; d = addDays(d, 1)) out.push(d);
    return out;
  }, [from, to]);

  // days with slots (whole horizon window, for the strip and the "next free day" hint)
  const days = useDays(serviceIds, from, addDays(from, 61) > lastDay ? lastDay : addDays(from, 61), refreshNonce);
  const dayView = useDay(serviceIds, day, refreshNonce);
  const nextFree = day && days.data ? [...days.data].sort().find((d) => d > day) : undefined;

  return (
    <div>
      {/* date strip */}
      <div className="flex items-center gap-2">
        <button
          type="button"
          className="chip !px-3"
          onClick={() => setPage((p) => p - 1)}
          disabled={page === 0}
          aria-label={t("prevDays")}
        >
          ‹
        </button>
        <div role="radiogroup" aria-label={t("date")} className="flex min-w-0 flex-1 gap-2 overflow-x-auto pb-1">
          {strip.map((d) => {
            const p = dayParts(d, locale);
            const free = days.data ? days.data.has(d) : true;
            const on = d === day;
            return (
              <button
                key={d}
                type="button"
                role="radio"
                aria-checked={on}
                aria-label={p.long + (days.data && !free ? ` – ${t("noSlotsShort")}` : "")}
                disabled={!!days.data && !free}
                onClick={() => onDay(d)}
                className="chip !min-h-[4.25rem] w-[3.75rem] shrink-0 flex-col !px-0 !no-underline"
              >
                <span className="text-[0.65rem] uppercase tracking-wider opacity-75">{p.weekday}</span>
                <span className="text-lg font-semibold leading-tight">{p.day}</span>
                <span className="text-[0.6rem] uppercase opacity-60">{p.month}</span>
              </button>
            );
          })}
        </div>
        <button
          type="button"
          className="chip !px-3"
          onClick={() => setPage((p) => p + 1)}
          disabled={to >= lastDay}
          aria-label={t("nextDays")}
        >
          ›
        </button>
      </div>

      <div className="mt-6" aria-live="polite" aria-busy={dayView.loading}>
        {!day ? (
          <p className="text-sm text-muted">{t("pickDay")}</p>
        ) : dayView.error ? (
          <Alert tone="error">{errorText(dayView.error)}</Alert>
        ) : dayView.loading || !dayView.data ? (
          <Columns>
            {[0, 1, 2].map((i) => (
              <div key={i} className="space-y-2">
                <Skeleton className="h-12" />
                <Skeleton className="h-64" />
              </div>
            ))}
          </Columns>
        ) : (dayView.data.anyBarber ?? []).length === 0 ? (
          <div className="rounded-2xl border border-line p-6 text-center">
            <p className="font-semibold">{t("noSlots")}</p>
            {nextFree ? (
              <button type="button" className="btn btn-outline-gold mt-4" onClick={() => onDay(nextFree)}>
                {t("nextFree", { day: dayParts(nextFree, locale).long })}
              </button>
            ) : (
              <p className="mt-2 text-sm text-muted">{t("noSlotsSoon")}</p>
            )}
          </div>
        ) : (
          <DayColumns day={dayView.data} staff={staff} choice={choice} onChoose={onChoose} preferStaffId={preferStaffId} />
        )}
      </div>
    </div>
  );
}

function Columns({ children }: { children: React.ReactNode }) {
  return (
    <div className="-mx-1 flex snap-x gap-3 overflow-x-auto px-1 pb-2 [&>*]:w-[9.5rem] [&>*]:shrink-0 [&>*]:snap-start sm:[&>*]:w-auto sm:[&>*]:min-w-[10rem] sm:[&>*]:flex-1">
      {children}
    </div>
  );
}

function DayColumns({
  day,
  staff,
  choice,
  onChoose,
  preferStaffId,
}: {
  preferStaffId?: string;
  day: DayView;
  staff: StaffMember[] | null;
  choice: SlotChoice | null;
  onChoose: (c: SlotChoice) => void;
}) {
  const t = useTranslations("booking.slots");
  const photo = (id?: string) => staff?.find((s) => s.id === id)?.photoUrl;
  const isChosen = (staffId: string | null, start?: string) =>
    !!choice && choice.staffId === staffId && choice.start === start;

  return (
    <Columns>
      {/* any barber */}
      <section aria-labelledby="col-any" className="flex flex-col">
        <header className="flex h-14 items-center gap-3">
          <span
            aria-hidden="true"
            className="flex h-10 w-10 items-center justify-center rounded-full border border-gold/60 text-gold-text"
          >
            ✦
          </span>
          <h3 id="col-any" className="text-sm font-semibold leading-tight">
            {t("anyBarber")}
            <span className="block text-xs font-normal text-muted">{t("anyBarberHint")}</span>
          </h3>
        </header>
        <SlotList>
          {(day.anyBarber ?? []).map((s) => (
            <li key={s.start}>
              <button
                type="button"
                className="chip w-full"
                aria-pressed={isChosen(null, s.start)}
                aria-label={`${s.time} – ${t("anyBarber")}`}
                onClick={() => onChoose({ staffId: null, staffName: null, start: s.start!, end: s.end!, time: s.time! })}
              >
                {s.time}
              </button>
            </li>
          ))}
        </SlotList>
      </section>

      {[...(day.barbers ?? [])]
        .sort((a, b) => Number(b.staffId === preferStaffId) - Number(a.staffId === preferStaffId))
        .map((b) => (
        <section key={b.staffId} aria-labelledby={`col-${b.staffId}`} className="flex flex-col">
          <header className="flex h-14 items-center gap-3">
            <Avatar name={b.name ?? ""} photoUrl={b.photoUrl ?? photo(b.staffId)} size={40} />
            <h3 id={`col-${b.staffId}`} className="text-sm font-semibold leading-tight">
              {b.name}
              <span className="block text-xs font-normal text-muted">
                {t("freeCount", { count: (b.slots ?? []).filter((s) => s.available).length })}
              </span>
            </h3>
          </header>
          <SlotList>
            {(b.slots ?? []).map((s) => (
              <li key={s.start}>
                <button
                  type="button"
                  className="chip w-full"
                  disabled={!s.available}
                  aria-pressed={isChosen(b.staffId!, s.start)}
                  aria-label={`${s.time} – ${b.name}${s.available ? "" : ` (${t("taken")})`}`}
                  onClick={() =>
                    onChoose({ staffId: b.staffId!, staffName: b.name ?? null, start: s.start!, end: s.end!, time: s.time! })
                  }
                >
                  {s.time}
                </button>
              </li>
            ))}
          </SlotList>
        </section>
      ))}
    </Columns>
  );
}

function SlotList({ children }: { children: React.ReactNode }) {
  return (
    <ul className="grid h-64 content-start gap-2 overflow-y-auto overscroll-contain rounded-xl border border-line p-2 sm:h-72" data-lenis-prevent>
      {children}
    </ul>
  );
}
