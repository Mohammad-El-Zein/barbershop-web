"use client";

import { useCallback, useEffect, useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { api, ApiError, call } from "@/lib/api/client";
import type {
  BarberLogin,
  CampaignStats,
  CustomerRow,
  OutboxProblem,
  Policy,
  ServiceAdmin,
  ShopProfile,
  StaffAdmin,
  Stats,
  TimeOff,
  WorkingHoursEntry,
} from "@/lib/api/types";
import { useErrorText } from "@/lib/api/useErrorText";
import { addDays, dateTime, dayParts, money, shopDay } from "@/lib/format";
import { ConfirmDialog } from "../ui/Dialog";
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
              {open === s.id && (
                <StaffEditor
                  staff={s}
                  services={services.data ?? []}
                  onSaved={list.load}
                  onDeleted={() => {
                    setOpen(null);
                    list.load();
                  }}
                />
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

const WEEKDAYS = [1, 2, 3, 4, 5, 6, 7];

function StaffEditor({ staff, services, onSaved, onDeleted }: { staff: StaffAdmin; services: ServiceAdmin[]; onSaved: () => void; onDeleted: () => void }) {
  const t = useTranslations("staff.barbers");
  const locale = useLocale();
  const errorText = useErrorText();
  const [profile, setProfile] = useState({ name: staff.name ?? "", photoUrl: staff.photoUrl ?? "", bioDe: staff.bioDe ?? "", bioEn: staff.bioEn ?? "", active: !!staff.active });
  const [assigned, setAssigned] = useState<string[]>(staff.serviceIds ?? []);
  const [hours, setHours] = useState<WorkingHoursEntry[] | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);
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
        <div>
          <Checkbox label={t("active")} checked={profile.active} onChange={(e) => setProfile({ ...profile, active: e.target.checked })} />
          <p className="mt-1 text-xs text-muted">{t("activeHint")}</p>
        </div>
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

      {msg && <Alert tone={msg.ok ? "success" : "error"}>{msg.text}</Alert>}

      <BarberLoginPanel staffId={id} />

      <section aria-labelledby={`del-${id}`} className="border-t border-line pt-4">
        <h3 id={`del-${id}`} className="label">
          {t("deleteTitle")}
        </h3>
        <p className="mb-3 text-xs text-muted">{t("deleteHint")}</p>
        <button type="button" className="btn btn-ghost btn-sm text-red-700" onClick={() => {
            setDeleteError(null);
            setConfirmDelete(true);
          }}>
          {t("delete")}
        </button>
        {deleteError && (
          <div className="mt-3">
            <Alert tone="error">{deleteError}</Alert>
          </div>
        )}
      </section>
      <ConfirmDialog
        open={confirmDelete}
        title={t("deleteConfirmTitle", { name: staff.name ?? "" })}
        confirmLabel={t("delete")}
        danger
        busy={deleting}
        onConfirm={async () => {
          setDeleting(true);
          try {
            await call(() => api.DELETE("/api/admin/staff/{id}/permanently", { params: { path: { id } } }));
            setConfirmDelete(false);
            onDeleted();
          } catch (e) {
            setConfirmDelete(false);
            setDeleteError(e instanceof ApiError && e.code === "staff.has_bookings" ? t("hasBookings") : message(e, errorText));
          } finally {
            setDeleting(false);
          }
        }}
        onClose={() => setConfirmDelete(false)}
      >
        <p>{t("deleteConfirm")}</p>
      </ConfirmDialog>
    </div>
  );
}

/**
 * The barber's login: state and email (the admin may correct it), creating it (invite, own or
 * generated start password) and resetting the password. A start password is shown exactly once,
 * right after it was set; passwords are never loaded from the server.
 */
function BarberLoginPanel({ staffId }: { staffId: string }) {
  const t = useTranslations("staff.barbers");
  const locale = useLocale();
  const errorText = useErrorText();
  const [login, setLogin] = useState<BarberLogin | null>(null);
  const [email, setEmail] = useState("");
  const [mode, setMode] = useState<"invite" | "generate" | "own">("invite");
  const [ownPassword, setOwnPassword] = useState("");
  const [shown, setShown] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const [confirmReset, setConfirmReset] = useState(false);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);

  const load = useCallback(() => {
    call(() => api.GET("/api/admin/staff/{id}/account", { params: { path: { id: staffId } } })).then(
      (l) => {
        setLogin(l as BarberLogin);
        setEmail(l?.email ?? "");
      },
      (e) => setMsg({ ok: false, text: message(e, errorText) }),
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [staffId]);
  useEffect(load, [load]);

  const act = async (fn: () => Promise<unknown>, ok?: string) => {
    setMsg(null);
    setBusy(true);
    try {
      await fn();
      if (ok) setMsg({ ok: true, text: ok });
      load();
    } catch (e) {
      setMsg({ ok: false, text: message(e, errorText) });
    } finally {
      setBusy(false);
    }
  };
  const show = (password: string | undefined) => {
    setCopied(false);
    setShown(password ?? null);
  };

  if (!login) return <Skeleton className="h-24" />;
  const state = login.state ?? "NONE";

  return (
    <section aria-labelledby={`login-${staffId}`} className="space-y-3 border-t border-line pt-4">
      <h3 id={`login-${staffId}`} className="label">
        {t("login")}
      </h3>
      <p className="text-sm" data-testid="login-state">
        <span className="font-semibold">{t(`loginState.${state}`)}</span>
        {login.email && <> · {login.email}</>}
        {state === "ACTIVE" && login.mustChangePassword && <span className="text-muted"> · {t("mustChange")}</span>}
      </p>

      {state === "NONE" ? (
        <form
          className="space-y-3"
          onSubmit={(e) => {
            e.preventDefault();
            act(async () => {
              const created = await call(() =>
                api.POST("/api/admin/staff/{id}/account", {
                  params: { path: { id: staffId } },
                  body: { email, lang: locale, password: mode === "own" ? ownPassword : undefined, generatePassword: mode === "generate" || undefined },
                }),
              );
              setOwnPassword("");
              if (mode === "generate") show(created?.startPassword);
              if (mode === "own") show(ownPassword);
            }, mode === "invite" ? t("invited") : t("accountCreated"));
          }}
        >
          <Field label={t("loginEmail")} type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
          <fieldset className="flex flex-wrap gap-x-5 gap-y-2 text-sm">
            <legend className="sr-only">{t("passwordMode")}</legend>
            {(["invite", "generate", "own"] as const).map((m) => (
              <label key={m} className="flex items-center gap-2">
                <input type="radio" name={`mode-${staffId}`} className="accent-[var(--gold)]" checked={mode === m} onChange={() => setMode(m)} />
                {t(`mode.${m}`)}
              </label>
            ))}
          </fieldset>
          {mode === "own" && (
            <Field
              label={t("initialPassword")}
              type="password"
              autoComplete="new-password"
              minLength={10}
              value={ownPassword}
              onChange={(e) => setOwnPassword(e.target.value)}
              hint={t("initialPasswordHint")}
              required
            />
          )}
          <button type="submit" className="btn btn-outline-gold btn-sm" disabled={busy}>
            {t("invite")}
          </button>
        </form>
      ) : (
        <div className="flex flex-wrap items-end gap-3">
          <form
            className="flex flex-1 flex-wrap items-end gap-3"
            onSubmit={(e) => {
              e.preventDefault();
              act(() => call(() => api.PUT("/api/admin/staff/{id}/account", { params: { path: { id: staffId } }, body: { email } })), t("emailSaved"));
            }}
          >
            <Field className="min-w-60 flex-1" label={t("loginEmail")} type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
            <button type="submit" className="btn btn-ghost btn-sm" disabled={busy || email === login.email}>
              {t("saveEmail")}
            </button>
          </form>
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => setConfirmReset(true)} disabled={busy}>
            {t("resetPassword")}
          </button>
        </div>
      )}

      {shown && (
        <div className="card space-y-2 border-gold/60 p-4" role="status">
          <p className="text-sm font-semibold">{t("startPassword")}</p>
          <div className="flex flex-wrap items-center gap-3">
            <code className="rounded bg-ink/5 px-3 py-2 font-mono text-base tracking-wider" data-testid="start-password">
              {shown}
            </code>
            <button
              type="button"
              className="btn btn-ghost btn-sm"
              onClick={() => navigator.clipboard?.writeText(shown).then(() => setCopied(true), () => setCopied(false))}
            >
              {copied ? t("copied") : t("copy")}
            </button>
          </div>
          <p className="text-xs text-muted">{t("shownOnce")}</p>
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => show(undefined)}>
            {t("hidePassword")}
          </button>
        </div>
      )}
      {msg && <Alert tone={msg.ok ? "success" : "error"}>{msg.text}</Alert>}

      <ConfirmDialog
        open={confirmReset}
        title={t("resetConfirmTitle")}
        confirmLabel={t("resetPassword")}
        busy={busy}
        onConfirm={() =>
          act(async () => {
            const r = await call(() => api.POST("/api/admin/staff/{id}/account/reset-password", { params: { path: { id: staffId } } }));
            setConfirmReset(false);
            show(r?.startPassword);
          }, t("passwordReset"))
        }
        onClose={() => setConfirmReset(false)}
      >
        <p>{t("resetConfirm")}</p>
      </ConfirmDialog>
    </section>
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
        // focusable + labelled: keyboard users can scroll the table sideways on small screens
        <div className="overflow-x-auto" tabIndex={0} role="region" aria-label={t("title")}>
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
      <CampaignTable range={range} />
      <OutboxProblems />
      <AdminSettings />
      <AdminProfile />
    </div>
  );
}

