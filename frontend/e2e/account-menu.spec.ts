import AxeBuilder from "@axe-core/playwright";
import { expect, type Page, test } from "@playwright/test";
import { PASSWORD, seededCustomer } from "./accounts";
import { shot, state, variant } from "./support";
import { T } from "./texts";

/**
 * The account menu in the header (initials → name, email, role, "My account", "Log out"; no
 * link into the staff area), logout from the header and from the staff area, switching
 * between admin, customer and barber in one browser, two tabs, and the "already logged in"
 * state of the login page. Keyboard use and axe on the open menu.
 */

const L = {
  de: {
    menu: /^Konto-Menü von /,
    myAccount: "Mein Konto",
    logout: "Abmelden",
    staffLogin: "Team-Anmeldung",
    noAccess: "Kein Zugriff",
    sections: "Bereiche",
    adminArea: "Admin-Bereich",
    barberArea: "Barber-Bereich",
    admin: "Admin",
    customer: "Kundenkonto",
    signedIn: "Du bist bereits angemeldet",
    switchAccount: "Abmelden und anderes Konto nutzen",
    wrongRole: "Dieser Bereich ist für Kundenkonten",
  },
  en: {
    menu: /^Account menu of /,
    myAccount: "My account",
    logout: "Log out",
    staffLogin: "Staff login",
    noAccess: "No access",
    sections: "Sections",
    adminArea: "Admin area",
    barberArea: "Barber area",
    admin: "Admin",
    customer: "Customer account",
    signedIn: "You are already logged in",
    switchAccount: "Log out and use another account",
    wrongRole: "This area is for customer accounts",
  },
};

/** Next keeps the previous page hidden in the DOM (React Activity): the visible form field only. */
const visible = (page: Page, label: string) => page.getByLabel(label, { exact: true }).locator("visible=true");

async function loginOnLoginPage(page: Page, lang: "de" | "en", email: string, password: string, barber = false) {
  const t = T[lang];
  await page.goto(`/${lang}/account/login${barber ? "?as=barber" : ""}`);
  await visible(page, t.email).fill(email);
  await visible(page, t.password).fill(password);
  await page.getByRole("main").getByRole("button", { name: t.login, exact: true }).click();
}

const menuButton = (page: Page, lang: "de" | "en") => page.getByRole("banner").getByRole("button", { name: L[lang].menu });

test("account menu: keyboard and mouse, no staff-area link, logout; switching admin → customer → barber → admin", async ({ page }, info) => {
  const { lang } = variant(info);
  const t = T[lang];
  const l = L[lang];
  const { adminEmail, adminPassword, barberEmail, barberPassword } = state();

  // ── admin ──
  await loginOnLoginPage(page, lang, adminEmail, adminPassword);
  await page.waitForURL(/\/admin$/);
  await expect(page.getByRole("navigation", { name: l.sections })).toBeVisible();

  // keyboard: Enter opens on the first item, ↓ ↑ Home End move (wrapping), Escape closes and returns focus
  await page.goto(`/${lang}/booking`);
  const button = menuButton(page, lang);
  await expect(button).toHaveAttribute("aria-haspopup", "menu");
  await expect(button).toHaveAttribute("aria-expanded", "false");
  await button.focus();
  await page.keyboard.press("Enter");
  const menu = page.getByRole("menu");
  await expect(menu).toBeVisible();
  await expect(button).toHaveAttribute("aria-expanded", "true");
  const myAccount = menu.getByRole("menuitem", { name: l.myAccount });
  const logout = menu.getByRole("menuitem", { name: l.logout });
  await expect(myAccount).toBeFocused();
  await page.keyboard.press("ArrowDown");
  await expect(logout).toBeFocused();
  await page.keyboard.press("ArrowDown");
  await expect(myAccount).toBeFocused();
  await page.keyboard.press("End");
  await expect(logout).toBeFocused();
  await page.keyboard.press("Home");
  await expect(myAccount).toBeFocused();
  await page.keyboard.press("Escape");
  await expect(menu).toBeHidden();
  await expect(button).toBeFocused();
  await page.keyboard.press("ArrowUp"); // ↑ opens on the last item
  await expect(logout).toBeFocused();
  await page.keyboard.press("Tab"); // Tab closes
  await expect(menu).toBeHidden();

  // mouse: name, email and role; exactly two items; nothing leads into the staff area
  await button.click();
  const popup = page.locator("[data-account-menu]:visible");
  await expect(popup).toContainText(adminEmail);
  await expect(popup).toContainText(l.admin);
  await expect(menu.getByRole("menuitem")).toHaveCount(2);
  await expect(popup.locator('a[href*="/admin"]')).toHaveCount(0);
  const axe = await new AxeBuilder({ page }).include("[data-account-popup]").withTags(["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"]).analyze();
  expect(axe.violations.map((v) => `${v.id}: ${v.nodes[0]?.target.join(" ")}`)).toEqual([]);
  await shot(page, info, "130-account-menu");
  // a click outside closes it
  const size = page.viewportSize()!;
  await page.mouse.click(8, size.height - 8);
  await expect(menu).toBeHidden();

  // "My account" for an admin: the profile (the customer page is for customer accounts)
  await button.click();
  await menu.getByRole("menuitem", { name: l.myAccount }).click();
  await page.waitForURL(/\/account\/profile$/);
  // the customer page itself says why it isn't for this account
  await page.goto(`/${lang}/account`);
  await expect(page.getByText(l.wrongRole)).toBeVisible();

  // log out from the header: home page, the person icon is a login link again, /admin asks to log in
  await menuButton(page, lang).click();
  await page.getByRole("menuitem", { name: l.logout }).click();
  await page.waitForURL(new RegExp(`/${lang}$`));
  await expect(page.getByRole("banner").getByRole("link", { name: t.login, exact: true })).toBeVisible();
  await page.goto(`/${lang}/admin`);
  await expect(page.getByRole("heading", { name: l.staffLogin })).toBeVisible();
  await expect(page.getByRole("navigation", { name: l.sections })).toHaveCount(0);
  // the session is gone on the server too: a reload does not bring it back
  await page.reload();
  await expect(page.getByRole("heading", { name: l.staffLogin })).toBeVisible();

  // ── customer, right after the admin ──
  const customerEmail = await seededCustomer(lang, 0, "switch");
  await loginOnLoginPage(page, lang, customerEmail, PASSWORD);
  await expect(page.getByRole("heading", { name: t.hello })).toBeVisible();
  await menuButton(page, lang).click();
  await expect(page.locator("[data-account-menu]:visible")).toContainText(customerEmail);
  await expect(page.locator("[data-account-menu]:visible")).toContainText(l.customer);
  await page.keyboard.press("Escape");
  // /admin turns a customer away (and offers to log out)
  await page.goto(`/${lang}/admin`);
  await expect(page.getByText(l.noAccess)).toBeVisible();
  await expect(page.getByRole("navigation", { name: l.sections })).toHaveCount(0);
  await page.getByRole("main").getByRole("button", { name: l.logout }).click();
  await page.waitForURL(/\/account\/login\?as=barber$/);

  // ── barber, right after the customer ──
  await expect(page.getByRole("tab", { name: t.asBarber })).toHaveAttribute("aria-selected", "true");
  await visible(page, t.email).fill(barberEmail);
  await visible(page, t.password).fill(barberPassword);
  await page.getByRole("main").getByRole("button", { name: t.login, exact: true }).click();
  await page.waitForURL(/\/admin$/);
  await expect(page.getByText(l.barberArea)).toBeVisible();
  await expect(page.getByText(customerEmail)).toHaveCount(0);
  // the visible logout in the staff area's header
  await page.getByRole("main").getByRole("button", { name: l.logout }).click();
  await page.waitForURL(/\/account\/login\?as=barber$/);

  // ── admin again ──
  await visible(page, t.email).fill(adminEmail);
  await visible(page, t.password).fill(adminPassword);
  await page.getByRole("main").getByRole("button", { name: t.login, exact: true }).click();
  await page.waitForURL(/\/admin$/);
  await expect(page.getByText(l.adminArea)).toBeVisible();
  await expect(page.getByRole("navigation", { name: l.sections })).toBeVisible();
  await expect(page.getByText(barberEmail)).toHaveCount(0);
});

