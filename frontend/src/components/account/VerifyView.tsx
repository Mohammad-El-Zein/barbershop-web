"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { api, ApiError, call } from "@/lib/api/client";
import { LinkProblem } from "../links/LinkState";
import { Alert } from "../ui/Feedback";

/** /account/verify/[token] — confirming is a button (POST), opening the link changes nothing. */
export function VerifyView() {
  const { token } = useParams<{ token: string }>();
  const t = useTranslations("account.verify");
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<{ linked: number } | null>(null);
  const [error, setError] = useState<unknown>(null);

  const verify = async () => {
    setBusy(true);
    try {
      const r = await call(() => api.POST("/api/public/auth/verify-email", { body: { token } }));
      setResult({ linked: r?.linkedGuestBookings ?? 0 });
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  if (error instanceof ApiError && error.code.startsWith("token.")) return <LinkProblem error={error} />;

  return (
    <div className="card p-6 sm:p-8">
      <p className="eyebrow">{t("eyebrow")}</p>
      <h1 className="display mt-3 text-4xl">{result ? t("doneTitle") : t("title")}</h1>
      {result ? (
        <>
          <p className="mt-3 text-muted">{t("doneText")}</p>
          {result.linked > 0 && (
            <Alert tone="success" className="mt-4">
              {t("linked", { count: result.linked })}
            </Alert>
          )}
          <Link href="/account/login" className="btn btn-primary mt-6">
            {t("toLogin")}
          </Link>
        </>
      ) : (
        <>
          <p className="mt-3 text-muted">{t("text")}</p>
          {error ? <Alert tone="error" className="mt-4">{String((error as ApiError).detail || "")}</Alert> : null}
          <button type="button" className="btn btn-primary mt-6" onClick={verify} disabled={busy} aria-busy={busy}>
            {t("button")}
          </button>
        </>
      )}
    </div>
  );
}
