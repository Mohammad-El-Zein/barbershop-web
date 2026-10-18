"use client";

import { useCallback, useEffect, useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { api, ApiError, call } from "@/lib/api/client";
import type { CustomerRow, Policy, ServiceAdmin, StaffAdmin, Stats, TimeOff, WorkingHoursEntry } from "@/lib/api/types";
import { useErrorText } from "@/lib/api/useErrorText";
import { addDays, dateTime, dayParts, shopDay } from "@/lib/format";
import { Alert, Skeleton } from "../ui/Feedback";
import { Checkbox, Field, Select, TextArea } from "../ui/Field";
import { toShopIso } from "./AdminBookings";

function useList<T>(fn: () => Promise<T>) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<unknown>(null);
  const load = useCallback(() => {
    fn().then(setData, setError);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  useEffect(load, [load]);
  return { data, error, load };
}

function message(e: unknown, errorText: (e: unknown) => string) {
  return e instanceof ApiError && Object.values(e.fieldErrors)[0] ? `${errorText(e)} ${Object.entries(e.fieldErrors).map(([k, v]) => `${k}: ${v}`).join(", ")}` : errorText(e);
}

// ── services (DE/EN) ─────────────────────────────────────────────────────

const emptyService = { nameDe: "", nameEn: "", descriptionDe: "", descriptionEn: "", durationMin: 30, priceCents: 2500, priceFrom: false, active: true, sortOrder: 0 };

export function AdminServices() {
  const t = useTranslations("staff.services");
  const errorText = useErrorText();
  const list = useList(async () => (await call(() => api.GET("/api/admin/services"))) as ServiceAdmin[]);
  const [edit, setEdit] = useState<(typeof emptyService & { id?: string }) | null>(null);
  const [msg, setMsg] = useState<string | null>(null);

  const save = async () => {
    if (!edit) return;
    setMsg(null);
    try {
      const body = { ...edit, durationMin: Number(edit.durationMin), priceCents: Number(edit.priceCents), sortOrder: Number(edit.sortOrder) };
      if (edit.id) await call(() => api.PUT("/api/admin/services/{id}", { params: { path: { id: edit.id! } }, body }));
      else await call(() => api.POST("/api/admin/services", { body }));
      setEdit(null);
      list.load();
    } catch (e) {
      setMsg(message(e, errorText));
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between gap-3">
        <h1 className="display text-3xl sm:text-4xl">{t("title")}</h1>
        <button type="button" className="btn btn-primary btn-sm" onClick={() => setEdit({ ...emptyService })}>
          {t("add")}
        </button>
      </div>
      {list.error ? <Alert tone="error">{errorText(list.error)}</Alert> : null}
      {edit && (
        <div className="card grid gap-4 p-5 sm:grid-cols-2">
          <Field label={t("nameDe")} value={edit.nameDe} onChange={(e) => setEdit({ ...edit, nameDe: e.target.value })} />
          <Field label={t("nameEn")} value={edit.nameEn} onChange={(e) => setEdit({ ...edit, nameEn: e.target.value })} />
          <TextArea label={t("descriptionDe")} value={edit.descriptionDe ?? ""} onChange={(e) => setEdit({ ...edit, descriptionDe: e.target.value })} />
          <TextArea label={t("descriptionEn")} value={edit.descriptionEn ?? ""} onChange={(e) => setEdit({ ...edit, descriptionEn: e.target.value })} />
          <Field label={t("duration")} type="number" min={5} max={480} value={edit.durationMin} onChange={(e) => setEdit({ ...edit, durationMin: Number(e.target.value) })} />
          <Field label={t("priceCents")} type="number" min={0} value={edit.priceCents} onChange={(e) => setEdit({ ...edit, priceCents: Number(e.target.value) })} hint={t("priceHint")} />
          <Field label={t("sortOrder")} type="number" value={edit.sortOrder} onChange={(e) => setEdit({ ...edit, sortOrder: Number(e.target.value) })} />
          <div className="space-y-3 pt-6">
            <Checkbox label={t("priceFrom")} checked={edit.priceFrom} onChange={(e) => setEdit({ ...edit, priceFrom: e.target.checked })} />
            <Checkbox label={t("active")} checked={edit.active} onChange={(e) => setEdit({ ...edit, active: e.target.checked })} />
          </div>
          {msg && <Alert tone="error" className="sm:col-span-2">{msg}</Alert>}
          <div className="flex gap-3 sm:col-span-2">
            <button type="button" className="btn btn-primary btn-sm" onClick={save}>
              {t("save")}
            </button>
            <button type="button" className="btn btn-ghost btn-sm" onClick={() => setEdit(null)}>
              {t("cancel")}
            </button>
          </div>
        </div>
      )}
      {!list.data ? (
        <Skeleton className="h-48" />
      ) : (
        <ul className="space-y-2">
          {list.data.map((s) => (
            <li key={s.id} className="card flex flex-wrap items-center justify-between gap-3 p-4">
              <div>
                <p className="font-semibold">
                  {s.nameDe} / {s.nameEn} {!s.active && <span className="text-xs text-muted">({t("inactive")})</span>}
                </p>
                <p className="text-sm text-muted">
                  {s.durationMin} min · {s.priceFrom ? t("fromShort") + " " : ""}
                  {((s.priceCents ?? 0) / 100).toFixed(2)} €
                </p>
              </div>
              <div className="flex gap-2">
                <button
                  type="button"
                  className="btn btn-ghost btn-sm"
                  onClick={() =>
                    setEdit({
                      id: s.id,
                      nameDe: s.nameDe ?? "",
                      nameEn: s.nameEn ?? "",
                      descriptionDe: s.descriptionDe ?? "",
                      descriptionEn: s.descriptionEn ?? "",
                      durationMin: s.durationMin ?? 30,
                      priceCents: s.priceCents ?? 0,
                      priceFrom: !!s.priceFrom,
                      active: !!s.active,
                      sortOrder: s.sortOrder ?? 0,
                    })
                  }
                >
                  {t("edit")}
                </button>
                {s.active && (
                  <button
                    type="button"
                    className="btn btn-ghost btn-sm"
                    onClick={async () => {
                      await call(() => api.DELETE("/api/admin/services/{id}", { params: { path: { id: s.id! } } }));
                      list.load();
                    }}
                  >
                    {t("deactivate")}
                  </button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

// ── barbers: profile, services, working hours, login ─────────────────────

export function AdminStaff() {
  const t = useTranslations("staff.barbers");
  const errorText = useErrorText();
  const list = useList(async () => (await call(() => api.GET("/api/admin/staff"))) as StaffAdmin[]);
  const services = useList(async () => (await call(() => api.GET("/api/admin/services"))) as ServiceAdmin[]);
  const [open, setOpen] = useState<string | null>(null);
  const [newName, setNewName] = useState("");
  const [error, setError] = useState<string | null>(null);

  return (
    <div className="space-y-6">
      <h1 className="display text-3xl sm:text-4xl">{t("title")}</h1>
      <form
        className="card flex flex-wrap items-end gap-3 p-4"
        onSubmit={async (e) => {
          e.preventDefault();
          setError(null);
          try {
            await call(() => api.POST("/api/admin/staff", { body: { name: newName } }));
            setNewName("");
            list.load();
          } catch (err) {
            setError(message(err, errorText));
          }
        }}
      >
        <Field className="min-w-60 flex-1" label={t("newName")} value={newName} onChange={(e) => setNewName(e.target.value)} required />
        <button type="submit" className="btn btn-primary btn-sm">
          {t("add")}
        </button>
      </form>
      {error && <Alert tone="error">{error}</Alert>}
      {!list.data ? (
        <Skeleton className="h-48" />
      ) : (
        <ul className="space-y-3">
          {list.data.map((s) => (
            <li key={s.id} className="card p-4">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <p className="font-semibold">
                  {s.name} {!s.active && <span className="text-xs text-muted">({t("inactive")})</span>}
                </p>
                <button type="button" className="btn btn-ghost btn-sm" aria-expanded={open === s.id} onClick={() => setOpen(open === s.id ? null : s.id!)}>
                  {open === s.id ? t("close") : t("manage")}
                </button>
              </div>
              {open === s.id && <StaffEditor staff={s} services={services.data ?? []} onSaved={list.load} />}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

const WEEKDAYS = [1, 2, 3, 4, 5, 6, 7];

function StaffEditor({ staff, services, onSaved }: { staff: StaffAdmin; services: ServiceAdmin[]; onSaved: () => void }) {
  const t = useTranslations("staff.barbers");
  const locale = useLocale();
  const errorText = useErrorText();
  const [profile, setProfile] = useState({ name: staff.name ?? "", photoUrl: staff.photoUrl ?? "", bioDe: staff.bioDe ?? "", bioEn: staff.bioEn ?? "", active: !!staff.active });
  const [assigned, setAssigned] = useState<string[]>(staff.serviceIds ?? []);
  const [hours, setHours] = useState<WorkingHoursEntry[] | null>(null);
  const [invite, setInvite] = useState("");
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const weekdayName = (d: number) => dayParts(addDays("2026-01-05", d - 1), locale).weekday; // 2026-01-05 = Monday

  useEffect(() => {
    call(() => api.GET("/api/admin/staff/{id}/working-hours", { params: { path: { id: staff.id! } } })).then(
      (h) => setHours((h ?? []).map((x) => ({ ...x, start: x.start?.slice(0, 5), end: x.end?.slice(0, 5) }))),
      () => setHours([]),
    );
  }, [staff.id]);

  const run = async (fn: () => Promise<unknown>, ok: string) => {
    setMsg(null);
    try {
      await fn();
      setMsg({ ok: true, text: ok });
      onSaved();
    } catch (e) {
      setMsg({ ok: false, text: message(e, errorText) });
    }
  };
  const id = staff.id!;

  return (
    <div className="mt-4 space-y-6 border-t border-line pt-4">
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label={t("name")} value={profile.name} onChange={(e) => setProfile({ ...profile, name: e.target.value })} />
        <Field label={t("photoUrl")} value={profile.photoUrl} onChange={(e) => setProfile({ ...profile, photoUrl: e.target.value })} />
        <TextArea label={t("bioDe")} value={profile.bioDe} onChange={(e) => setProfile({ ...profile, bioDe: e.target.value })} />
        <TextArea label={t("bioEn")} value={profile.bioEn} onChange={(e) => setProfile({ ...profile, bioEn: e.target.value })} />
        <Checkbox label={t("active")} checked={profile.active} onChange={(e) => setProfile({ ...profile, active: e.target.checked })} />
        <div>
          <button
            type="button"
            className="btn btn-primary btn-sm"
            onClick={() =>
              run(() => call(() => api.PUT("/api/admin/staff/{id}", { params: { path: { id } }, body: { ...profile, photoUrl: profile.photoUrl || undefined } })), t("saved"))
            }
          >
            {t("saveProfile")}
          </button>
        </div>
      </div>

      <fieldset>
        <legend className="label">{t("services")}</legend>
        <div className="flex flex-wrap gap-2">
          {services.map((s) => (
            <label key={s.id} className="chip gap-2">
              <input
                type="checkbox"
                className="accent-[var(--gold)]"
                checked={assigned.includes(s.id!)}
                onChange={(e) => setAssigned(e.target.checked ? [...assigned, s.id!] : assigned.filter((x) => x !== s.id))}
              />
              {locale === "en" ? s.nameEn : s.nameDe}
            </label>
          ))}
        </div>
        <button
          type="button"
          className="btn btn-ghost btn-sm mt-3"
          onClick={() => run(() => call(() => api.PUT("/api/admin/staff/{id}/services", { params: { path: { id } }, body: { serviceIds: assigned } })), t("saved"))}
        >
          {t("saveServices")}
        </button>
      </fieldset>

      <fieldset>
        <legend className="label">{t("hours")}</legend>
        {!hours ? (
          <Skeleton className="h-24" />
        ) : (
          <div className="space-y-2">
            {hours.map((h, i) => (
              <div key={i} className="flex flex-wrap items-end gap-2">
                <Select label={t("weekday")} value={h.weekday} onChange={(e) => setHours(hours.map((x, j) => (j === i ? { ...x, weekday: Number(e.target.value) } : x)))}>
                  {WEEKDAYS.map((d) => (
                    <option key={d} value={d}>
                      {weekdayName(d)}
                    </option>
                  ))}
                </Select>
                <Field label={t("start")} type="time" value={h.start} onChange={(e) => setHours(hours.map((x, j) => (j === i ? { ...x, start: e.target.value } : x)))} />
                <Field label={t("end")} type="time" value={h.end} onChange={(e) => setHours(hours.map((x, j) => (j === i ? { ...x, end: e.target.value } : x)))} />
                <button type="button" className="btn btn-ghost btn-sm" onClick={() => setHours(hours.filter((_, j) => j !== i))} aria-label={t("removeRange")}>
                  ✕
                </button>
              </div>
            ))}
            <div className="flex gap-2">
              <button type="button" className="btn btn-ghost btn-sm" onClick={() => setHours([...hours, { weekday: 1, start: "09:00", end: "18:00" }])}>
                {t("addRange")}
              </button>
              <button
                type="button"
                className="btn btn-primary btn-sm"
                onClick={() => run(() => call(() => api.PUT("/api/admin/staff/{id}/working-hours", { params: { path: { id } }, body: { entries: hours } })), t("saved"))}
              >
                {t("saveHours")}
              </button>
            </div>
          </div>
        )}
      </fieldset>

      <form
        className="flex flex-wrap items-end gap-3"
        onSubmit={(e) => {
          e.preventDefault();
          run(() => call(() => api.POST("/api/admin/staff/{id}/account", { params: { path: { id } }, body: { email: invite, lang: locale } })), t("invited"));
        }}
      >
        <Field className="min-w-60 flex-1" label={t("loginEmail")} type="email" value={invite} onChange={(e) => setInvite(e.target.value)} hint={t("loginHint")} required />
        <button type="submit" className="btn btn-outline-gold">
          {t("invite")}
        </button>
      </form>
      {msg && <Alert tone={msg.ok ? "success" : "error"}>{msg.text}</Alert>}
    </div>
  );
}

// ── time off ─────────────────────────────────────────────────────────────

export function AdminTimeOff() {
  const t = useTranslations("staff.timeOff");
  const locale = useLocale();
  const errorText = useErrorText();
  const list = useList(async () => (await call(() => api.GET("/api/admin/time-off", { params: { query: {} } }))) as TimeOff[]);
  const staff = useList(async () => (await call(() => api.GET("/api/admin/staff"))) as StaffAdmin[]);
  const today = shopDay(new Date());
  const [f, setF] = useState({ staffId: "", fromDate: today, fromTime: "00:00", toDate: addDays(today, 1), toTime: "00:00", reason: "" });
  const [msg, setMsg] = useState<string | null>(null);

  return (
    <div className="space-y-6">
      <h1 className="display text-3xl sm:text-4xl">{t("title")}</h1>
      <form
        className="card grid gap-3 p-5 sm:grid-cols-3"
        onSubmit={async (e) => {
          e.preventDefault();
          setMsg(null);
          try {
            await call(() =>
              api.POST("/api/admin/time-off", {
                body: {
                  staffId: f.staffId || undefined,
                  start: toShopIso(f.fromDate, f.fromTime),
                  end: toShopIso(f.toDate, f.toTime),
                  reason: f.reason || undefined,
                },
              }),
            );
            list.load();
          } catch (err) {
            setMsg(message(err, errorText));
          }
        }}
      >
        <Select label={t("who")} value={f.staffId} onChange={(e) => setF({ ...f, staffId: e.target.value })}>
          <option value="">{t("wholeShop")}</option>
          {(staff.data ?? []).map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </Select>
        <Field label={t("fromDate")} type="date" value={f.fromDate} onChange={(e) => setF({ ...f, fromDate: e.target.value })} />
        <Field label={t("fromTime")} type="time" value={f.fromTime} onChange={(e) => setF({ ...f, fromTime: e.target.value })} />
        <Field label={t("reason")} value={f.reason} onChange={(e) => setF({ ...f, reason: e.target.value })} />
        <Field label={t("toDate")} type="date" value={f.toDate} onChange={(e) => setF({ ...f, toDate: e.target.value })} />
        <Field label={t("toTime")} type="time" value={f.toTime} onChange={(e) => setF({ ...f, toTime: e.target.value })} />
        {msg && <Alert tone="error" className="sm:col-span-3">{msg}</Alert>}
        <div className="sm:col-span-3">
          <button type="submit" className="btn btn-primary btn-sm">
            {t("add")}
          </button>
        </div>
      </form>
      {!list.data ? (
        <Skeleton className="h-32" />
      ) : list.data.length === 0 ? (
        <p className="card p-5 text-sm text-muted">{t("empty")}</p>
      ) : (
        <ul className="space-y-2">
          {list.data.map((o) => (
            <li key={o.id} className="card flex flex-wrap items-center justify-between gap-3 p-4">
              <div>
                <p className="font-semibold">{o.staffName ?? t("wholeShop")}</p>
                <p className="text-sm text-muted">
                  {dateTime(o.start!, locale)} – {dateTime(o.end!, locale)} {o.reason && `· ${o.reason}`}
                </p>
              </div>
              <button
                type="button"
                className="btn btn-ghost btn-sm"
                onClick={async () => {
                  await call(() => api.DELETE("/api/admin/time-off/{id}", { params: { path: { id: o.id! } } }));
                  list.load();
                }}
              >
                {t("remove")}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

// ── customers ────────────────────────────────────────────────────────────

export function AdminCustomers() {
  const t = useTranslations("staff.customers");
  const locale = useLocale();
  const errorText = useErrorText();
  const [q, setQ] = useState("");
  const [rows, setRows] = useState<CustomerRow[] | null>(null);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    const id = setTimeout(() => {
      call(() => api.GET("/api/admin/customers", { params: { query: { q: q || undefined, size: 200 } } })).then(
        (r) => setRows(r?.items ?? []),
        setError,
      );
    }, 250);
    return () => clearTimeout(id);
  }, [q]);

  return (
    <div className="space-y-6">
      <h1 className="display text-3xl sm:text-4xl">{t("title")}</h1>
      <Field label={t("search")} value={q} onChange={(e) => setQ(e.target.value)} className="max-w-sm" />
      {error ? <Alert tone="error">{errorText(error)}</Alert> : null}
      {!rows ? (
        <Skeleton className="h-64" />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[40rem] text-left text-sm">
            <thead className="text-xs uppercase tracking-wider text-muted">
              <tr>
                <th className="py-2 pr-3">{t("name")}</th>
                <th className="py-2 pr-3">{t("contact")}</th>
                <th className="py-2 pr-3">{t("type")}</th>
                <th className="py-2 pr-3 text-right">{t("visits")}</th>
                <th className="py-2 pr-3 text-right">{t("cycle")}</th>
                <th className="py-2">{t("last")}</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r, i) => (
                <tr key={r.userId ?? r.email ?? i} className="border-t border-line">
                  <td className="py-2.5 pr-3 font-semibold">{r.name}</td>
                  <td className="py-2.5 pr-3 text-muted">
                    {r.email}
                    {r.phone && <span className="block">{r.phone}</span>}
                  </td>
                  <td className="py-2.5 pr-3">{r.registered ? t("account") : t("guest")}</td>
                  <td className="py-2.5 pr-3 text-right tabular-nums">{r.completedVisits}</td>
                  <td className="py-2.5 pr-3 text-right tabular-nums">
                    {r.registered ? `${r.visitsInCurrentCycle}${r.nextDiscountAvailable ? " ★" : ""}` : "–"}
                  </td>
                  <td className="py-2.5 text-muted">{r.lastBookingAt ? dateTime(r.lastBookingAt, locale) : "–"}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="mt-3 text-xs text-muted">{t("legend")}</p>
        </div>
      )}
    </div>
  );
}

// ── stats + settings ─────────────────────────────────────────────────────

export function AdminStats() {
  const t = useTranslations("staff.stats");
  const locale = useLocale();
  const errorText = useErrorText();
  const today = shopDay(new Date());
  const [range, setRange] = useState({ from: addDays(today, -29), to: addDays(today, 30) });
  const [stats, setStats] = useState<Stats | null>(null);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    call(() => api.GET("/api/admin/stats", { params: { query: range } })).then((s) => setStats(s as Stats), setError);
  }, [range]);

  const max = Math.max(1, ...(stats?.perDay ?? []).map((d) => (d.booked ?? 0) + (d.cancelled ?? 0)));

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-end gap-3">
        <h1 className="display mr-auto text-3xl sm:text-4xl">{t("title")}</h1>
        <Field label={t("from")} type="date" value={range.from} onChange={(e) => setRange({ ...range, from: e.target.value })} />
        <Field label={t("to")} type="date" value={range.to} onChange={(e) => setRange({ ...range, to: e.target.value })} />
      </div>
      {error ? <Alert tone="error">{errorText(error)}</Alert> : null}
      {!stats ? (
        <Skeleton className="h-64" />
      ) : (
        <>
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="card p-5">
              <p className="label">{t("booked")}</p>
              <p className="display text-5xl text-gold-text">{stats.totalBooked}</p>
            </div>
            <div className="card p-5">
              <p className="label">{t("lost")}</p>
              <p className="display text-5xl">{stats.totalCancelled}</p>
            </div>
          </div>
          <section className="card p-5">
            <h2 className="label">{t("perDay")}</h2>
            {(stats.perDay ?? []).length === 0 ? (
              <p className="text-sm text-muted">{t("empty")}</p>
            ) : (
              <ul className="space-y-1.5">
                {(stats.perDay ?? []).map((d) => (
                  <li key={d.date} className="grid grid-cols-[7rem_1fr_4rem] items-center gap-3 text-sm">
                    <span className="text-muted">{dayParts(d.date!, locale).weekday} {dayParts(d.date!, locale).day}. {dayParts(d.date!, locale).month}</span>
                    <span className="flex h-3 overflow-hidden rounded-full bg-ink/10" aria-hidden="true">
                      <span className="bg-gold" style={{ width: `${((d.booked ?? 0) / max) * 100}%` }} />
                      <span className="bg-ink/35" style={{ width: `${((d.cancelled ?? 0) / max) * 100}%` }} />
                    </span>
                    <span className="text-right tabular-nums">
                      {d.booked}/{d.cancelled}
                    </span>
                  </li>
                ))}
              </ul>
            )}
            <p className="mt-3 text-xs text-muted">{t("legend")}</p>
          </section>
          <section className="card p-5">
            <h2 className="label">{t("perService")}</h2>
            <ul className="space-y-1 text-sm">
              {(stats.perService ?? []).map((s) => (
                <li key={s.serviceId} className="flex justify-between border-b border-line py-1.5 last:border-0">
                  <span>{s.name}</span>
                  <span className="tabular-nums">
                    {s.booked} / {s.cancelled}
                  </span>
                </li>
              ))}
            </ul>
          </section>
        </>
      )}
      <AdminSettings />
    </div>
  );
}

const POLICY_FIELDS: (keyof Policy)[] = [
  "depositCents",
  "refundCutoffMin",
  "onlineCancelCutoffMin",
  "barberResponseMin",
  "paymentDeadlineMin",
  "deadlineBeforeStartMin",
  "minLeadMin",
  "maxDaysAhead",
  "slotStepMin",
  "bufferMin",
  "loyaltyThreshold",
  "loyaltyPercent",
  "autoCompleteAfterMin",
  "reminderBeforeMin",
];

function AdminSettings() {
  const t = useTranslations("staff.settings");
  const errorText = useErrorText();
  const [p, setP] = useState<Policy | null>(null);
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);
  useEffect(() => {
    call(() => api.GET("/api/admin/settings")).then((x) => setP(x as Policy), () => undefined);
  }, []);
  if (!p) return <Skeleton className="h-48" />;
  return (
    <form
      className="card space-y-4 p-5"
      onSubmit={async (e) => {
        e.preventDefault();
        setMsg(null);
        try {
          setP((await call(() => api.PUT("/api/admin/settings", { body: p }))) as Policy);
          setMsg({ ok: true, text: t("saved") });
        } catch (err) {
          setMsg({ ok: false, text: message(err, errorText) });
        }
      }}
    >
      <h2 className="display text-2xl">{t("title")}</h2>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {POLICY_FIELDS.map((k) => (
          <Field key={k} label={t(k)} type="number" min={0} value={p[k]} onChange={(e) => setP({ ...p, [k]: Number(e.target.value) })} />
        ))}
      </div>
      {msg && <Alert tone={msg.ok ? "success" : "error"}>{msg.text}</Alert>}
      <button type="submit" className="btn btn-primary btn-sm">
        {t("save")}
      </button>
    </form>
  );
}
