"use client";

import { hasConsent } from "./consent";

/**
 * Marketing attribution: which ad / campaign brought the visitor (first touch).
 *
 * On a page load with campaign data in the URL (utm_*, gclid, fbclid) or on an
 * ad landing page (/xx/lp/...), the values are kept IN MEMORY for this page view.
 * They are only written to localStorage (30 days, first touch wins) once the
 * visitor has given MARKETING consent — immediately if consent exists already, or
 * when it is granted later on the same page. Without marketing consent nothing is
 * stored and nothing is sent with a booking. Withdrawing consent deletes it.
 */
const KEY = "attribution";
const TTL_MS = 30 * 24 * 60 * 60 * 1000;
const UTM = ["utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content"] as const;

export type Attribution = {
  source?: string;
  medium?: string;
  campaign?: string;
  term?: string;
  content?: string;
  gclid?: string;
  fbclid?: string;
  landingPath: string;
  capturedAt: string;
};

let pending: Attribution | null = null;

const cut = (v: string | null, max: number) => (v ? v.trim().slice(0, max) || undefined : undefined);

/** Reads campaign data from the current URL into memory (call once per full page load). */
export function captureAttribution(url: URL = new URL(window.location.href)) {
  const q = url.searchParams;
  const a: Attribution = {
    source: cut(q.get("utm_source"), 100),
    medium: cut(q.get("utm_medium"), 100),
    campaign: cut(q.get("utm_campaign"), 150),
    term: cut(q.get("utm_term"), 150),
    content: cut(q.get("utm_content"), 150),
    gclid: cut(q.get("gclid"), 255),
    fbclid: cut(q.get("fbclid"), 255),
    // path + the campaign parameters only (no other query data)
    landingPath: (url.pathname + campaignQuery(q)).slice(0, 300),
    capturedAt: new Date().toISOString(),
  };
  const hasCampaign = UTM.some((k) => q.has(k)) || !!a.gclid || !!a.fbclid || /^\/[a-z]{2}\/lp\//.test(url.pathname);
  if (!hasCampaign) return;
  pending = a;
  persistAttribution();
}

function campaignQuery(q: URLSearchParams) {
  const keep = new URLSearchParams();
  for (const k of [...UTM, "gclid", "fbclid"]) {
    const v = q.get(k);
    if (v) keep.set(k, k === "gclid" || k === "fbclid" ? "1" : v); // click ids are stored separately
  }
  return keep.size ? `?${keep}` : "";
}

function readStored(): Attribution | null {
  try {
    const raw = window.localStorage.getItem(KEY);
    if (!raw) return null;
    const a = JSON.parse(raw) as Attribution;
    if (Date.now() - new Date(a.capturedAt).getTime() > TTL_MS) {
      window.localStorage.removeItem(KEY);
      return null;
    }
    return a;
  } catch {
    return null;
  }
}

/** Stores the pending first touch if marketing consent is given and none is stored yet. */
export function persistAttribution() {
  if (!pending || !hasConsent("marketing")) return;
  if (!readStored()) {
    try {
      window.localStorage.setItem(KEY, JSON.stringify(pending));
    } catch {
      // storage blocked: still sent from memory with a booking in this page view
      return;
    }
  }
  pending = null;
}

/** What goes with a booking request: only with marketing consent, else nothing. */
export function currentAttribution(): Attribution | undefined {
  if (!hasConsent("marketing")) return undefined;
  return readStored() ?? pending ?? undefined;
}

/** Marketing consent withdrawn: forget everything. */
export function clearAttribution() {
  pending = null;
  try {
    window.localStorage.removeItem(KEY);
  } catch {
    // nothing stored
  }
}
