"use client";

import { useState } from "react";
import { useSearchParams } from "next/navigation";
import { useLocale, useTranslations } from "next-intl";
import { Link, useRouter } from "@/i18n/navigation";
import { api, ApiError, call } from "@/lib/api/client";
import { TENANT } from "@/lib/api/config";
import { useErrorText } from "@/lib/api/useErrorText";
import { useAuth } from "@/lib/auth/AuthProvider";
import { Alert } from "../ui/Feedback";
import { Checkbox, Field, Select } from "../ui/Field";

/** Login for everybody (customers → account, barbers/admins → staff area). `?next=` returns afterwards. */
export function LoginForm({ staff = false }: { staff?: boolean }) {
  const t = useTranslations("account.login");
  const errorText = useErrorText();
  const { login } = useAuth();
  const router = useRouter();
  const search = useSearchParams();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  return (
    <form
      className="card space-y-4 p-6 sm:p-8"
      onSubmit={async (e) => {
        e.preventDefault();
        setBusy(true);
        setError(null);
        try {
          const user = await login(email, password);
          const next = search.get("next");
          const isStaff = user.role === "BARBER" || user.role === "ADMIN";
          router.push(next && next.startsWith("/") && !next.startsWith("//") ? next : isStaff ? "/admin" : "/account");
        } catch (err) {
          setError(err);
        } finally {
          setBusy(false);
        }
      }}
    >
      <Field label={t("email")} type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
      <Field
        label={t("password")}
        type="password"
        autoComplete="current-password"
        required
        value={password}
        onChange={(e) => setPassword(e.target.value)}
      />
      {error ? <Alert tone="error">{errorText(error)}</Alert> : null}
      {error instanceof ApiError && error.code === "auth.email_not_verified" && <ResendVerification email={email} />}
      <button type="submit" className="btn btn-primary w-full sm:w-auto" disabled={busy} aria-busy={busy}>
        {t("button")}
      </button>
      <div className="flex flex-wrap gap-x-6 gap-y-2 text-sm">
        <Link href="/account/forgot-password" className="text-gold-text underline underline-offset-2">
          {t("forgot")}
        </Link>
        {!staff && (
          <Link href="/account/register" className="text-gold-text underline underline-offset-2">
            {t("register")}
          </Link>
        )}
      </div>
    </form>
  );
}

function ResendVerification({ email }: { email: string }) {
  const t = useTranslations("account.login");
  const [sent, setSent] = useState(false);
  return sent ? (
    <p className="text-sm text-muted">{t("resent")}</p>
  ) : (
    <button
      type="button"
      className="btn btn-ghost btn-sm"
      onClick={async () => {
        await call(() =>
          api.POST("/api/public/{tenant}/auth/resend-verification", { params: { path: { tenant: TENANT } }, body: { email } }),
        ).catch(() => undefined);
        setSent(true);
      }}
    >
      {t("resend")}
    </button>
  );
}

export function RegisterForm() {
  const t = useTranslations("account.register");
  const locale = useLocale();
  const errorText = useErrorText();
  const [f, setF] = useState({ name: "", email: "", phone: "", password: "", lang: locale, consent: false });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [done, setDone] = useState<string | null>(null);

  if (done)
    return (
      <div className="card p-6 sm:p-8">
        <Alert tone="success" title={t("doneTitle")}>
          {done}
        </Alert>
        <p className="mt-4 text-sm text-muted">{t("doneHint")}</p>
      </div>
    );

  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setF({ ...f, [k]: e.target instanceof HTMLInputElement && e.target.type === "checkbox" ? e.target.checked : e.target.value });

  return (
    <form
      className="card grid gap-4 p-6 sm:grid-cols-2 sm:p-8"
      onSubmit={async (e) => {
        e.preventDefault();
        const errs: Record<string, string> = {};
        if (f.name.trim().length < 2) errs.name = t("errors.name");
        if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(f.email)) errs.email = t("errors.email");
        if (!/^\+?[0-9 ()/-]{6,30}$/.test(f.phone)) errs.phone = t("errors.phone");
        if (f.password.length < 10) errs.password = t("errors.password");
        if (!f.consent) errs.consent = t("errors.consent");
        setErrors(errs);
        if (Object.keys(errs).length) return;
        setBusy(true);
        setError(null);
        try {
          const r = await call(() =>
            api.POST("/api/public/{tenant}/auth/register", {
              params: { path: { tenant: TENANT } },
              body: { ...f, name: f.name.trim(), email: f.email.trim(), phone: f.phone.trim() },
            }),
          );
          setDone(r?.message ?? "");
        } catch (err) {
          if (err instanceof ApiError && Object.keys(err.fieldErrors).length) setErrors(err.fieldErrors);
          else setError(err);
        } finally {
          setBusy(false);
        }
      }}
    >
      <Field className="sm:col-span-2" label={t("name")} autoComplete="name" value={f.name} onChange={set("name")} error={errors.name} />
      <Field label={t("email")} type="email" autoComplete="email" value={f.email} onChange={set("email")} error={errors.email} />
      <Field label={t("phone")} type="tel" autoComplete="tel" value={f.phone} onChange={set("phone")} error={errors.phone} />
      <Field
        label={t("password")}
        type="password"
        autoComplete="new-password"
        value={f.password}
        onChange={set("password")}
        error={errors.password}
        hint={t("passwordHint")}
      />
      <Select label={t("lang")} value={f.lang} onChange={set("lang")}>
        <option value="de">Deutsch</option>
        <option value="en">English</option>
      </Select>
      <Checkbox
        className="sm:col-span-2"
        checked={f.consent}
        onChange={set("consent")}
        error={errors.consent}
        label={t.rich("consent", {
          link: (c) => (
            <Link href="/datenschutz" target="_blank" className="text-gold-text underline underline-offset-2">
              {c}
            </Link>
          ),
        })}
      />
      {error ? (
        <Alert tone="error" className="sm:col-span-2">
          {errorText(error)}
        </Alert>
      ) : null}
      <div className="flex flex-wrap items-center gap-4 sm:col-span-2">
        <button type="submit" className="btn btn-primary" disabled={busy} aria-busy={busy}>
          {t("button")}
        </button>
        <Link href="/account/login" className="text-sm text-gold-text underline underline-offset-2">
          {t("haveAccount")}
        </Link>
      </div>
    </form>
  );
}

export function ForgotForm() {
  const t = useTranslations("account.forgot");
  const errorText = useErrorText();
  const [email, setEmail] = useState("");
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);
  return (
    <form
      className="card space-y-4 p-6 sm:p-8"
      onSubmit={async (e) => {
        e.preventDefault();
        setBusy(true);
        setError(null);
        try {
          const r = await call(() =>
            api.POST("/api/public/{tenant}/auth/forgot-password", { params: { path: { tenant: TENANT } }, body: { email } }),
          );
          setMsg(r?.message ?? "");
        } catch (err) {
          setError(err);
        } finally {
          setBusy(false);
        }
      }}
    >
      {msg ? (
        <Alert tone="success">{msg}</Alert>
      ) : (
        <>
          <Field label={t("email")} type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
          {error ? <Alert tone="error">{errorText(error)}</Alert> : null}
          <button type="submit" className="btn btn-primary" disabled={busy} aria-busy={busy}>
            {t("button")}
          </button>
        </>
      )}
      <Link href="/account/login" className="block text-sm text-gold-text underline underline-offset-2">
        {t("back")}
      </Link>
    </form>
  );
}
