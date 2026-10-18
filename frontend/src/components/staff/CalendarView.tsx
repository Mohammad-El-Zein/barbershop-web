"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { api, call } from "@/lib/api/client";
import type { AdminBooking, StaffMember } from "@/lib/api/types";
import { TENANT } from "@/lib/api/config";
import { useErrorText } from "@/lib/api/useErrorText";
import { useAuth } from "@/lib/auth/AuthProvider";
import { addDays, dayParts, mondayOf, shopDay, time } from "@/lib/format";
import { Alert, Skeleton } from "../ui/Feedback";
import { StaffBookingCard } from "./BookingActions";

/** Bookings that no longer hold a slot: hidden in the calendar unless asked for. */
const CLOSED = new Set(["CANCELLED", "DECLINED", "EXPIRED"]);

/**
 * Barber: own day/week calendar + pending requests. Admin: the same for the whole
 * shop (filterable by barber), from the admin booking list.
 */
export function CalendarView() {
  const t = useTranslations("staff.calendar");
  const tStatus = useTranslations("status");
  const locale = useLocale();
  const errorText = useErrorText();
  const { user } = useAuth();
  const admin = user?.role === "ADMIN";
  const [view, setView] = useState<"day" | "week">("day");
  const [day, setDay] = useState(() => shopDay(new Date()));
  const [staffFilter, setStaffFilter] = useState("");
  const [staff, setStaff] = useState<StaffMember[]>([]);
  const [bookings, setBookings] = useState<AdminBooking[] | null>(null);
  const [requests, setRequests] = useState<AdminBooking[] | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [showClosed, setShowClosed] = useState(false);

  const from = view === "day" ? day : mondayOf(day);
  const to = view === "day" ? day : addDays(from, 6);

  /** Calendar + open requests; state is only set when the data arrives. */
  const load = useCallback(() => {
    const fetchAll = async () => {
      if (admin) {
        const [list, pending] = await Promise.all([
          call(() =>
            api.GET("/api/admin/bookings", {
              params: { query: { from, to, staffId: staffFilter || undefined, size: 200, sort: "startTime" } },
            }),
          ),
          call(() => api.GET("/api/admin/bookings", { params: { query: { status: "PENDING_BARBER", size: 100 } } })),
        ]);
        return { list: list?.items ?? [], pending: pending?.items ?? [] };
      }
      const [list, pending] = await Promise.all([
        call(() => api.GET("/api/barber/calendar", { params: { query: { date: day, view } } })),
        call(() => api.GET("/api/barber/requests")),
      ]);
      return { list: list ?? [], pending: pending ?? [] };
    };
    return fetchAll().then(
      ({ list, pending }) => {
        setBookings(list);
        setRequests(pending);
        setError(null);
      },
      (e) => setError(e),
    );
  }, [admin, from, to, day, view, staffFilter]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (!admin) return;
    call(() => api.GET("/api/public/{tenant}/staff", { params: { path: { tenant: TENANT } } })).then(
      (s) => setStaff(s ?? []),
      () => undefined,
    );
  }, [admin]);

  const days = useMemo(() => {
    const out: string[] = [];
    for (let d = from; d <= to; d = addDays(d, 1)) out.push(d);
    return out;
  }, [from, to]);
  const inRange = (bookings ?? []).filter((b) => !CLOSED.has(b.status ?? "") || showClosed);
  const closedCount = (bookings ?? []).filter((b) => CLOSED.has(b.status ?? "")).length;
  const onDay = (d: string) => inRange.filter((b) => shopDay(new Date(b.start!)) === d);

  return (
    <div className="grid gap-8 lg:grid-cols-[1fr_22rem]">
      <section aria-labelledby="cal-h">
        <div className="mb-4 flex flex-wrap items-center gap-3">
          <h1 id="cal-h" className="display mr-auto text-3xl sm:text-4xl">
            {t("title")}
          </h1>
          <div className="inline-flex rounded-full border border-line p-1" role="radiogroup" aria-label={t("view")}>
            {(["day", "week"] as const).map((v) => (
              <button
                key={v}
                type="button"
                role="radio"
                aria-checked={view === v}
                onClick={() => setView(v)}
                className={`rounded-full px-3 py-1.5 text-xs font-semibold uppercase tracking-[0.12em] ${view === v ? "bg-gold text-[#120d05]" : "text-ink/70"}`}
              >
                {t(v)}
              </button>
            ))}
          </div>
        </div>
        <div className="mb-5 flex flex-wrap items-center gap-2">
          <button type="button" className="chip" onClick={() => setDay(addDays(day, view === "day" ? -1 : -7))} aria-label={t("prev")}>
            ‹
          </button>
          <button type="button" className="chip" onClick={() => setDay(shopDay(new Date()))}>
            {t("today")}
          </button>
          <button type="button" className="chip" onClick={() => setDay(addDays(day, view === "day" ? 1 : 7))} aria-label={t("next")}>
            ›
          </button>
          <input
            type="date"
            className="input !min-h-[2.75rem] !w-auto"
            value={day}
            onChange={(e) => e.target.value && setDay(e.target.value)}
            aria-label={t("pickDate")}
          />
          {closedCount > 0 && (
            <button type="button" className="chip" aria-pressed={showClosed} onClick={() => setShowClosed((v) => !v)}>
              {showClosed ? t("hideClosed") : t("showClosed", { count: closedCount })}
            </button>
          )}
          {admin && (
            <select className="input !min-h-[2.75rem] !w-auto" value={staffFilter} onChange={(e) => setStaffFilter(e.target.value)} aria-label={t("barber")}>
              <option value="">{t("allBarbers")}</option>
              {staff.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </select>
          )}
        </div>

        {error ? <Alert tone="error">{errorText(error)}</Alert> : null}
        {!bookings ? (
          <Skeleton className="h-80" />
        ) : view === "day" ? (
          <div className="space-y-3">
            <h2 className="text-sm font-semibold text-muted">{dayParts(day, locale).long}</h2>
            {onDay(day).length === 0 ? (
              <p className="card p-6 text-center text-muted">{t("empty")}</p>
            ) : (
              onDay(day).map((b) => <StaffBookingCard key={b.id} booking={b} onChanged={load} />)
            )}
          </div>
        ) : (
          <div className="grid gap-3 md:grid-cols-7">
            {days.map((d) => {
              const p = dayParts(d, locale);
              const list = onDay(d);
              return (
                <div key={d} className="card min-h-32 p-2">
                  <button type="button" className="w-full text-left" onClick={() => { setDay(d); setView("day"); }}>
                    <span className="block text-xs uppercase tracking-wider text-muted">{p.weekday}</span>
                    <span className="block text-lg font-semibold">{p.day}. {p.month}</span>
                  </button>
                  <ul className="mt-2 space-y-1">
                    {list.map((b) => (
                      <li key={b.id} className="rounded-md border border-line px-2 py-1 text-xs">
                        <span className="tabular-nums">{time(b.start!, locale)}</span> {b.customerName}
                        <span className="block text-[0.65rem] uppercase tracking-wider text-muted">{tStatus(b.status ?? "PENDING_BARBER")}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              );
            })}
          </div>
        )}
      </section>

      <aside aria-labelledby="req-h">
        <h2 id="req-h" className="display mb-4 text-2xl">
          {t("requests")} {requests && <span className="text-gold-text">({requests.length})</span>}
        </h2>
        {!requests ? (
          <Skeleton className="h-40" />
        ) : requests.length === 0 ? (
          <p className="card p-5 text-sm text-muted">{t("noRequests")}</p>
        ) : (
          <div className="space-y-3">
            {requests.map((b) => (
              <StaffBookingCard key={b.id} booking={b} onChanged={load} withDate />
            ))}
          </div>
        )}
      </aside>
    </div>
  );
}
