"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";

/**
 * Newsletter signup — UI only. NOT connected to any provider yet
 * (hook up Brevo/Mailchimp/backend in a later phase; keep GDPR double opt-in).
 */
export function NewsletterForm() {
  const t = useTranslations("footer.newsletter");
  const [done, setDone] = useState(false);

  return (
    <div>
      <form
        className="mt-4 flex flex-col gap-2 sm:flex-row"
        onSubmit={(e) => {
          e.preventDefault();
          setDone(true);
        }}
      >
        <label htmlFor="newsletter-email" className="sr-only">
          {t("emailLabel")}
        </label>
        <input
          id="newsletter-email"
          type="email"
          required
          placeholder={t("placeholder")}
          autoComplete="email"
          className="h-11 w-full min-w-0 rounded-full border border-ink/20 bg-ink/[0.03] px-5 text-sm text-ink placeholder:text-ink/45 focus:border-gold-text focus:outline-none sm:flex-1"
        />
        <button type="submit" className="btn btn-outline-gold h-11">
          {t("subscribe")}
        </button>
      </form>
      <p className="mt-2 min-h-4 text-xs text-gold-text" aria-live="polite">
        {done ? t("thanks") : ""}
      </p>
    </div>
  );
}