/** Emails that could not be delivered (retrying / given up), with a manual retry. */
function OutboxProblems() {
  const t = useTranslations("staff.outbox");
  const locale = useLocale();
  const errorText = useErrorText();
  const [rows, setRows] = useState<OutboxProblem[] | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const load = useCallback(
    () => call(() => api.GET("/api/admin/outbox")).then((r) => setRows((r ?? []) as OutboxProblem[]), setError),
    [],
  );
  useEffect(() => {
    load();
  }, [load]);
  const retry = async (id: string) => {
    setNotice(null);
    try {
      await call(() => api.POST("/api/admin/outbox/{id}/retry", { params: { path: { id } } }));
      setNotice(t("retried"));
      await load();
    } catch (e) {
      setError(e);
    }
  };
  return (
    <section className="card p-5" aria-labelledby="outbox-h">
      <h2 id="outbox-h" className="label">
        {t("title")}
      </h2>
      <p className="mt-1 text-xs text-muted">{t("lead")}</p>
      {error ? <Alert tone="error">{errorText(error)}</Alert> : null}
      {notice && <Alert tone="success">{notice}</Alert>}
      {!rows ? (
        <Skeleton className="mt-4 h-20" />
      ) : rows.length === 0 ? (
        <p className="mt-4 text-sm text-muted">{t("empty")}</p>
      ) : (
        <ul className="mt-3 divide-y divide-line">
          {rows.map((r) => (
            <li key={r.id} className="flex flex-wrap items-center justify-between gap-3 py-3 text-sm">
              <div className="min-w-0">
                <p className="truncate font-semibold">{r.subject}</p>
                <p className="truncate text-xs text-muted">
                  {r.recipient} · {r.kind} · {t(`status.${r.status as "RETRY" | "DEAD"}`)} · {t("attempts", { count: r.attempts ?? 0 })}
                  {r.status === "RETRY" && r.nextAttemptAt ? ` · ${t("nextAttempt", { time: dateTime(r.nextAttemptAt, locale) })}` : ""}
                </p>
                {r.lastError && <p className="mt-0.5 truncate text-xs text-muted">{r.lastError}</p>}
              </div>
              <button type="button" className="btn btn-ghost btn-sm" onClick={() => retry(r.id!)}>
                {t("retry")}
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

/** "Which ad produced which booking": online bookings per first-touch campaign (by booking date). */
function CampaignTable({ range }: { range: { from: string; to: string } }) {
  const t = useTranslations("staff.campaigns");
  const locale = useLocale();
  const errorText = useErrorText();
  const [data, setData] = useState<CampaignStats | null>(null);
  const [error, setError] = useState<unknown>(null);
  useEffect(() => {
    call(() => api.GET("/api/admin/stats/campaigns", { params: { query: range } })).then(
      (s) => setData(s as CampaignStats),
      setError,
    );
  }, [range]);
  const label = (v?: string) =>
    v === "(direct)" ? t("direct") : v === "(none)" || v === "(not set)" ? "–" : v?.startsWith("lp:") ? t("landingPage", { slug: v.slice(3) }) : v;
  return (
    <section className="card p-5" aria-labelledby="campaigns-h">
      <h2 id="campaigns-h" className="label">
        {t("title")}
      </h2>
      <p className="mt-1 text-xs text-muted">{t("lead")}</p>
      {error ? <Alert tone="error">{errorText(error)}</Alert> : null}
      {!data ? (
        <Skeleton className="mt-4 h-32" />
      ) : (data.campaigns ?? []).length === 0 ? (
        <p className="mt-4 text-sm text-muted">{t("empty")}</p>
      ) : (
        <>
          <p className="mt-3 text-sm">
            {t("summary", { total: data.totalBookings ?? 0, attributed: data.attributedBookings ?? 0 })}
          </p>
          <div className="mt-3 overflow-x-auto" tabIndex={0} role="region" aria-label={t("title")}>
            <table className="w-full min-w-[34rem] text-left text-sm">
              <caption className="sr-only">{t("title")}</caption>
              <thead className="text-xs uppercase tracking-wider text-muted">
                <tr className="border-b border-line">
                  <th scope="col" className="py-2 pr-3 font-semibold">{t("source")}</th>
                  <th scope="col" className="py-2 pr-3 font-semibold">{t("medium")}</th>
                  <th scope="col" className="py-2 pr-3 font-semibold">{t("campaign")}</th>
                  <th scope="col" className="py-2 pr-3 text-right font-semibold">{t("bookings")}</th>
                  <th scope="col" className="py-2 pr-3 text-right font-semibold">{t("confirmed")}</th>
                  <th scope="col" className="py-2 text-right font-semibold">{t("revenue")}</th>
                </tr>
              </thead>
              <tbody>
                {(data.campaigns ?? []).map((c) => (
                  <tr key={`${c.source}|${c.medium}|${c.campaign}`} className="border-b border-line last:border-0">
                    <td className="py-2 pr-3">{label(c.source)}</td>
                    <td className="py-2 pr-3">{label(c.medium)}</td>
                    <td className="py-2 pr-3">{label(c.campaign)}</td>
                    <td className="py-2 pr-3 text-right tabular-nums">{c.bookings}</td>
                    <td className="py-2 pr-3 text-right tabular-nums">{c.confirmed}</td>
                    <td className="py-2 text-right tabular-nums">{money(c.confirmedRevenueCents ?? 0, locale, data.currency ?? "EUR")}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </section>
  );
}

const PROFILE_FIELDS: { key: keyof ShopProfile; type?: string; autoComplete?: string }[] = [
  { key: "street", autoComplete: "street-address" },
  { key: "postalCode", autoComplete: "postal-code" },
  { key: "city", autoComplete: "address-level2" },
  { key: "country", autoComplete: "country" },
  { key: "phone", type: "tel", autoComplete: "tel" },
  { key: "email", type: "email", autoComplete: "email" },
  { key: "priceRange" },
];

/** Public address + contact (website structured data). Opening hours come from the barbers' working hours. */
function AdminProfile() {
  const t = useTranslations("staff.shopProfile");
  const errorText = useErrorText();
  const [p, setP] = useState<ShopProfile | null>(null);
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);
  useEffect(() => {
    call(() => api.GET("/api/admin/settings/profile")).then((x) => setP((x ?? {}) as ShopProfile), () => undefined);
  }, []);
  if (!p) return <Skeleton className="h-48" />;
  return (
    <form
      className="card space-y-4 p-5"
      onSubmit={async (e) => {
        e.preventDefault();
        setMsg(null);
        try {
          setP((await call(() => api.PUT("/api/admin/settings/profile", { body: p }))) as ShopProfile);
          setMsg({ ok: true, text: t("saved") });
        } catch (err) {
          setMsg({ ok: false, text: message(err, errorText) });
        }
      }}
    >
      <h2 className="display text-2xl">{t("title")}</h2>
      <p className="text-sm text-muted">{t("lead")}</p>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {PROFILE_FIELDS.map(({ key, type, autoComplete }) => (
          <Field
            key={key}
            label={t(key)}
            type={type ?? "text"}
            autoComplete={autoComplete}
            value={p[key] ?? ""}
            onChange={(e) => setP({ ...p, [key]: e.target.value })}
            hint={key === "country" ? t("countryHint") : key === "priceRange" ? t("priceRangeHint") : undefined}
          />
        ))}
      </div>
      {msg && <Alert tone={msg.ok ? "success" : "error"}>{msg.text}</Alert>}
      <button type="submit" className="btn btn-primary btn-sm">
        {t("save")}
      </button>
    </form>
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