test("two tabs: a logout in one ends the session in the other; a login with another account switches it", async ({ browser }, info) => {
  const { lang } = variant(info);
  const l = L[lang];
  const { adminEmail, adminPassword } = state();
  const ctx = await browser.newContext({ ...info.project.use });
  const one = await ctx.newPage();
  const two = await ctx.newPage();

  await loginOnLoginPage(one, lang, adminEmail, adminPassword);
  await one.waitForURL(/\/admin$/);
  await two.goto(`/${lang}/admin`);
  await expect(two.getByRole("navigation", { name: l.sections })).toBeVisible();

  // log out in tab one → tab two shows the login at once (no reload), nothing of the admin view
  await one.getByRole("main").getByRole("button", { name: l.logout }).click();
  await expect(two.getByRole("heading", { name: l.staffLogin })).toBeVisible();
  await expect(two.getByRole("navigation", { name: l.sections })).toHaveCount(0);

  // log in as a customer in tab one → tab two follows: a customer has no access to /admin
  const customerEmail = await seededCustomer(lang, 0, "tabs");
  await loginOnLoginPage(one, lang, customerEmail, PASSWORD);
  await expect(one.getByRole("heading", { name: T[lang].hello })).toBeVisible();
  await expect(two.getByText(l.noAccess)).toBeVisible();
  await ctx.close();
});

test("login page while logged in: shows the account and offers a logout to switch (axe, keyboard)", async ({ page }, info) => {
  const { lang } = variant(info);
  const t = T[lang];
  const l = L[lang];
  const { adminEmail, adminPassword } = state();
  await loginOnLoginPage(page, lang, adminEmail, adminPassword);
  await page.waitForURL(/\/admin$/);

  await page.goto(`/${lang}/account/login`);
  const card = page.locator("[data-signed-in]");
  await expect(card.getByRole("heading", { name: l.signedIn })).toBeVisible();
  await expect(card).toContainText(adminEmail);
  await expect(card).toContainText(l.admin);
  await expect(page.getByRole("tab")).toHaveCount(0); // no second login on top of the session
  const axe = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"]).analyze();
  expect(axe.violations.filter((v) => v.impact === "serious" || v.impact === "critical").map((v) => v.id)).toEqual([]);
  await shot(page, info, "131-login-signed-in");

  // keyboard: log out → the login form with the customer / barber tabs is there for the next account
  await card.getByRole("button", { name: l.switchAccount }).focus();
  await page.keyboard.press("Enter");
  await expect(page.getByRole("tab", { name: t.asCustomer })).toBeVisible();
  await expect(page.getByRole("tab", { name: t.asBarber })).toBeVisible();
  await expect(page.getByRole("banner").getByRole("link", { name: t.login, exact: true })).toBeVisible();
});
