"use client";

import { type Consent, readConsent } from "./consent";

/**
 * Tracking layer: one `track(event, params)` API, provider adapters behind it.
 *
 * - Default provider: none (no-op). In development events are logged to the console.
 * - Optional adapters, enabled by env vars (inlined at build time, see .env.example):
 *     GA4            NEXT_PUBLIC_GA4_ID                         → category "statistics"
 *     Google Ads     NEXT_PUBLIC_GOOGLE_ADS_ID (+ _CONVERSION_LABEL) → category "marketing"
 *     Meta Pixel     NEXT_PUBLIC_META_PIXEL_ID                  → category "marketing"
 * - An adapter's script is injected only after consent for its category (never on
 *   page load before a decision). Withdrawing consent stops it, deletes its cookies
 *   and reloads the page so already loaded scripts are gone too.
 * - Events carry no personal data (no names, emails, phones) — only the funnel step,
 *   the source button, counts, the booking id (as transaction id) and amounts.
 */
export type TrackEvent = "book_click" | "booking_started" | "booking_step_completed" | "booking_submitted" | "booking_confirmed";
export type TrackParams = Record<string, string | number | boolean | undefined>;

const GA4_ID = process.env.NEXT_PUBLIC_GA4_ID ?? "";
const ADS_ID = process.env.NEXT_PUBLIC_GOOGLE_ADS_ID ?? "";
const ADS_LABEL = process.env.NEXT_PUBLIC_GOOGLE_ADS_CONVERSION_LABEL ?? "";
const META_ID = process.env.NEXT_PUBLIC_META_PIXEL_ID ?? "";

type Category = "statistics" | "marketing";

type Adapter = {
  name: string;
  category: Category;
  enabled: boolean;
  load(): void;
  track(event: TrackEvent, params: TrackParams): void;
  /** Cookie name prefixes the tool sets (deleted on withdrawal). */
  cookies: string[];
  /** Stop sending (before the page reloads after a withdrawal). */
  disable(): void;
};

type Gtag = (...args: unknown[]) => void;
declare global {
  interface Window {
    dataLayer?: unknown[];
    gtag?: Gtag;
    fbq?: ((...args: unknown[]) => void) & { queue?: unknown[]; loaded?: boolean; version?: string; callMethod?: unknown; push?: unknown };
    _fbq?: unknown;
  }
}

function injectScript(src: string, id: string) {
  if (document.getElementById(id)) return;
  const s = document.createElement("script");
  s.id = id;
  s.async = true;
  s.src = src;
  document.head.appendChild(s);
}

/** gtag.js is shared by GA4 and Google Ads; loaded once, with Consent Mode v2 set from our banner. */
function ensureGtag(c: Consent) {
  if (!window.gtag) {
    window.dataLayer = window.dataLayer ?? [];
    // gtag must push the `arguments` object itself (that is what gtag.js expects)
    window.gtag = function gtag() {
      // eslint-disable-next-line prefer-rest-params
      window.dataLayer!.push(arguments);
    };
    window.gtag("consent", "default", {
      analytics_storage: c.statistics ? "granted" : "denied",
      ad_storage: c.marketing ? "granted" : "denied",
      ad_user_data: c.marketing ? "granted" : "denied",
      ad_personalization: c.marketing ? "granted" : "denied",
    });
    window.gtag("js", new Date());
    injectScript(`https://www.googletagmanager.com/gtag/js?id=${encodeURIComponent(GA4_ID || ADS_ID)}`, "gtag-js");
  }
}

const ga4: Adapter = {
  name: "ga4",
  category: "statistics",
  enabled: !!GA4_ID,
  cookies: ["_ga", "_gid", "_gat"],
  load() {
    ensureGtag(readConsent()!);
    window.gtag!("config", GA4_ID, { anonymize_ip: true, send_page_view: true });
  },
  track(event, params) {
    window.gtag?.("event", event, params);
  },
  disable() {
    (window as unknown as Record<string, boolean>)[`ga-disable-${GA4_ID}`] = true;
  },
};

const googleAds: Adapter = {
  name: "google-ads",
  category: "marketing",
  enabled: !!ADS_ID,
  cookies: ["_gcl_"],
  load() {
    ensureGtag(readConsent()!);
    window.gtag!("config", ADS_ID);
  },
  track(event, params) {
    // only the confirmed booking is a conversion (deduplicated by the booking id)
    if (event === "booking_confirmed" && ADS_LABEL) {
      window.gtag?.("event", "conversion", {
        send_to: `${ADS_ID}/${ADS_LABEL}`,
        value: params.value,
        currency: params.currency,
        transaction_id: params.booking_id,
      });
    }
  },
  disable() {
    window.gtag?.("consent", "update", { ad_storage: "denied", ad_user_data: "denied", ad_personalization: "denied" });
  },
};

