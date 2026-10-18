"use client";

import { useEffect, useRef, useState } from "react";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { captureAttribution, clearAttribution, persistAttribution } from "@/lib/attribution";
import {
  CONSENT_OPEN_EVENT,
  type ConsentChoice,
  onConsentChange,
  openConsentSettings,
  readConsent,
  saveConsent,
} from "@/lib/consent";
import { applyConsent, configuredTools } from "@/lib/tracking";

/**
 * Cookie consent banner + the glue around it (mounted once in the locale layout):
 * applies the stored decision to the tracking adapters, captures campaign
 * attribution, shows the banner while there is no decision, and re-opens it from
 * the footer ("Cookie settings"). "Accept all" and "Only necessary" are equally
 * prominent; categories can be chosen individually in the settings view.
 *
 * The panel is always server-rendered but hidden by CSS; the first-visit display is
 * switched on by <html data-consent="ask"> (set before paint by CONSENT_BOOT_SCRIPT),
 * a re-open from the footer by data-open.
 */
export function ConsentManager() {
  const t = useTranslations("consent");
  const [open, setOpen] = useState(false);
  const [details, setDetails] = useState(false);
  const [choice, setChoice] = useState<ConsentChoice>({ statistics: false, marketing: false });
  const panelRef = useRef<HTMLDivElement>(null);
  const returnFocus = useRef<HTMLElement | null>(null);

  useEffect(() => {
    const c = readConsent();
    applyConsent(c);
    captureAttribution();
    const show = (reopen: boolean) => {
      const stored = readConsent();
      setChoice({ statistics: !!stored?.statistics, marketing: !!stored?.marketing });
      setDetails(reopen);
      setOpen(true);
    };
    // no decision yet: the head script has already shown the banner (fallback if it didn't run)
    if (!c) document.documentElement.setAttribute("data-consent", "ask");
    const onOpen = () => {
      returnFocus.current = document.activeElement as HTMLElement | null;
      show(true);
    };
    window.addEventListener(CONSENT_OPEN_EVENT, onOpen);
    const off = onConsentChange((next) => {
      applyConsent(next);
      if (next.marketing) persistAttribution();
      else clearAttribution();
    });
    return () => {
      window.removeEventListener(CONSENT_OPEN_EVENT, onOpen);
      off();
    };
  }, []);

  // re-opened from the footer: move focus into the panel
  useEffect(() => {
    if (open && returnFocus.current) panelRef.current?.focus();
  }, [open]);

  const decide = (c: ConsentChoice) => {
    saveConsent(c);
    document.documentElement.removeAttribute("data-consent");
    setOpen(false);
    returnFocus.current?.focus();
    returnFocus.current = null;
  };

  const tools = (category: "statistics" | "marketing") =>
    configuredTools.filter((x) => x.category === category).map((x) => t(`tools.${x.name}`));

  return (
    <div
      ref={panelRef}
      role="dialog"
      aria-modal="false"
      data-open={open || undefined}
      aria-labelledby="consent-title"
      aria-describedby="consent-text"
      tabIndex={-1}
      onKeyDown={(e) => {
        // Escape closes only a re-opened panel (a first decision must be made explicitly)
        if (e.key === "Escape" && readConsent()) {
          document.documentElement.removeAttribute("data-consent");
          setOpen(false);
          returnFocus.current?.focus();
        }
      }}
      className="consent-panel fixed inset-x-3 bottom-3 z-[110] mx-auto max-h-[85svh] max-w-xl overflow-y-auto rounded-2xl border border-gold/40 bg-surface p-5 text-ink shadow-[0_20px_60px_rgb(0_0_0/0.45)] focus:outline-none sm:inset-x-6 sm:bottom-6"
    >
      {/* body font, not the display face: the swap from the fallback font must not change the
          panel's height (it is anchored at the bottom — a reflow would be a layout shift) */}
      <h2 id="consent-title" className="text-lg font-semibold tracking-tight">
        {t("title")}
      </h2>
      <p id="consent-text" className="mt-2 text-sm leading-relaxed text-ink/85">
        {t.rich("text", {
          link: (c) => (
            <Link href="/datenschutz#cookies" className="text-gold-text underline underline-offset-2">
              {c}
            </Link>
          ),
        })}
      </p>

      {details && (
        <fieldset className="mt-4 space-y-3">
          <legend className="sr-only">{t("categories")}</legend>
          <Category title={t("necessary.title")} text={t("necessary.text")} checked disabled />
          <Category
            title={t("statistics.title")}
            text={t("statistics.text")}
            tools={tools("statistics")}
            none={t("noTool")}
            checked={choice.statistics}
            onChange={(v) => setChoice({ ...choice, statistics: v })}
          />
          <Category
            title={t("marketing.title")}
            text={t("marketing.text")}
            tools={tools("marketing")}
            none={t("noTool")}
            checked={choice.marketing}
            onChange={(v) => setChoice({ ...choice, marketing: v })}
          />
        </fieldset>
      )}

      {/* "accept" and "reject" share one style: equal prominence */}
      <div className="mt-5 grid gap-2 sm:grid-cols-2">
        <button type="button" className="btn btn-ghost btn-sm" onClick={() => decide({ statistics: true, marketing: true })}>
          {t("acceptAll")}
        </button>
        <button type="button" className="btn btn-ghost btn-sm" onClick={() => decide({ statistics: false, marketing: false })}>
          {t("rejectAll")}
        </button>
        {details ? (
          <button type="button" className="btn btn-ghost btn-sm sm:col-span-2" onClick={() => decide(choice)}>
            {t("save")}
          </button>
        ) : (
          <button
            type="button"
            className="text-sm font-semibold text-gold-text underline underline-offset-4 sm:col-span-2"
            aria-expanded={false}
            onClick={() => setDetails(true)}
          >
            {t("settings")}
          </button>
        )}
      </div>
    </div>
  );
}

function Category({
  title,
  text,
  tools,
  none,
  checked,
  disabled,
  onChange,
}: {
  title: string;
  text: string;
  tools?: string[];
  none?: string;
  checked: boolean;
  disabled?: boolean;
  onChange?: (v: boolean) => void;
}) {
  return (
    <label className={`flex gap-3 rounded-xl border border-line p-3 ${disabled ? "opacity-80" : "cursor-pointer"}`}>
      <input
        type="checkbox"
        className="mt-1 h-5 w-5 shrink-0 accent-[var(--gold)]"
        checked={checked}
        disabled={disabled}
        onChange={(e) => onChange?.(e.target.checked)}
      />
      <span className="text-sm">
        <span className="font-semibold">{title}</span>
        <span className="mt-0.5 block text-ink/75">{text}</span>
        {tools && <span className="mt-1 block text-xs text-muted">{tools.length ? tools.join(", ") : none}</span>}
      </span>
    </label>
  );
}

/** Footer link "Cookie settings" (re-opens the banner). */
export function CookieSettingsButton({ label }: { label: string }) {
  return (
    <button
      type="button"
      className="hover:text-gold-text"
      onClick={openConsentSettings}
    >
      {label}
    </button>
  );
}
