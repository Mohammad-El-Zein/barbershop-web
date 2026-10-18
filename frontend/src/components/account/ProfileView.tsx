"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { Link, useRouter } from "@/i18n/navigation";
import { api, ApiError, call } from "@/lib/api/client";
import type { Session, User } from "@/lib/api/types";
import { useErrorText } from "@/lib/api/useErrorText";
import { useAuth } from "@/lib/auth/AuthProvider";
import { Alert } from "../ui/Feedback";
import { ConfirmDialog } from "../ui/Dialog";
import { Field, Select } from "../ui/Field";

/** /account/profile — name, phone, language; change password; GDPR export and delete. */
export function ProfileView() {
  const t = useTranslations("account.profile");
  const { user, setUser, applySession, clear } = useAuth();
  const router = useRouter();
  const errorText = useErrorText();
  const [p, setP] = useState({ name: user?.name ?? "", phone: user?.phone ?? "", lang: user?.lang ?? "de" });
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [pw, setPw] = useState({ current: "", next: "" });
  const [pwMsg, setPwMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const [del, setDel] = useState(false);
  const [delPw, setDelPw] = useState("");
  const [delError, setDelError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const isCustomer = user?.role === "CUSTOMER";

  return (
    <div className="space-y-8">
      <header>
        <Link href="/account" className="text-sm text-gold-text underline underline-offset-2">
          ← {t("back")}
        </Link>
        <h1 className="display mt-4 text-4xl sm:text-5xl">{t("title")}</h1>
        <p className="mt-2 text-muted">{user?.email}</p>
      </header>

      <form
        className="card grid gap-4 p-5 sm:grid-cols-2 sm:p-6"
        onSubmit={async (e) => {
          e.preventDefault();
          setSaved(false);
          setError(null);
          try {
            const u = await call(() => api.PATCH("/api/me", { body: p }));
            setUser(u as User);
            setSaved(true);
          } catch (err) {
            setError(err);
          }
        }}
      >
        <h2 className="text-sm font-semibold uppercase tracking-[0.14em] text-muted sm:col-span-2">{t("data")}</h2>
        <Field label={t("name")} value={p.name} onChange={(e) => setP({ ...p, name: e.target.value })} />
        <Field label={t("phone")} type="tel" value={p.phone} onChange={(e) => setP({ ...p, phone: e.target.value })} />
        <Select label={t("lang")} value={p.lang} onChange={(e) => setP({ ...p, lang: e.target.value })} hint={t("langHint")}>
          <option value="de">Deutsch</option>
          <option value="en">English</option>
        </Select>
        <div className="flex items-end gap-4 sm:col-span-2">
          <button type="submit" className="btn btn-primary btn-sm">
            {t("save")}
          </button>
          {saved && <span className="text-sm text-gold-text" role="status">{t("saved")}</span>}
        </div>
        {error ? <Alert tone="error" className="sm:col-span-2">{errorText(error)}</Alert> : null}
      </form>

      <form
        className="card grid gap-4 p-5 sm:grid-cols-2 sm:p-6"
        onSubmit={async (e) => {
          e.preventDefault();
          setPwMsg(null);
          try {
            const s = await call(() =>
              api.POST("/api/auth/password", { body: { currentPassword: pw.current, newPassword: pw.next } }),
            );
            applySession(s as Session);
            setPw({ current: "", next: "" });
            setPwMsg({ ok: true, text: t("passwordChanged") });
          } catch (err) {
            const text = err instanceof ApiError && Object.values(err.fieldErrors)[0] ? Object.values(err.fieldErrors)[0] : errorText(err);
            setPwMsg({ ok: false, text });
          }
        }}
      >
        <h2 className="text-sm font-semibold uppercase tracking-[0.14em] text-muted sm:col-span-2">{t("password")}</h2>
        <Field label={t("currentPassword")} type="password" autoComplete="current-password" value={pw.current} onChange={(e) => setPw({ ...pw, current: e.target.value })} />
        <Field label={t("newPassword")} type="password" autoComplete="new-password" value={pw.next} onChange={(e) => setPw({ ...pw, next: e.target.value })} hint={t("passwordHint")} />
        <div className="sm:col-span-2">
          <button type="submit" className="btn btn-outline-gold">
            {t("changePassword")}
          </button>
        </div>
        {pwMsg && (
          <Alert tone={pwMsg.ok ? "success" : "error"} className="sm:col-span-2">
            {pwMsg.text}
          </Alert>
        )}
      </form>

      {isCustomer && (
        <section className="card p-5 sm:p-6">
          <h2 className="text-sm font-semibold uppercase tracking-[0.14em] text-muted">{t("privacy")}</h2>
          <p className="mt-2 text-sm text-muted">{t("exportText")}</p>
          <button
            type="button"
            className="btn btn-ghost btn-sm mt-3"
            onClick={async () => {
              const data = await call(() => api.GET("/api/me/export"));
              const url = URL.createObjectURL(new Blob([JSON.stringify(data, null, 2)], { type: "application/json" }));
              const a = Object.assign(document.createElement("a"), { href: url, download: "my-data.json" });
              a.click();
              URL.revokeObjectURL(url);
            }}
          >
            {t("export")}
          </button>
          <hr className="my-5 border-line" />
          <p className="text-sm text-muted">{t("deleteText")}</p>
          <button type="button" className="btn btn-danger btn-sm mt-3" onClick={() => setDel(true)}>
            {t("delete")}
          </button>
        </section>
      )}

      <ConfirmDialog
        open={del}
        title={t("deleteTitle")}
        confirmLabel={t("deleteConfirm")}
        danger
        busy={busy}
        onClose={() => setDel(false)}
        onConfirm={async () => {
          setBusy(true);
          setDelError(null);
          try {
            await call(() => api.POST("/api/me/delete", { body: { password: delPw } }));
            clear();
            router.push("/");
          } catch (err) {
            setDelError(err instanceof ApiError && err.fieldErrors.password ? err.fieldErrors.password : errorText(err));
          } finally {
            setBusy(false);
          }
        }}
      >
        <p>{t("deleteWarning")}</p>
        <Field label={t("currentPassword")} type="password" autoComplete="current-password" value={delPw} onChange={(e) => setDelPw(e.target.value)} error={delError ?? undefined} />
      </ConfirmDialog>
    </div>
  );
}
