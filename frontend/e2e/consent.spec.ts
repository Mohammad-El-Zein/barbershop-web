import { expect, type Page, test } from "@playwright/test";
import { freeSlot, HAIRCUT, login, LUCA, nameFor, shot, shotFile, sql, state, uniqueEmail, variant, api } from "./support";
import { pickSlot, T } from "./texts";

/**
 * Cookie consent, tracking and campaign attribution. These tests start WITHOUT a
 * stored decision (the other specs get "only necessary" via storageState).
 *
 * Tracking adapters only exist in a build with (dummy) tool ids, e.g.
 *   NEXT_PUBLIC_GA4_ID=G-E2ETEST000 NEXT_PUBLIC_META_PIXEL_ID=1234567890 \
 *   NEXT_PUBLIC_GOOGLE_ADS_ID=AW-000000000 NEXT_PUBLIC_GOOGLE_ADS_CONVERSION_LABEL=e2e npm run build
 * Run with E2E_TRACKING_BUILD=1 to assert the scripts load after consent. Requests to
 * Google / Meta are intercepted and answered locally — nothing leaves the machine.
 */
test.use({ storageState: { cookies: [], origins: [] } });

const TRACKING_BUILD = process.env.E2E_TRACKING_BUILD === "1";
const TRACKER = /googletagmanager\.com|google-analytics\.com|connect\.facebook\.net|facebook\.com\/tr/;

const C = {
  de: { title: "Cookies & Datenschutz", accept: "Alle akzeptieren", reject: "Nur notwendige", settings: "Einstellungen", save: "Auswahl speichern", statistics: /Statistik/, marketing: /Marketing/, reopen: "Cookie-Einstellungen" },
  en: { title: "Cookies & privacy", accept: "Accept all", reject: "Only necessary", settings: "Settings", save: "Save selection", statistics: /Statistics/, marketing: /Marketing/, reopen: "Cookie settings" },
};

/** Records (and locally answers) every request to an analytics / ad host. */
async function watchTrackers(page: Page) {
  const hits: string[] = [];
  await page.route(TRACKER, (route) => {
    hits.push(route.request().url());
    return route.fulfill({ status: 200, contentType: "text/javascript", body: "/* e2e stub */" });
  });
  return hits;
}

async function trackingCookies(page: Page) {
  return (await page.context().cookies()).filter((c) => /^(_ga|_gid|_gcl|_fbp|_fbc)/.test(c.name)).map((c) => c.name);
}

test("banner: only necessary by default, equal choices, nothing tracked before consent, re-openable", async ({ page }, info) => {
  const { lang } = variant(info);
  const c = C[lang];
  const hits = await watchTrackers(page);

  await page.goto(`/${lang}/booking`);
  const banner = page.getByRole("dialog", { name: c.title });
  await expect(banner).toBeVisible();
  // equal prominence: same class for accept and reject
  const accept = banner.getByRole("button", { name: c.accept });
  const reject = banner.getByRole("button", { name: c.reject });
  expect(await accept.getAttribute("class")).toBe(await reject.getAttribute("class"));
  await shot(page, info, "100-consent-banner");

  // before any decision: no tracker request, no tracker cookie, nothing stored
  await page.waitForLoadState("networkidle");
  expect(hits).toEqual([]);
  expect(await trackingCookies(page)).toEqual([]);
  expect(await page.evaluate(() => localStorage.getItem("consent"))).toBeNull();

  // settings: necessary always on, statistics + marketing off by default
  await banner.getByRole("button", { name: c.settings }).click();
  await expect(banner.getByRole("checkbox").first()).toBeChecked();
  await expect(banner.getByRole("checkbox").first()).toBeDisabled();
  await expect(banner.getByRole("checkbox", { name: c.statistics })).not.toBeChecked();
  await expect(banner.getByRole("checkbox", { name: c.marketing })).not.toBeChecked();
  await shot(page, info, "101-consent-settings");

  await banner.getByRole("button", { name: c.reject }).click();
  await expect(banner).toBeHidden();
  const stored = JSON.parse((await page.evaluate(() => localStorage.getItem("consent")))!);
  expect(stored).toMatchObject({ v: 1, statistics: false, marketing: false });
  expect(Date.now() - new Date(stored.at).getTime()).toBeLessThan(60_000);

  // decision remembered; still nothing loaded after a reload
  await page.reload();
  await page.waitForLoadState("networkidle");
  await expect(page.getByRole("dialog", { name: c.title })).toHaveCount(0);
  expect(hits).toEqual([]);

  // re-open from the footer and change the choice
  await page.getByRole("contentinfo").getByRole("button", { name: c.reopen }).click();
  const again = page.getByRole("dialog", { name: c.title });
  await expect(again).toBeVisible();
  await expect(again).toBeFocused();
  await again.getByRole("checkbox", { name: c.statistics }).check();
  await again.getByRole("button", { name: c.save }).click();
  await expect(again).toBeHidden();
  expect(JSON.parse((await page.evaluate(() => localStorage.getItem("consent")))!)).toMatchObject({ statistics: true, marketing: false });

  if (TRACKING_BUILD) {
    // statistics → GA4 only; no Meta pixel without marketing consent
    await expect.poll(() => hits.some((u) => u.includes("googletagmanager.com"))).toBe(true);
    expect(hits.some((u) => u.includes("facebook"))).toBe(false);
  }
});

