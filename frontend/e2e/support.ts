import { execFileSync } from "node:child_process";
import { mkdirSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { expect, type Page, type TestInfo } from "@playwright/test";

/** Helpers for the e2e suite: backend API, Mailpit, SQL in the postgres container, screenshots. */

const here = resolve(process.cwd(), "e2e");
export const API = process.env.E2E_API_URL ?? "http://localhost:8080";
export const MAILPIT = process.env.E2E_MAILPIT_URL ?? "http://localhost:8025";
export const TENANT = "the-barber-news";
export const HAIRCUT = "7b3f1c2e-0000-4000-8000-000000000101";
export const BEARD = "7b3f1c2e-0000-4000-8000-000000000102";
export const LUCA = "7b3f1c2e-0000-4000-8000-000000000201";
export const MO = "7b3f1c2e-0000-4000-8000-000000000202";
export const STATE_FILE = resolve(here, ".state.json");
/** Browser storage (consent decision) every project starts with, written by global-setup. */
export const STORAGE_FILE = resolve(here, ".storage.json");

export type E2EState = { adminEmail: string; adminPassword: string; barberEmail: string; barberPassword: string };

export function state(): E2EState {
  return JSON.parse(readFileSync(STATE_FILE, "utf8"));
}

/** "de" / "en" and "dark" / "light" from the project name, e.g. "mobile-en-light". */
export function variant(info: TestInfo) {
  const [, lang, theme] = info.project.name.split("-");
  return { lang: lang as "de" | "en", theme: theme as "dark" | "light" };
}

export function uniqueEmail(prefix: string) {
  return `${prefix}-${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}@example.com`;
}

// ── backend ──────────────────────────────────────────────────────────────

export async function api<T = Record<string, unknown>>(
  method: string,
  path: string,
  body?: unknown,
  opts: { token?: string; lang?: string } = {},
): Promise<{ status: number; body: T }> {
  const res = await fetch(`${API}${path}`, {
    method,
    headers: {
      "Content-Type": "application/json",
      "Accept-Language": opts.lang ?? "de",
      ...(opts.token ? { Authorization: `Bearer ${opts.token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  return { status: res.status, body: (text ? JSON.parse(text) : {}) as T };
}

export async function login(email: string, password: string) {
  const r = await api<{ accessToken: string }>("POST", `/api/auth/${TENANT}/login`, { email, password });
  if (r.status !== 200) throw new Error(`login ${email} failed: ${r.status} ${JSON.stringify(r.body)}`);
  return r.body.accessToken;
}

/** SQL in the dev database (docker container "barbershop-postgres-1"); returns rows as text. */
export function sql(statement: string): string {
  return execFileSync(
    "docker",
    ["exec", "barbershop-postgres-1", "psql", "-U", "barbershop", "-d", "barbershop", "-tAc", statement],
    { encoding: "utf8" },
  ).trim();
}

/** A free start time for a barber: the first day ≥ minDays ahead that has `nth` free slots in their column. */
export async function freeSlot(staffId: string, services: string[], minDays = 2, nth = 0) {
  for (let d = minDays; d < 40; d++) {
    const day = new Date(Date.now() + d * 86_400_000).toLocaleDateString("en-CA", { timeZone: "Europe/Berlin" });
    const r = await api<{ barbers: { staffId: string; slots: { start: string; time: string; available: boolean }[] }[] }>(
      "GET",
      `/api/public/${TENANT}/availability?services=${services.join(",")}&date=${day}`,
    );
    const free = r.body.barbers?.find((b) => b.staffId === staffId)?.slots.filter((s) => s.available) ?? [];
    if (free.length > nth) return { day, ...free[nth] };
  }
  throw new Error(`no free slot for ${staffId}`);
}

/** Guest booking via the API (for tests that don't need the UI flow). */
export async function guestBooking(staffId: string, email: string, lang = "de", nth = 0) {
  const slot = await freeSlot(staffId, [HAIRCUT], 2, nth);
  const r = await api<{ id: string; statusToken: string }>("POST", `/api/public/${TENANT}/bookings`, {
    serviceIds: [HAIRCUT],
    staffId,
    start: slot.start,
    customerName: nameFor(email),
    customerEmail: email,
    customerPhone: "+49 151 2345678",
    lang,
    consent: true,
  });
  if (r.status !== 201) throw new Error(`booking failed: ${r.status} ${JSON.stringify(r.body)}`);
  return { ...r.body, slot };
}

export function nameFor(email: string) {
  return `E2E ${email.split("@")[0].split("-").slice(-1)[0]}`;
}

// ── Mailpit ──────────────────────────────────────────────────────────────

type MailSummary = { ID: string; Subject: string; To: { Address: string }[]; Created: string };

export async function waitForMail(to: string, subjectPart: string, timeoutMs = 20_000) {
  const until = Date.now() + timeoutMs;
  while (Date.now() < until) {
    const list = (await (await fetch(`${MAILPIT}/api/v1/messages?limit=200`)).json()) as { messages: MailSummary[] };
    const hit = list.messages.find(
      (m) => m.To.some((t) => t.Address.toLowerCase() === to.toLowerCase()) && m.Subject.includes(subjectPart),
    );
    if (hit) {
      return (await (await fetch(`${MAILPIT}/api/v1/message/${hit.ID}`)).json()) as {
        Subject: string;
        Text: string;
        HTML: string;
        Attachments: { FileName: string }[];
      };
    }
    await new Promise((r) => setTimeout(r, 400));
  }
  throw new Error(`no mail to ${to} with "${subjectPart}"`);
}

/** The frontend link (path + query) after the host, e.g. "/de/pay/<token>". */
export function linkIn(mail: { Text: string }, pathPart: string) {
  const m = mail.Text.match(new RegExp(`https?://[^/\\s]+(/[a-z]{2}${pathPart.replace(/\//g, "\\/")}[A-Za-z0-9_-]{43}(\\?[^\\s]*)?)`));
  if (!m) throw new Error(`no ${pathPart} link in mail`);
  return m[1];
}

// ── screenshots ──────────────────────────────────────────────────────────

const shotRoot = resolve(here, "../../screenshots/phase3");

/** Path for screenshots/phase3/<project>/<name>.png (directory created). */
export function shotFile(info: TestInfo, name: string) {
  const dir = resolve(shotRoot, info.project.name);
  mkdirSync(dir, { recursive: true });
  return resolve(dir, `${name}.png`);
}

/**
 * Screenshot to screenshots/phase3/<project>/<name>.png after fonts and skeletons settled:
 * the full page, or just the viewport while a modal dialog is open (the page behind it is the landing scene).
 */
export async function shot(page: Page, info: TestInfo, name: string) {
  await page.evaluate(() => document.fonts?.ready);
  await expect(page.locator(".skeleton")).toHaveCount(0, { timeout: 15_000 }).catch(() => undefined);
  const modal = await page.locator("dialog[open]").count();
  // fixed/sticky bars are captured where they sit, so take full pages from the top
  if (!modal) await page.evaluate(() => window.scrollTo(0, 0));
  await page.waitForTimeout(250);
  await page.screenshot({ path: shotFile(info, name), fullPage: modal === 0, animations: "disabled" });
}
