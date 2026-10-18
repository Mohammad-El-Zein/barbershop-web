"use client";

/**
 * Cookie / tracking consent (GDPR + § 25 TDDDG).
 *
 * - Default: only technically necessary storage. "statistics" and "marketing" are
 *   off until the visitor opts in; nothing optional loads or stores before that.
 * - The decision is stored in localStorage with version and timestamp. It is asked
 *   again after 12 months, or when CONSENT_VERSION changes (bump it whenever the
 *   categories or the tools behind them change, e.g. a new ad pixel).
 * - Changes are broadcast as a "consent:change" window event so the tracking layer
 *   and the attribution capture react immediately.
 */
export const CONSENT_VERSION = 1;
const KEY = "consent";
const MAX_AGE_MS = 365 * 24 * 60 * 60 * 1000;
export const CONSENT_EVENT = "consent:change";
export const CONSENT_OPEN_EVENT = "consent:open";

export type Consent = {
  /** Version of the consent texts / categories this decision applies to. */
  v: number;
  /** ISO timestamp of the decision. */
  at: string;
  statistics: boolean;
  marketing: boolean;
};

export type ConsentChoice = Pick<Consent, "statistics" | "marketing">;

let current: Consent | null | undefined; // undefined = not read yet

/** The stored decision, or null when there is none (or it is outdated / expired). */
export function readConsent(): Consent | null {
  if (current !== undefined) return current;
  current = null;
  try {
    const raw = window.localStorage.getItem(KEY);
    if (raw) {
      const c = JSON.parse(raw) as Consent;
      const fresh = Date.now() - new Date(c.at).getTime() < MAX_AGE_MS;
      if (c.v === CONSENT_VERSION && fresh) current = { v: c.v, at: c.at, statistics: !!c.statistics, marketing: !!c.marketing };
    }
  } catch {
    // storage blocked: treat as "no decision" (banner shows, nothing optional loads)
  }
  return current;
}

export function saveConsent(choice: ConsentChoice): Consent {
  const c: Consent = { v: CONSENT_VERSION, at: new Date().toISOString(), ...choice };
  current = c;
  try {
    window.localStorage.setItem(KEY, JSON.stringify(c));
  } catch {
    // keep the decision for this page view at least
  }
  window.dispatchEvent(new CustomEvent<Consent>(CONSENT_EVENT, { detail: c }));
  return c;
}

/** True only after an explicit opt-in for that category. */
export function hasConsent(category: "statistics" | "marketing"): boolean {
  return !!readConsent()?.[category];
}

/** Re-opens the banner (footer link "Cookie settings"). */
export function openConsentSettings() {
  window.dispatchEvent(new Event(CONSENT_OPEN_EVENT));
}

export function onConsentChange(fn: (c: Consent) => void) {
  const handler = (e: Event) => fn((e as CustomEvent<Consent>).detail);
  window.addEventListener(CONSENT_EVENT, handler);
  return () => window.removeEventListener(CONSENT_EVENT, handler);
}
