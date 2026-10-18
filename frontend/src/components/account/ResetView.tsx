"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { api, ApiError, call } from "@/lib/api/client";
import { useErrorText } from "@/lib/api/useErrorText";
import { LinkProblem } from "../links/LinkState";
import { Alert } from "../ui/Feedback";
import { Field } from "../ui/Field";

/** /account/reset-password/[token] — forgot-password link and the barber invite ("set your password"). */
export function ResetView() {
  const { token } = useParams<{ token: string }>();
  const t = useTranslations("account.reset");
  const errorText = useErrorText();
  const [pw, setPw] = useState("");
  const [pw2, setPw2] = useState("");
  const [fieldError, setFieldError] = useState<string | undefined>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [done, setDone] = useState(false);

  if (error instanceof ApiError && error.code.startsWith("token.")) return <LinkProblem error={error} />;

  return (
    <div className="card p-6 sm:p-8">
      <p className="eyebrow">{t("eyebrow")}</p>
      <h1 className="display mt-3 text-4xl">{done ? t("doneTitle") : t("title")}</h1>
      {done ? (
        <>
          <p className="mt-3 text-muted">{t("doneText")}</p>
          <Link href="/account/login" className="btn btn-primary mt-6">
            {t("toLogin")}
          </Link>
        </>
      ) : (
        <form
          className="mt-6 space-y-4"
          onSubmit={async (e) => {
            e.preventDefault();
            setFieldError(undefined);
            setError(null);
            if (pw.length < 10) return setFieldError(t("tooShort"));
            if (pw !== pw2) return setFieldError(t("mismatch"));
            setBusy(true);
            try {
              await call(() => api.POST("/api/public/auth/reset-password", { body: { token, newPassword: pw } }));
              setDone(true);
            } catch (err) {
              if (err instanceof ApiError && err.fieldErrors.password) setFieldError(err.fieldErrors.password);
              else setError(err);
            } finally {
              setBusy(false);
            }
          }}
        >
          <Field label={t("password")} type="password" autoComplete="new-password" value={pw} onChange={(e) => setPw(e.target.value)} hint={t("hint")} />
          <Field label={t("repeat")} type="password" autoComplete="new-password" value={pw2} onChange={(e) => setPw2(e.target.value)} error={fieldError} />
          {error ? <Alert tone="error">{errorText(error)}</Alert> : null}
          <button type="submit" className="btn btn-primary" disabled={busy} aria-busy={busy}>
            {t("button")}
          </button>
        </form>
      )}
    </div>
  );
}
