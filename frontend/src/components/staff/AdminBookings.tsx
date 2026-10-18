"use client";

import { useCallback, useEffect, useState } from "react";
import { useTranslations } from "next-intl";
import { api, ApiError, call } from "@/lib/api/client";
import { TENANT } from "@/lib/api/config";
import type { AdminBooking, Service, StaffMember } from "@/lib/api/types";
import { useErrorText } from "@/lib/api/useErrorText";
import { addDays, shopDay } from "@/lib/format";
import { Alert, Skeleton } from "../ui/Feedback";
import { Field, Select, TextArea } from "../ui/Field";
import { StaffBookingCard } from "./BookingActions";

const STATUSES = ["", "PENDING_BARBER", "AWAITING_PAYMENT", "CONFIRMED", "COMPLETED", "DECLINED", "EXPIRED", "CANCELLED", "NO_SHOW"];

/** Admin: all bookings with filters + manual bookings (walk-in / phone, no deposit). */
export function AdminBookings() {
  const t = useTranslations("staff.bookings");
  const ts = useTranslations("status");
  const errorText = useErrorText();
  const today = shopDay(new Date());
  const [filter, setFilter] = useState({ from: today, to: addDays(today, 14), status: "", q: "" });
  const [items, setItems] = useState<AdminBooking[] | null>(null);
  const [total, setTotal] = useState(0);
  const [error, setError] = useState<unknown>(null);

  const load = useCallback(() => {
    call(() =>
      api.GET("/api/admin/bookings", {
        params: {
          query: {
            from: filter.from || undefined,
            to: filter.to || undefined,
            status: (filter.status || undefined) as never,
            q: filter.q || undefined,
            size: 100,
          },
        },
      }),
    ).then(
      (r) => {
        setItems(r?.items ?? []);
        setTotal(r?.totalItems ?? 0);
      },
      setError,
    );
  }, [filter]);
  useEffect(load, [load]);

  return (
    <div className="grid gap-8 lg:grid-cols-[1fr_24rem]">
      <section>
        <h1 className="display mb-4 text-3xl sm:text-4xl">{t("title")}</h1>
        <div className="mb-5 grid gap-3 sm:grid-cols-4">
          <Field label={t("from")} type="date" value={filter.from} onChange={(e) => setFilter({ ...filter, from: e.target.value })} />
          <Field label={t("to")} type="date" value={filter.to} onChange={(e) => setFilter({ ...filter, to: e.target.value })} />
          <Select label={t("status")} value={filter.status} onChange={(e) => setFilter({ ...filter, status: e.target.value })}>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {s ? ts(s) : t("all")}
              </option>
            ))}
          </Select>
          <Field label={t("search")} value={filter.q} onChange={(e) => setFilter({ ...filter, q: e.target.value })} />
        </div>
        {error ? <Alert tone="error">{errorText(error)}</Alert> : null}
        {!items ? (
          <Skeleton className="h-64" />
        ) : (
          <>
            <p className="mb-3 text-sm text-muted">{t("count", { count: total })}</p>
            <div className="space-y-3">
              {items.map((b) => (
                <StaffBookingCard key={b.id} booking={b} onChanged={load} withDate />
              ))}
            </div>
          </>
        )}
      </section>
      <ManualBooking onCreated={load} />
    </div>
  );
}

