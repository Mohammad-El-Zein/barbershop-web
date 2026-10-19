import { expect, test } from "@playwright/test";
import { PASSWORD, seededCustomer, uiLogin } from "./accounts";
import { guestBooking, LUCA, linkIn, shot, shotFile, state, uniqueEmail, variant, waitForMail } from "./support";
import { T } from "./texts";

/** Screenshots of every remaining page (the flow and special specs cover the booking steps). */

const P = {
  de: {
    requestedMail: "Deine Terminanfrage bei",
    resetMail: "Passwort zurücksetzen",
    forgotButton: "Link senden",
    manageCancel: "Termin stornieren",
    noPayment: "Du hast noch keine Anzahlung bezahlt",
    newPassword: "Neues Passwort",
    repeat: "Passwort wiederholen",
    save: "Passwort speichern",
    invalid: "Link ungültig",
  },
  en: {
    requestedMail: "Your appointment request at",
    resetMail: "Reset your password",
    forgotButton: "Send link",
    manageCancel: "Cancel appointment",
    noPayment: "You haven’t paid a deposit yet",
    newPassword: "New password",
    repeat: "Repeat password",
    save: "Save password",
    invalid: "Invalid link",
  },
};

test("public pages: landing, booking, login, register, forgot password, privacy", async ({ page }, info) => {
  const { lang } = variant(info);
  const t = T[lang];

  await page.goto(`/${lang}`);
  await page.locator("[data-preloader]").waitFor({ state: "detached", timeout: 60_000 });
  await page.screenshot({ path: shotFile(info, "60-landing-top") }); // the scroll scene: first screen only

  await page.goto(`/${lang}/booking`);
  await expect(page.getByRole("main").getByRole("checkbox", { name: t.haircut })).toBeVisible();
  await shot(page, info, "61-booking-page");

  await page.goto(`/${lang}/account/login`);
  // "Customer": login + register link; "Barber": login only (accounts come from the admin)
  const main = page.getByRole("main");
  await expect(main.getByRole("tab", { name: t.asCustomer, exact: true })).toHaveAttribute("aria-selected", "true");
  await expect(main.getByRole("link", { name: t.registerLink })).toBeVisible();
  await main.getByRole("tab", { name: t.asBarber, exact: true }).click();
  await expect(main.getByRole("link", { name: t.registerLink })).toHaveCount(0);
  await expect(main.getByRole("button", { name: t.login, exact: true })).toBeVisible();
  await main.getByRole("tab", { name: t.asCustomer, exact: true }).click();
  await shot(page, info, "62-login");
  await page.goto(`/${lang}/account/register`);
  await shot(page, info, "63-register");
  await page.goto(`/${lang}/account/forgot-password`);
  await shot(page, info, "64-forgot-password");
  await page.goto(`/${lang}/datenschutz`);
  await shot(page, info, "65-privacy");

  // an unknown token: clear message, no actions
  await page.goto(`/${lang}/pay/${"x".repeat(43)}`);
  await expect(page.getByRole("heading", { name: P[lang].invalid })).toBeVisible();
  await shot(page, info, "66-link-invalid");
});

test("guest manage link: details, refund info before confirming, cancelled", async ({ page }, info) => {
  const { lang } = variant(info);
  const t = T[lang];
  const email = uniqueEmail(`manage-${lang}`);
  await guestBooking(LUCA, email, lang, 2);
  const mail = await waitForMail(email, P[lang].requestedMail);

  await page.goto(linkIn(mail, "/booking/manage/"));
  await expect(page.getByRole("button", { name: P[lang].manageCancel })).toBeVisible();
  await shot(page, info, "70-manage");
  await page.getByRole("button", { name: P[lang].manageCancel }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog.getByText(P[lang].noPayment)).toBeVisible();
  await shot(page, info, "71-manage-confirm");
  await dialog.getByRole("button", { name: t.yesCancel }).click();
  await expect(page.getByText(t.cancelledBadge, { exact: true }).first()).toBeVisible();
  await shot(page, info, "72-manage-cancelled");
});

