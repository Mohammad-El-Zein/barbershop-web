import AxeBuilder from "@axe-core/playwright";
import { expect, type Page, test } from "@playwright/test";
import { shot, state, variant } from "./support";
import { T } from "./texts";

/**
 * Accessibility: axe-core scans (WCAG 2.2 A/AA rules) of the main pages in both
 * themes, keyboard-only use of the booking flow and the admin area, and the
 * prefers-reduced-motion variant of the landing page.
 */

async function axe(page: Page, label: string) {
  const result = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"]).analyze();
  const serious = result.violations.filter((v) => v.impact === "serious" || v.impact === "critical");
  const summary = serious.map((v) => `${v.id} (${v.impact}): ${v.nodes.slice(0, 3).map((n) => n.target.join(" ")).join(" | ")}`);
  expect(summary, `${label}: serious/critical axe violations`).toEqual([]);
  return result.violations.length;
}

test("axe: public pages, booking flow, legal pages, landing page for ads, 404", async ({ page }, info) => {
  const { lang } = variant(info);
  const t = T[lang];

  await page.goto(`/${lang}`);
  await page.locator("[data-preloader]").waitFor({ state: "detached", timeout: 60_000 });
  await axe(page, "landing");

  await page.goto(`/${lang}/booking`);
  await expect(page.getByRole("main").getByRole("checkbox", { name: t.haircut })).toBeVisible();
  await axe(page, "booking: services");
  await page.getByRole("main").getByRole("checkbox", { name: t.haircut }).check();
  await page.getByRole("main").getByRole("button", { name: t.next }).click();
  await expect(page.getByRole("main").getByRole("radiogroup", { name: t.date })).toBeVisible();
  await expect(page.locator(".skeleton")).toHaveCount(0, { timeout: 15_000 });
  await axe(page, "booking: slots");

  for (const path of ["/account/login", "/account/register", "/impressum", "/datenschutz", "/agb", "/lp/herbst-fade", "/gibts-nicht"]) {
    await page.goto(`/${lang}${path}`);
    await page.waitForLoadState("networkidle");
    await axe(page, path);
  }
});

test("axe: admin area", async ({ page }, info) => {
  const { lang } = variant(info);
  const t = T[lang];
  await page.goto(`/${lang}/admin`);
  await page.getByLabel(t.email, { exact: true }).fill(state().adminEmail);
  await page.getByLabel(t.password, { exact: true }).fill(state().adminPassword);
  await page.getByRole("main").getByRole("button", { name: t.login, exact: true }).click();
  await expect(page.getByRole("navigation", { name: lang === "de" ? "Bereiche" : "Sections" })).toBeVisible();
  await expect(page.locator(".skeleton")).toHaveCount(0, { timeout: 15_000 });
  await axe(page, "admin calendar");
  for (const path of ["bookings", "services", "staff", "customers", "stats"]) {
    await page.goto(`/${lang}/admin/${path}`);
    await expect(page.locator(".skeleton")).toHaveCount(0, { timeout: 15_000 });
    await axe(page, `admin ${path}`);
  }
});

test("keyboard only: booking dialog (focus trap, step headings, Escape returns focus)", async ({ page }, info) => {
  test.skip(info.project.name.startsWith("mobile"), "keyboard flow on desktop layouts");
  const { lang } = variant(info);
  const t = T[lang];
  await page.goto(`/${lang}/booking`); // any page: the nav "Book" button opens the dialog
  const trigger = page.locator("header").getByRole("button", { name: t.book });
  await trigger.focus();
  await page.keyboard.press("Enter");
  const dialog = page.getByRole("dialog", { name: lang === "de" ? "Termin buchen" : "Book an appointment" });
  await expect(dialog).toBeVisible();

  // focus is inside and stays inside (trap) while tabbing around
  for (let i = 0; i < 25; i++) {
    await page.keyboard.press("Tab");
    expect(await dialog.evaluate((d) => d.contains(document.activeElement))).toBe(true);
  }

  // choose a service with the keyboard, go on with Enter: the step heading gets focus
  const haircut = dialog.getByRole("checkbox", { name: t.haircut });
  await haircut.focus();
  await page.keyboard.press("Space");
  await expect(haircut).toBeChecked();
  await dialog.getByRole("button", { name: t.next }).focus();
  await page.keyboard.press("Enter");
  await expect(dialog.getByRole("heading", { level: 3 })).toBeFocused();
  await shot(page, info, "120-keyboard-booking");

  // Escape closes and returns focus to the button that opened the dialog
  await page.keyboard.press("Escape");
  await expect(dialog).toBeHidden();
  await expect(trigger).toBeFocused();
});

test("keyboard only: staff login and tab navigation", async ({ page }, info) => {
  test.skip(info.project.name.startsWith("mobile"), "keyboard flow on desktop layouts");
  const { lang } = variant(info);
  const t = T[lang];
  await page.goto(`/${lang}/admin`);
  await page.getByLabel(t.email, { exact: true }).focus();
  await page.keyboard.type(state().adminEmail);
  await page.keyboard.press("Tab");
  await page.keyboard.type(state().adminPassword);
  await page.keyboard.press("Enter");
  const sections = page.getByRole("navigation", { name: lang === "de" ? "Bereiche" : "Sections" });
  await expect(sections).toBeVisible();
  await sections.getByRole("link", { name: lang === "de" ? "Buchungen" : "Bookings" }).focus();
  await page.keyboard.press("Enter");
  await page.waitForURL(/\/admin\/bookings/);
  await expect(sections.getByRole("link", { name: lang === "de" ? "Buchungen" : "Bookings" })).toHaveAttribute("aria-current", "page");
});

test("prefers-reduced-motion: static landing page, native scrolling, no pin", async ({ browser }, info) => {
  const ctx = await browser.newContext({ ...info.project.use, reducedMotion: "reduce" });
  const page = await ctx.newPage();
  const { lang } = variant(info);
  await page.goto(`/${lang}`);
  await page.locator("[data-preloader]").waitFor({ state: "detached", timeout: 60_000 });
  // StaticScene: chapters as normal sections, no ScrollTrigger pin, no Lenis smooth scrolling
  await expect(page.locator(".pin-spacer")).toHaveCount(0);
  await expect(page.locator("canvas[data-turntable]")).toHaveCount(0);
  expect(await page.evaluate(() => document.documentElement.classList.contains("lenis"))).toBe(false);
  await expect(page.locator("section#services")).toBeAttached();
  await page.mouse.wheel(0, 1200);
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(500);
  await expect(page.locator("section#services img").first()).toHaveAttribute("src", /service-/);
  await shot(page, info, "121-reduced-motion");
  await ctx.close();
});