const META_STANDARD: Partial<Record<TrackEvent, string>> = { booking_submitted: "Lead", booking_confirmed: "Schedule" };

const metaPixel: Adapter = {
  name: "meta-pixel",
  category: "marketing",
  enabled: !!META_ID,
  cookies: ["_fbp", "_fbc"],
  load() {
    if (!window.fbq) {
      // the standard Meta pixel bootstrap (queue until fbevents.js has loaded)
      const fbq = function (...args: unknown[]) {
        if (fbq.callMethod) (fbq.callMethod as (...a: unknown[]) => void)(...args);
        else fbq.queue!.push(args);
      } as NonNullable<Window["fbq"]>;
      fbq.push = fbq;
      fbq.loaded = true;
      fbq.version = "2.0";
      fbq.queue = [];
      window.fbq = fbq;
      window._fbq = fbq;
      injectScript("https://connect.facebook.net/en_US/fbevents.js", "meta-pixel-js");
      fbq("init", META_ID);
      fbq("track", "PageView");
    }
  },
  track(event, params) {
    const standard = META_STANDARD[event];
    if (standard) window.fbq?.("track", standard, { value: params.value, currency: params.currency }, { eventID: params.booking_id });
    else window.fbq?.("trackCustom", event, params);
  },
  disable() {
    window.fbq?.("consent", "revoke");
  },
};

const ADAPTERS = [ga4, googleAds, metaPixel].filter((a) => a.enabled);
const active = new Set<Adapter>();

/** Activates the adapters the visitor consented to; called on load and on every consent change. */
export function applyConsent(c: Consent | null) {
  let revoked = false;
  for (const a of ADAPTERS) {
    const allowed = !!c?.[a.category];
    if (allowed && !active.has(a)) {
      a.load();
      active.add(a);
    } else if (!allowed && active.has(a)) {
      a.disable();
      active.delete(a);
      deleteCookies(a.cookies);
      revoked = true;
    } else if (!allowed) {
      deleteCookies(a.cookies); // leftovers from an earlier consent
    }
  }
  // scripts can't be unloaded: start fresh without them
  if (revoked) window.location.reload();
}

function deleteCookies(prefixes: string[]) {
  const host = window.location.hostname;
  const domains = ["", host, `.${host}`, `.${host.split(".").slice(-2).join(".")}`];
  for (const cookie of document.cookie.split(";")) {
    const name = cookie.split("=")[0].trim();
    if (!prefixes.some((p) => name.startsWith(p))) continue;
    for (const d of domains) {
      document.cookie = `${name}=; Max-Age=0; path=/${d ? `; domain=${d}` : ""}`;
    }
  }
}

/** Sends an event to every active adapter (and a "track" window event for tests / debugging). */
export function track(event: TrackEvent, params: TrackParams = {}) {
  if (typeof window === "undefined") return;
  const clean = Object.fromEntries(Object.entries(params).filter(([, v]) => v !== undefined));
  for (const a of active) a.track(event, clean);
  window.dispatchEvent(new CustomEvent("track", { detail: { event, params: clean } }));
  if (process.env.NODE_ENV !== "production") console.debug("[track]", event, clean);
}

/** Names of the configured adapters (for the consent banner's details). */
export const configuredTools = ADAPTERS.map((a) => ({ name: a.name, category: a.category }));

/** Like track(), but at most once per key and browser session (e.g. a confirmation seen on two pages). */
export function trackOnce(key: string, event: TrackEvent, params: TrackParams = {}) {
  try {
    if (window.sessionStorage.getItem(`tracked:${key}`)) return;
    window.sessionStorage.setItem(`tracked:${key}`, "1");
  } catch {
    // storage blocked: may track twice, the booking id deduplicates in the ad tools
  }
  track(event, params);
}

/** Conversion value of a booking: the booked price (EUR), not personal data. */
export function bookingParams(b: { id?: string; price?: { currency?: string; total?: { cents?: number } }; services?: unknown[] }) {
  return {
    booking_id: b.id,
    value: (b.price?.total?.cents ?? 0) / 100,
    currency: b.price?.currency ?? "EUR",
    service_count: b.services?.length,
  };
}