function ManualBooking({ onCreated }: { onCreated: () => void }) {
  const t = useTranslations("staff.manual");
  const errorText = useErrorText();
  const [services, setServices] = useState<Service[]>([]);
  const [staff, setStaff] = useState<StaffMember[]>([]);
  const [f, setF] = useState({
    serviceId: "",
    staffId: "",
    date: shopDay(new Date()),
    time: "10:00",
    name: "",
    phone: "",
    email: "",
    source: "WALK_IN",
    note: "",
  });
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const path = { tenant: TENANT };
    call(() => api.GET("/api/public/{tenant}/services", { params: { path } })).then((s) => setServices(s ?? []), () => undefined);
    call(() => api.GET("/api/public/{tenant}/staff", { params: { path } })).then((s) => setStaff(s ?? []), () => undefined);
  }, []);

  return (
    <form
      className="card h-fit space-y-4 p-5"
      onSubmit={async (e) => {
        e.preventDefault();
        setBusy(true);
        setMsg(null);
        try {
          // local wall time in the shop's timezone → ISO with offset
          const start = toShopIso(f.date, f.time);
          await call(() =>
            api.POST("/api/admin/bookings", {
              body: {
                serviceIds: [f.serviceId],
                staffId: f.staffId,
                start,
                customerName: f.name,
                customerPhone: f.phone || undefined,
                customerEmail: f.email || undefined,
                note: f.note || undefined,
                source: f.source as "WALK_IN",
              },
            }),
          );
          setMsg({ ok: true, text: t("created") });
          setF({ ...f, name: "", phone: "", email: "", note: "" });
          onCreated();
        } catch (err) {
          setMsg({ ok: false, text: err instanceof ApiError && Object.values(err.fieldErrors)[0] ? Object.values(err.fieldErrors)[0] : errorText(err) });
        } finally {
          setBusy(false);
        }
      }}
    >
      <h2 className="display text-2xl">{t("title")}</h2>
      <p className="text-xs text-muted">{t("hint")}</p>
      <Select label={t("service")} required value={f.serviceId} onChange={(e) => setF({ ...f, serviceId: e.target.value })}>
        <option value="">–</option>
        {services.map((s) => (
          <option key={s.id} value={s.id}>
            {s.name} ({s.durationMin} min)
          </option>
        ))}
      </Select>
      <Select label={t("barber")} required value={f.staffId} onChange={(e) => setF({ ...f, staffId: e.target.value })}>
        <option value="">–</option>
        {staff.map((s) => (
          <option key={s.id} value={s.id}>
            {s.name}
          </option>
        ))}
      </Select>
      <div className="grid grid-cols-2 gap-3">
        <Field label={t("date")} type="date" required value={f.date} onChange={(e) => setF({ ...f, date: e.target.value })} />
        <Field label={t("time")} type="time" required value={f.time} onChange={(e) => setF({ ...f, time: e.target.value })} />
      </div>
      <Select label={t("source")} value={f.source} onChange={(e) => setF({ ...f, source: e.target.value })}>
        <option value="WALK_IN">{t("walkIn")}</option>
        <option value="PHONE">{t("phoneBooking")}</option>
      </Select>
      <Field label={t("name")} required value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} />
      <Field label={t("phone")} type="tel" value={f.phone} onChange={(e) => setF({ ...f, phone: e.target.value })} />
      <Field label={t("email")} type="email" value={f.email} onChange={(e) => setF({ ...f, email: e.target.value })} hint={t("emailHint")} />
      <TextArea label={t("note")} value={f.note} maxLength={500} onChange={(e) => setF({ ...f, note: e.target.value })} />
      {msg && <Alert tone={msg.ok ? "success" : "error"}>{msg.text}</Alert>}
      <button type="submit" className="btn btn-primary w-full" disabled={busy}>
        {t("button")}
      </button>
    </form>
  );
}

/** "2026-10-20" + "10:00" in Europe/Berlin → "2026-10-20T10:00:00+02:00". */
export function toShopIso(date: string, hhmm: string, tz = "Europe/Berlin") {
  const guess = new Date(`${date}T${hhmm}:00Z`);
  const parts = new Intl.DateTimeFormat("en-US", { timeZone: tz, timeZoneName: "longOffset" }).formatToParts(guess);
  const off = parts.find((p) => p.type === "timeZoneName")?.value.replace("GMT", "") || "+00:00";
  return `${date}T${hhmm}:00${off === "" ? "+00:00" : off}`;
}