test("text typed before the page hydrated is not lost (forgot password)", async ({ page }, info) => {
  const { lang } = variant(info);
  // hold back the JavaScript: the server HTML is usable, React is not running yet
  let release!: () => void;
  const gate = new Promise<void>((r) => (release = r));
  await page.route("**/_next/static/chunks/*.js", async (route) => {
    await gate;
    await route.continue();
  });
  await page.goto(`/${lang}/account/forgot-password`, { waitUntil: "domcontentloaded" });
  const email = uniqueEmail("prehydration");
  await page.getByLabel(T[lang].email, { exact: true }).fill(email);

  release();
  await page.waitForLoadState("load");
  await expect
    .poll(() => page.evaluate(() => Object.keys(document.querySelector("form")!).some((k) => k.startsWith("__react"))))
    .toBe(true);
  const sent = page.waitForRequest((r) => r.url().includes("/auth/forgot-password") && r.method() === "POST");
  await page.getByRole("button", { name: P[lang].forgotButton }).click();
  expect((await sent).postDataJSON()).toMatchObject({ email });
});

test("account pages: reset password, profile, my bookings", async ({ page }, info) => {
  const { lang } = variant(info);
  const t = T[lang];
  const email = await seededCustomer(lang, 7, "profile");

  // forgot → email → reset page → new password
  await page.goto(`/${lang}/account/forgot-password`);
  await page.getByLabel(t.email, { exact: true }).fill(email);
  await page.getByRole("button", { name: P[lang].forgotButton }).click();
  await expect(page.getByRole("status").or(page.getByRole("alert")).first()).toBeVisible();
  await shot(page, info, "80-forgot-sent");
  const reset = await waitForMail(email, P[lang].resetMail);
  await page.goto(linkIn(reset, "/account/reset-password/"));
  await shot(page, info, "81-reset-password");
  await page.getByLabel(P[lang].newPassword, { exact: true }).fill(`${PASSWORD}-2`);
  await page.getByLabel(P[lang].repeat, { exact: true }).fill(`${PASSWORD}-2`);
  await page.getByRole("button", { name: P[lang].save }).click();
  await expect(page.getByRole("heading", { name: lang === "de" ? "Passwort gespeichert" : "Password saved" })).toBeVisible();
  await shot(page, info, "82-reset-done");

  await uiLogin(page, t, lang, email, `${PASSWORD}-2`);
  await expect(page.getByText(lang === "de" ? /7 von 10 Besuchen/ : /7 of 10 visits/)).toBeVisible();
  await shot(page, info, "83-my-bookings-loyalty");
  await page.goto(`/${lang}/account/profile`);
  await expect(page.getByLabel(t.name, { exact: true })).toHaveValue(/E2E/);
  await shot(page, info, "84-profile");
});

test("staff area: admin calendar, bookings, services, staff, time off, customers, stats", async ({ page }, info) => {
  const { lang } = variant(info);
  const t = T[lang];
  const { adminEmail, adminPassword } = state();

  await page.goto(`/${lang}/admin`);
  await shot(page, info, "90-staff-login");
  await page.getByLabel(t.email, { exact: true }).fill(adminEmail);
  await page.getByLabel(t.password, { exact: true }).fill(adminPassword);
  await page.getByRole("main").getByRole("button", { name: t.login, exact: true }).click();
  await expect(page.getByRole("navigation", { name: lang === "de" ? "Bereiche" : "Sections" })).toBeVisible();
  await shot(page, info, "91-admin-calendar");

  for (const [i, path] of ["bookings", "services", "staff", "time-off", "customers", "stats"].entries()) {
    await page.goto(`/${lang}/admin/${path}`);
    await expect(page.locator(`a[href$='/admin/${path}']`).first()).toBeVisible();
    await shot(page, info, `9${i + 2}-admin-${path}`);
  }
});


test("404: real status and complete server HTML (works without JavaScript)", async ({ browser, request }, info) => {
  test.skip(info.project.name !== "desktop-de-dark", "routing check, one project is enough");
  for (const path of ["/de/gibts-nicht", "/en/a/b/c", "/de/lp/unbekannte-kampagne", "/fr/irgendwas"]) {
    const res = await request.get(path);
    expect(res.status(), path).toBe(404);
    expect(await res.text(), path).toContain("Seite nicht gefunden");
  }
  const ctx = await browser.newContext({ javaScriptEnabled: false });
  const page = await ctx.newPage();
  const res = await page.goto("/de/lp/unbekannte-kampagne");
  expect(res?.status()).toBe(404);
  await expect(page.getByRole("heading", { level: 1, name: "Seite nicht gefunden" })).toBeVisible();
  await expect(page.getByRole("heading", { level: 2, name: "Page not found" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Zur Startseite" })).toHaveAttribute("href", "/de");
  await expect(page).toHaveTitle(/Seite nicht gefunden · Page not found/);
  await ctx.close();
});