test("marketing consent loads the ad tools; withdrawing it removes them", async ({ page }, info) => {
  test.skip(!TRACKING_BUILD, "needs a build with dummy tool ids (E2E_TRACKING_BUILD=1)");
  const { lang } = variant(info);
  const c = C[lang];
  const hits = await watchTrackers(page);
  await page.goto(`/${lang}/booking`);
  await page.getByRole("dialog", { name: c.title }).getByRole("button", { name: c.accept }).click();
  await expect.poll(() => hits.some((u) => u.includes("connect.facebook.net"))).toBe(true);
  expect(hits.some((u) => u.includes("googletagmanager.com"))).toBe(true);

  // withdraw everything → page reloads without any tracker
  await page.getByRole("contentinfo").getByRole("button", { name: c.reopen }).click();
  const before = hits.length;
  await Promise.all([
    page.waitForEvent("load"),
    page.getByRole("dialog", { name: c.title }).getByRole("button", { name: c.reject }).click(),
  ]);
  await page.waitForLoadState("networkidle");
  expect(hits.slice(before)).toEqual([]);
  expect(await trackingCookies(page)).toEqual([]);
});

test("ad landing page + UTM: attributed only with marketing consent, visible in the campaign stats", async ({ page, browser }, info) => {
  const { lang } = variant(info);
  const t = T[lang];
  const c = C[lang];
  await watchTrackers(page);
  const ad = `/${lang}/lp/herbst-fade?utm_source=google&utm_medium=cpc&utm_campaign=herbst-fade-e2e&gclid=E2E-${Date.now()}`;

  // 1) no consent yet: nothing is stored
  await page.goto(ad);
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
  await page.screenshot({ path: shotFile(info, "110-landing-page") });
  expect(await page.evaluate(() => localStorage.getItem("attribution"))).toBeNull();

  // 2) marketing consent on the same page → first touch is stored (30 days)
  await page.getByRole("dialog", { name: c.title }).getByRole("button", { name: c.accept }).click();
  const stored = JSON.parse((await page.evaluate(() => localStorage.getItem("attribution")))!);
  expect(stored).toMatchObject({ source: "google", medium: "cpc", campaign: "herbst-fade-e2e" });
  expect(stored.gclid).toMatch(/^E2E-/);
  expect(stored.landingPath).toBe(`/${lang}/lp/herbst-fade?utm_source=google&utm_medium=cpc&utm_campaign=herbst-fade-e2e&gclid=1`);

  // 3) book through the landing page's CTA (haircut preselected)
  const email = uniqueEmail(`lp-${lang}`);
  const slot = await freeSlot(LUCA, [HAIRCUT], 5);
  await page.getByRole("main").getByRole("button", { name: lang === "de" ? "Freien Termin finden" : "Find a free slot" }).first().click();
  const dialog = page.getByRole("dialog", { name: lang === "de" ? "Termin buchen" : "Book an appointment" });
  await pickSlot(dialog, t, lang, slot, "Luca");
  await dialog.getByRole("button", { name: t.next }).click();
  await dialog.getByLabel(t.name, { exact: true }).fill(nameFor(email));
  await dialog.getByLabel(t.email, { exact: true }).fill(email);
  await dialog.getByLabel(t.phone, { exact: true }).fill("+49 151 2345678");
  await dialog.getByRole("checkbox", { name: t.consent }).check();
  await dialog.getByRole("button", { name: t.next }).click();
  await dialog.getByRole("button", { name: t.send }).click();
  await expect(page.getByRole("heading", { name: t.sent })).toBeVisible();

  expect(sql(`select attr_source || '|' || attr_medium || '|' || attr_campaign || '|' || (attr_gclid is not null) from booking where customer_email = '${email}'`))
    .toBe("google|cpc|herbst-fade-e2e|true");

  // 4) a visitor who rejects marketing: same ad link, no attribution on the booking
  const other = await browser.newContext({ ...info.project.use, storageState: { cookies: [], origins: [] } });
  const p2 = await other.newPage();
  await p2.goto(ad);
  await p2.getByRole("dialog", { name: c.title }).getByRole("button", { name: c.reject }).click();
  expect(await p2.evaluate(() => localStorage.getItem("attribution"))).toBeNull();
  await other.close();

  // 5) the admin sees which ad produced the booking
  const token = await login(state().adminEmail, state().adminPassword);
  const today = new Date().toLocaleDateString("en-CA", { timeZone: "Europe/Berlin" });
  const stats = await api<{ campaigns: { source: string; campaign: string; bookings: number }[] }>(
    "GET", `/api/admin/stats/campaigns?from=${today}&to=${today}`, undefined, { token },
  );
  expect(stats.body.campaigns).toContainEqual(expect.objectContaining({ source: "google", campaign: "herbst-fade-e2e" }));

  // screenshot of the admin dashboard with the campaign table
  const adminCtx = await browser.newContext({ ...info.project.use });
  const admin = await adminCtx.newPage();
  await admin.goto(`/${lang}/admin`);
  await admin.getByLabel(t.email, { exact: true }).fill(state().adminEmail);
  await admin.getByLabel(t.password, { exact: true }).fill(state().adminPassword);
  await admin.getByRole("main").getByRole("button", { name: t.login, exact: true }).click();
  // the login form lives at /admin itself: wait until the staff area has rendered
  await expect(admin.getByRole("navigation", { name: lang === "de" ? "Bereiche" : "Sections" })).toBeVisible();
  await admin.goto(`/${lang}/admin/stats`);
  await expect(admin.getByRole("cell", { name: "herbst-fade-e2e" }).first()).toBeVisible();
  await shot(admin, info, "111-admin-campaigns");
  await adminCtx.close();
});
