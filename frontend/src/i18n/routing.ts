import { defineRouting } from "next-intl/routing";

/**
 * Locale routing: every page lives under /de or /en.
 * - First visit to "/": the proxy picks the best match from the browser's
 *   Accept-Language header (fallback: de).
 * - Switching language stores the choice in the NEXT_LOCALE cookie for a year,
 *   so it wins over the browser language on later visits. This is a functional
 *   cookie for a choice the user made (no tracking), see the Phase 4 consent notes.
 */
export const routing = defineRouting({
  locales: ["de", "en"],
  defaultLocale: "de",
  localePrefix: "always",
  localeCookie: {
    maxAge: 60 * 60 * 24 * 365,
  },
});

export type Locale = (typeof routing.locales)[number];
