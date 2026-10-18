"use client";

import { useEffect, useState } from "react";
import { useParams, useSearchParams } from "next/navigation";
import { useLocale, useTranslations } from "next-intl";
import { api, call } from "@/lib/api/client";
import type { Decision } from "@/lib/api/types";
import { dateTime, dateTimeRange, minutes } from "@/lib/format";
import { Alert, Row, Skeleton, StatusBadge } from "../ui/Feedback";
import { TextArea } from "../ui/Field";
import { ErrorBox, LinkProblem } from "./LinkState";

/**
 * /barber/requests/[token]?action=accept|decline — the barber's email buttons
 * land here. The page only SHOWS the request (email scanners open links); the
 * single confirm button sends the POST.
 */
export function DecisionView() {
  const { token } = useParams<{ token: string }>();
  const search = useSearchParams();
  const t = useTranslations("decision");
  const tS = useTranslations("booking.summary");
  const locale = useLocale();
  const [action, setAction] = useState<"accept" | "decline">(search.get("action") === "decline" ? "decline" : "accept");
  const [data, setData] = useState<Decision | null>(null);
  const [loadError, setLoadError] = useState<unknown>(null);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [done, setDone] = useState<"accept" | "decline" | null>(null);

  useEffect(() => {
    call(() => api.GET("/api/public/barber-decision/{token}", { params: { path: { token } } })).then(
      (d) => setData(d as Decision),
      setLoadError,
    );
  }, [token]);

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      const d =
        action === "accept"
          ? await call(() => api.POST("/api/public/barber-decision/{token}/accept", { params: { path: { token } } }))
          : await call(() =>
              api.POST("/api/public/barber-decision/{token}/decline", {
                params: { path: { token } },
                body: { reason: reason.trim() || undefined },
              }),
            );
      setData(d as Decision);
      setDone(action);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  if (loadError) return <LinkProblem error={loadError} />;
  if (!data)
    return (
      <div className="space-y-4">
        <Skeleton className="h-16 w-2/3" />
        <Skeleton className="h-80" />
      </div>
    );

  return (
    <div className="space-y-6">
      <header>
        <p className="eyebrow">{t("eyebrow")}</p>
        <h1 className="display mt-3 text-4xl sm:text-5xl">
          {done ? t(done === "accept" ? "acceptedTitle" : "declinedTitle") : t("title")}
        </h1>
        <p className="mt-3 text-muted">
          {done
            ? t(done === "accept" ? "acceptedText" : "declinedText")
            : data.respondUntil && t("respondUntil", { time: dateTime(data.respondUntil, locale) })}
        </p>
      </header>

      <div className="card p-5">
        <div className="mb-3 flex items-center justify-between gap-3">
          <p className="font-semibold">{data.customerName}</p>
          <StatusBadge status={data.status} />
        </div>
        <dl>
          <Row label={tS("services")}>{(data.services ?? []).map((s) => s.name).join(" + ")}</Row>
          <Row label={tS("barber")}>{data.staff?.name}</Row>
          <Row label={tS("when")}>{dateTimeRange(data.start!, data.end!, locale)}</Row>
          <Row label={tS("duration")}>{minutes(data.durationMin ?? 0, locale)}</Row>
          <Row label={tS("note")}>{data.note || "–"}</Row>
          <Row label={t("loyalty")}>{data.loyaltyNote}</Row>
          <Row label={tS("total")}>{data.price?.total?.formatted}</Row>
        </dl>
      </div>

      <ErrorBox error={error} />

      {!done && (data.actions ?? []).length > 0 && (
        <div className="card p-5">
          <div role="radiogroup" aria-label={t("choose")} className="mb-4 inline-flex rounded-full border border-line p-1">
            {(["accept", "decline"] as const).map((a) => (
              <button
                key={a}
                type="button"
                role="radio"
                aria-checked={action === a}
                onClick={() => setAction(a)}
                className={`rounded-full px-4 py-2 text-xs font-semibold uppercase tracking-[0.14em] ${action === a ? "bg-gold text-[#120d05]" : "text-ink/70"}`}
              >
                {t(a)}
              </button>
            ))}
          </div>
          {action === "decline" && (
            <TextArea
              className="mb-4"
              label={t("reason")}
              hint={t("reasonHint")}
              value={reason}
              maxLength={500}
              onChange={(e) => setReason(e.target.value)}
            />
          )}
          <p className="mb-4 text-sm text-muted">{t(action === "accept" ? "acceptHint" : "declineHint")}</p>
          <button
            type="button"
            className={`btn btn-lg ${action === "accept" ? "btn-primary" : "btn-danger"}`}
            onClick={submit}
            disabled={busy}
            aria-busy={busy}
          >
            {t(action === "accept" ? "confirmAccept" : "confirmDecline")}
          </button>
        </div>
      )}

      {done && <Alert tone="success">{t(done === "accept" ? "acceptedNext" : "declinedNext")}</Alert>}
    </div>
  );
}
