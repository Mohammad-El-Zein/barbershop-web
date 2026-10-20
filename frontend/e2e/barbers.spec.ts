import { expect, type Page, test } from "@playwright/test";
import { api, guestBooking, HAIRCUT, login, state, uniqueEmail, variant } from "./support";
import { T } from "./texts";

/** Admin → Barbers → Manage: login state, a start password shown once, reset, delete (only without bookings). */

const L = {
  de: {
    manage: "Verwalten",
    loginEmail: "E-Mail für den Barber-Login",
    generate: "Startpasswort erzeugen",
    setUp: "Login einrichten",
    noLogin: "Kein Login",
    active: "Login aktiv",
    shownOnce: "Wird nicht erneut angezeigt",
    copy: "Kopieren",
    hide: "Ausblenden",
    reset: "Passwort zurücksetzen",
    del: "Löschen",
    hasBookings: "Hat Buchungen – bitte stattdessen auf inaktiv setzen.",
    sections: "Bereiche",
  },
  en: {
    manage: "Manage",
    loginEmail: "Email for the barber login",
    generate: "Generate a start password",
    setUp: "Set up login",
    noLogin: "No login",
    active: "Login active",
    shownOnce: "It won’t be shown again",
    copy: "Copy",
    hide: "Hide",
    reset: "Reset password",
    del: "Delete",
    hasBookings: "Has bookings – please set to inactive instead.",
    sections: "Sections",
  },
};

/** A new barber offering the haircut, Monday–Saturday 09:00–19:00 (API). */
async function newBarber(token: string, name: string) {
  const s = await api<{ id: string }>("POST", "/api/admin/staff", { name }, { token });
  if (s.status !== 201) throw new Error(`staff create failed: ${s.status}`);
  await api("PUT", `/api/admin/staff/${s.body.id}/services`, { serviceIds: [HAIRCUT] }, { token });
  const entries = [1, 2, 3, 4, 5, 6].map((weekday) => ({ weekday, start: "09:00", end: "19:00" }));
  await api("PUT", `/api/admin/staff/${s.body.id}/working-hours`, { entries }, { token });
  return s.body.id;
}

async function adminStaffPage(page: Page, lang: "de" | "en") {
  await page.goto(`/${lang}/admin`);
  await page.getByLabel(T[lang].email, { exact: true }).fill(state().adminEmail);
  await page.getByLabel(T[lang].password, { exact: true }).fill(state().adminPassword);
  await page.keyboard.press("Enter");
  await expect(page.getByRole("navigation", { name: L[lang].sections })).toBeVisible();
  await page.goto(`/${lang}/admin/staff`);
}

function card(page: Page, name: string) {
  return page.getByRole("listitem").filter({ hasText: name });
}

test("barber login: generated start password shown once, reset ends the old one; delete only without bookings", async ({ page }, info) => {
  const { lang } = variant(info);
  const t = L[lang];
  const admin = await login(state().adminEmail, state().adminPassword);
  const suffix = `${info.project.name}-${Date.now().toString(36)}`;
  const free = `E2E Free ${suffix}`;
  const busy = `E2E Busy ${suffix}`;
  await newBarber(admin, free);
  const busyId = await newBarber(admin, busy);
  await guestBooking(busyId, uniqueEmail("busy-barber"), lang);

  await adminStaffPage(page, lang);

  // ── login with a generated start password ──
  const freeCard = card(page, free);
  await freeCard.getByRole("button", { name: t.manage }).click();
  await expect(freeCard.getByTestId("login-state")).toContainText(t.noLogin);
  const email = uniqueEmail("barber-start");
  await freeCard.getByLabel(t.loginEmail).fill(email);
  await freeCard.getByRole("radio", { name: t.generate }).check();
  await freeCard.getByRole("button", { name: t.setUp }).click();
  const shown = freeCard.getByTestId("start-password");
  await expect(shown).toBeVisible();
  const startPassword = (await shown.textContent())!.trim();
  expect(startPassword.length).toBeGreaterThanOrEqual(12);
  await expect(freeCard.getByText(t.shownOnce)).toBeVisible();
  await expect(freeCard.getByRole("button", { name: t.copy })).toBeVisible();
  await expect(freeCard.getByTestId("login-state")).toContainText(t.active);
  await expect(freeCard.getByTestId("login-state")).toContainText(email);
  // the barber can sign in and has to change it
  const session = await api<{ user: { mustChangePassword: boolean } }>("POST", "/api/auth/the-barber-news/login", { email, password: startPassword });
  expect(session.status).toBe(200);
  expect(session.body.user.mustChangePassword).toBe(true);

  // never shown again: hidden, and gone after a reload
  await freeCard.getByRole("button", { name: t.hide }).click();
  await expect(shown).toHaveCount(0);
  await page.reload();
  await card(page, free).getByRole("button", { name: t.manage }).click();
  await expect(card(page, free).getByTestId("login-state")).toContainText(email);
  await expect(page.getByTestId("start-password")).toHaveCount(0);
  await expect(page.getByText(startPassword)).toHaveCount(0);

  // ── reset: a new start password, the old one stops working ──
  await card(page, free).getByRole("button", { name: t.reset }).click();
  const resetDialog = page.getByRole("dialog");
  await resetDialog.getByRole("button", { name: t.reset }).click();
  const newPassword = (await card(page, free).getByTestId("start-password").textContent())!.trim();
  expect(newPassword).not.toBe(startPassword);
  expect((await api("POST", "/api/auth/the-barber-news/login", { email, password: startPassword })).status).toBe(401);
  expect((await api("POST", "/api/auth/the-barber-news/login", { email, password: newPassword })).status).toBe(200);

  // ── delete: refused with bookings, done without ──
  await card(page, busy).getByRole("button", { name: t.manage }).click();
  await card(page, busy).getByRole("button", { name: t.del, exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: t.del, exact: true }).click();
  await expect(card(page, busy).getByText(t.hasBookings)).toBeVisible();

  // one barber is open at a time
  await card(page, free).getByRole("button", { name: t.manage }).click();
  await card(page, free).getByRole("button", { name: t.del, exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: t.del, exact: true }).click();
  await expect(card(page, free)).toHaveCount(0);
  expect((await api("POST", "/api/auth/the-barber-news/login", { email, password: newPassword })).status).toBe(401);
});
