"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { useAuth } from "@/lib/auth/AuthProvider";
import { useErrorText } from "@/lib/api/useErrorText";
import { Alert } from "../ui/Feedback";
import { Checkbox, Field, TextArea } from "../ui/Field";

export type Guest = { name: string; email: string; phone: string };
export type DetailErrors = Partial<Record<"customerName" | "customerEmail" | "customerPhone" | "consent" | "note", string>>;

export const NOTE_MAX = 500;
const PHONE = /^\+?[0-9 ()/-]{6,30}$/;
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** Client-side check mirroring the backend rules (the backend validates again). */
export function validateDetails(
  t: (k: string) => string,
  asCustomer: boolean,
  guest: Guest,
  consent: boolean,
  note: string,
  accountHasPhone: boolean,
): DetailErrors {
  const e: DetailErrors = {};
  if (note.length > NOTE_MAX) e.note = t("noteTooLong");
  if (asCustomer) {
    if (!accountHasPhone && !PHONE.test(guest.phone.trim())) e.customerPhone = t("phoneInvalid");
    return e;
  }
  if (guest.name.trim().length < 2) e.customerName = t("nameRequired");
  if (!EMAIL.test(guest.email.trim())) e.customerEmail = t("emailInvalid");
  if (!PHONE.test(guest.phone.trim())) e.customerPhone = t("phoneInvalid");
  if (!consent) e.consent = t("consentRequired");
  return e;
}

/**
 * Step 3: note + who is booking. Logged-in customers book with their account
 * (loyalty applies); everybody else continues as guest or logs in inline.
 */
export function DetailsStep({
  note,
  onNote,
  guest,
  onGuest,
  consent,
  onConsent,
  errors,
}: {
  note: string;
  onNote: (s: string) => void;
  guest: Guest;
  onGuest: (g: Guest) => void;
  consent: boolean;
  onConsent: (b: boolean) => void;
  errors: DetailErrors;
}) {
  const t = useTranslations("booking.details");
  const { status, user } = useAuth();
  const asCustomer = status === "authenticated" && user?.role === "CUSTOMER";
  const [mode, setMode] = useState<"guest" | "login">("guest");

  return (
    <div className="space-y-6">
      <TextArea
        label={t("note")}
        value={note}
        maxLength={NOTE_MAX}
        onChange={(e) => onNote(e.target.value)}
        placeholder={t("notePlaceholder")}
        error={errors.note}
        hint={t("noteCount", { count: note.length, max: NOTE_MAX })}
      />

      {asCustomer ? (
        <div className="card p-4">
          <p className="text-sm text-muted">{t("bookingAs")}</p>
          <p className="mt-1 font-semibold">
            {user?.name} <span className="font-normal text-muted">· {user?.email}</span>
          </p>
          {!user?.phone && (
            <Field
              className="mt-4"
              label={t("phone")}
              type="tel"
              autoComplete="tel"
              value={guest.phone}
              onChange={(e) => onGuest({ ...guest, phone: e.target.value })}
              error={errors.customerPhone}
            />
          )}
        </div>
      ) : (
        <div>
          <div role="tablist" aria-label={t("who")} className="mb-4 inline-flex rounded-full border border-line p-1">
            {(["guest", "login"] as const).map((m) => (
              <button
                key={m}
                type="button"
                role="tab"
                aria-selected={mode === m}
                onClick={() => setMode(m)}
                className={`rounded-full px-4 py-2 text-xs font-semibold uppercase tracking-[0.14em] transition-colors ${
                  mode === m ? "bg-gold text-[#120d05]" : "text-ink/70 hover:text-ink"
                }`}
              >
                {t(m === "guest" ? "asGuest" : "login")}
              </button>
            ))}
          </div>

          {mode === "guest" ? (
            <div className="grid gap-4 sm:grid-cols-2">
              <Field
                className="sm:col-span-2"
                label={t("name")}
                autoComplete="name"
                value={guest.name}
                onChange={(e) => onGuest({ ...guest, name: e.target.value })}
                error={errors.customerName}
              />
              <Field
                label={t("email")}
                type="email"
                autoComplete="email"
                value={guest.email}
                onChange={(e) => onGuest({ ...guest, email: e.target.value })}
                error={errors.customerEmail}
                hint={t("emailHint")}
              />
              <Field
                label={t("phone")}
                type="tel"
                autoComplete="tel"
                value={guest.phone}
                onChange={(e) => onGuest({ ...guest, phone: e.target.value })}
                error={errors.customerPhone}
              />
              <Checkbox
                className="sm:col-span-2"
                checked={consent}
                onChange={(e) => onConsent(e.target.checked)}
                error={errors.consent}
                label={t.rich("consent", {
                  link: (c) => (
                    <Link href="/datenschutz" target="_blank" className="text-gold-text underline underline-offset-2">
                      {c}
                    </Link>
                  ),
                  terms: (c) => (
                    <Link href="/agb" target="_blank" className="text-gold-text underline underline-offset-2">
                      {c}
                    </Link>
                  ),
                })}
              />
              <p className="text-xs text-muted sm:col-span-2">
                {t.rich("registerHint", {
                  link: (c) => (
                    <Link href="/account/register" target="_blank" className="text-gold-text underline underline-offset-2">
                      {c}
                    </Link>
                  ),
                })}
              </p>
            </div>
          ) : (
            <InlineLogin onDone={() => setMode("guest")} />
          )}
        </div>
      )}
    </div>
  );
}

function InlineLogin({ onDone }: { onDone: () => void }) {
  const t = useTranslations("booking.details");
  const { login } = useAuth();
  const errorText = useErrorText();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  return (
    <form
      className="grid gap-4 sm:grid-cols-2"
      onSubmit={async (e) => {
        e.preventDefault();
        setBusy(true);
        setError(null);
        try {
          await login(email, password);
          onDone();
        } catch (err) {
          setError(errorText(err));
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
      {error && (
        <Alert tone="error" className="sm:col-span-2">
          {error}
        </Alert>
      )}
      <div className="flex flex-wrap items-center gap-4 sm:col-span-2">
        <button type="submit" className="btn btn-outline-gold" disabled={busy}>
          {t("loginButton")}
        </button>
        <Link href="/account/forgot-password" target="_blank" className="text-xs text-muted underline underline-offset-2">
          {t("forgot")}
        </Link>
      </div>
    </form>
  );
}
