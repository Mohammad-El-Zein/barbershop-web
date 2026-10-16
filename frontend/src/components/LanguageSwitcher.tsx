"use client";

import { useLocale, useTranslations } from "next-intl";
import { routing } from "@/i18n/routing";

/**
 * DE | EN switcher. Full page navigation to /de or /en on purpose: the pinned
 * GSAP scene is rebuilt cleanly on a fresh load, and the proxy stores the choice
 * in the NEXT_LOCALE cookie (1 year) on that request.
 * `className` sets the display (default "flex"), e.g. "hidden sm:flex".
 */
export function LanguageSwitcher({ className = "flex" }: { className?: string }) {
  const t = useTranslations("language");
  const current = useLocale();

  return (
    <nav aria-label={t("label")} className={`items-center text-[0.7rem] font-semibold tracking-[0.2em] ${className}`}>
      {routing.locales.map((locale, i) => {
        const active = locale === current;
        return (
          <span key={locale} className="flex items-center">
            {i > 0 && (
              <span className="mx-1.5 h-3 w-px bg-ink/30" aria-hidden="true" />
            )}
            <a
              href={`/${locale}`}
              hrefLang={locale}
              lang={locale}
              aria-current={active ? "true" : undefined}
              className={`rounded px-1 py-1 uppercase transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-gold-text ${
                active ? "text-gold-text" : "text-ink/60 hover:text-ink"
              }`}
            >
              {locale}
              {/* accessible name "de (Deutsch)" contains the visible text (WCAG 2.5.3) */}
              <span className="sr-only"> ({t(locale)})</span>
            </a>
          </span>
        );
      })}
    </nav>
  );
}
