"use client";

import { useEffect, useRef, useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { api, ApiError, call } from "@/lib/api/client";
import { TENANT } from "@/lib/api/config";
import type { Booking } from "@/lib/api/types";
import { useErrorText } from "@/lib/api/useErrorText";
import { useAuth } from "@/lib/auth/AuthProvider";
import { currentAttribution } from "@/lib/attribution";
import { bookingParams, track } from "@/lib/tracking";
import { CAPTCHA_REQUIRED, captchaHeaders } from "@/lib/captcha";
import { Turnstile, type TurnstileHandle } from "../security/Turnstile";
import { rulesOf, useShop } from "@/lib/useShop";
import { Alert } from "../ui/Feedback";
import { useServices, useStaff } from "./data";
import { type DetailErrors, DetailsStep, type Guest, validateDetails } from "./DetailsStep";
import { ServicesStep } from "./ServicesStep";
import { type SlotChoice, SlotStep } from "./SlotStep";
import { SummaryStep } from "./SummaryStep";

const STEPS = ["services", "slot", "details", "summary"] as const;
type Step = (typeof STEPS)[number];

/**
 * The booking flow (modal or /booking page): services → date & barber →
 * details → summary → confirm. On success it hands the one-time status token
 * to `onBooked` (→ the live status page).
 */
export function BookingFlow({
  initialServiceIds = [],
  initialStaffId,
  onBooked,
  source = "unknown",
  headingLevel = "h3",
}: {
  initialServiceIds?: string[];
  initialStaffId?: string;
  onBooked: (booking: Booking) => void;
  /** Which button / page opened the flow (analytics). */
  source?: string;
  /** Step heading level: h3 inside the dialog (its title is h2), h2 on the booking page (below the h1). */
  headingLevel?: "h2" | "h3";
}) {
  const Heading = headingLevel;
  const t = useTranslations("booking");
  const locale = useLocale();
  const errorText = useErrorText();
  const { status, user } = useAuth();
  const rules = rulesOf(useShop());
  const services = useServices(locale);
  const staff = useStaff(locale);

  const [step, setStep] = useState<Step>(initialServiceIds.length ? "slot" : "services");
  const [serviceIds, setServiceIds] = useState<string[]>(initialServiceIds);
  const [day, setDay] = useState<string | null>(null);
  const [choice, setChoice] = useState<SlotChoice | null>(null);
  const [note, setNote] = useState("");
  const [guest, setGuest] = useState<Guest>({ name: "", email: "", phone: "" });
  const [consent, setConsent] = useState(false);
  const [errors, setErrors] = useState<DetailErrors>({});
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [nonce, setNonce] = useState(0);
  const headingRef = useRef<HTMLHeadingElement>(null);
  const [captcha, setCaptcha] = useState<string | null>(null);
  const turnstile = useRef<TurnstileHandle>(null);

  const asCustomer = status === "authenticated" && user?.role === "CUSTOMER";
  const chosenServices = (services.data ?? []).filter((s) => serviceIds.includes(s.id!));
  const index = STEPS.indexOf(step);

  useEffect(() => {
    track("booking_started", { source, preselected: initialServiceIds.length > 0 });
    // once per flow instance (the dialog remounts the flow for every opening)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // move focus to the step heading on every step change (screen readers + keyboard)
  const first = useRef(true);
  useEffect(() => {
    if (first.current) {
      first.current = false;
      return;
    }
    headingRef.current?.focus();
  }, [step]);

  const toggleService = (id: string) => {
    setServiceIds((ids) => (ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id]));
    setChoice(null);
  };

  const canNext =
    (step === "services" && serviceIds.length > 0) ||
    (step === "slot" && !!choice) ||
    step === "details";

  const next = () => {
    setNotice(null);
    if (step === "details") {
      const e = validateDetails((k) => t(`details.${k}`), asCustomer, guest, consent, note, !!user?.phone);
      setErrors(e);
      if (Object.keys(e).length) return;
    }
    track("booking_step_completed", { step, step_number: index + 1, source });
    setStep(STEPS[Math.min(index + 1, STEPS.length - 1)]);
  };
  const back = () => {
    setNotice(null);
    setStep(STEPS[Math.max(index - 1, 0)]);
  };

  const confirm = async () => {
    if (!choice) return;
    setBusy(true);
    setNotice(null);
    try {
      const body = {
        serviceIds,
        staffId: choice.staffId ?? undefined,
        start: choice.start,
        lang: locale,
        note: note.trim() || undefined,
        ...(asCustomer
          ? { customerPhone: user?.phone ? undefined : guest.phone.trim() }
          : {
              customerName: guest.name.trim(),
              customerEmail: guest.email.trim(),
              customerPhone: guest.phone.trim(),
              consent,
            }),
        // first-touch campaign data, only present with marketing consent
        attribution: currentAttribution(),
      };
      const booking = (await call(() =>
        api.POST("/api/public/{tenant}/bookings", { params: { path: { tenant: TENANT } }, body, headers: captchaHeaders(captcha) }),
      )) as Booking;
      track("booking_submitted", { ...bookingParams(booking), source });
      onBooked(booking);
    } catch (e) {
      const msg = errorText(e);
      if (e instanceof ApiError && (e.code === "booking.slot_taken" || e.code === "booking.slot_invalid")) {
        // somebody was faster: back to the slots, fresh data, keep everything else
        setChoice(null);
        setNonce((n) => n + 1);
        setStep("slot");
      } else if (e instanceof ApiError && Object.keys(e.fieldErrors).length) {
        setErrors(e.fieldErrors as DetailErrors);
        setStep("details");
      }
      setNotice(msg);
    } finally {
      setBusy(false);
      turnstile.current?.reset(); // tokens are single-use
    }
  };

  return (
    <div className="flex min-h-[32rem] flex-col">
      {/* stepper */}
      <ol className="mb-6 grid grid-cols-4 gap-2" aria-label={t("progress")}>
        {STEPS.map((s, i) => (
          <li key={s} aria-current={s === step ? "step" : undefined} className="min-w-0">
            <span className={`block h-1 rounded-full ${i <= index ? "bg-gold" : "bg-ink/15"}`} />
            {/* phones: labels move to the "step x of 4" line below (still read by screen readers here) */}
            <span
              className={`mt-2 block truncate text-[0.62rem] font-semibold uppercase tracking-[0.14em] sm:text-[0.68rem] ${
                i === index ? "text-gold-text max-sm:sr-only" : "text-muted max-sm:sr-only"
              }`}
            >
              {i + 1}. {t(`steps.${s}`)}
            </span>
          </li>
        ))}
      </ol>
      <p className="-mt-4 mb-5 text-[0.68rem] font-semibold uppercase tracking-[0.14em] text-gold-text sm:hidden" aria-hidden="true">
        {t("stepOf", { current: index + 1, total: STEPS.length, label: t(`steps.${step}`) })}
      </p>

      <Heading ref={headingRef} tabIndex={-1} className="display mb-4 text-2xl focus:outline-none sm:text-3xl">
        {t(`titles.${step}`)}
      </Heading>

      {notice && (
        <Alert tone="error" className="mb-4">
          {notice}
        </Alert>
      )}

      <div className="flex-1">
        {step === "services" && (
          <ServicesStep services={services.data} loading={services.loading} selected={serviceIds} onToggle={toggleService} />
        )}
        {step === "slot" && (
          <SlotStep
            serviceIds={serviceIds}
            maxDaysAhead={rules.maxDaysAhead}
            staff={staff.data}
            day={day}
            onDay={(d) => {
              setDay(d);
              setChoice(null);
            }}
            choice={choice}
            onChoose={setChoice}
            refreshNonce={nonce}
            preferStaffId={initialStaffId}
          />
        )}
        {step === "details" && (
          <DetailsStep
            note={note}
            onNote={setNote}
            guest={guest}
            onGuest={setGuest}
            consent={consent}
            onConsent={setConsent}
            errors={errors}
          />
        )}
        {step === "summary" && choice && (
          <>
            <SummaryStep services={chosenServices} choice={choice} rules={rules} note={note} />
            <div className="mt-4">
              <Turnstile ref={turnstile} onToken={setCaptcha} />
            </div>
          </>
        )}
      </div>

      {/* sticky footer: selection + back / next */}
      <div className="sticky bottom-0 -mx-5 mt-6 flex items-center justify-between gap-3 border-t border-line bg-[color-mix(in_srgb,var(--surface)_70%,var(--bg))] px-5 py-4 sm:-mx-7 sm:px-7">
        <button type="button" className="btn btn-ghost" onClick={back} disabled={index === 0 || busy}>
          {t("back")}
        </button>
        <p className="hidden min-w-0 truncate text-sm text-muted md:block" aria-live="polite">
          {choice ? `${choice.time} · ${choice.staffName ?? t("slots.anyBarber")}` : ""}
        </p>
        {step === "summary" ? (
          <button type="button" className="btn btn-primary" onClick={confirm} disabled={busy || (CAPTCHA_REQUIRED && !captcha)} aria-busy={busy}>
            {busy ? t("sending") : t("confirm")}
          </button>
        ) : (
          <button type="button" className="btn btn-primary" onClick={next} disabled={!canNext}>
            {t("next")}
          </button>
        )}
      </div>
    </div>
  );
}
