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

/**
 * axe does not see the list of a closed <select>. Chrome/Edge on Windows and Firefox draw it
 * with the colours of the <option>s, and a transparent option background falls back to white
 * (light text on white in the dark theme, once). Checked: every option (computed colours, a
 * transparent background counts as white) has ≥ 4.5:1, and date/time inputs and selects use
 * the theme's color-scheme (dark pickers and icons in the dark theme).
 */
async function nativeControls(page: Page, theme: "dark" | "light", label: string) {
  const problems = await page.evaluate((theme) => {
    const rgba = (c: string) => {
      const m = c.match(/rgba?\(([\d.]+),? ([\d.]+),? ([\d.]+)(?:,? \/? ?([\d.]+))?\)/);
      if (!m) throw new Error(`unparsed colour ${c}`);
      return [Number(m[1]), Number(m[2]), Number(m[3]), m[4] === undefined ? 1 : Number(m[4])];
    };
    const luminance = ([r, g, b]: number[]) => {
      const ch = (v: number) => ((v /= 255) <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4);
      return 0.2126 * ch(r) + 0.7152 * ch(g) + 0.0722 * ch(b);
    };
    const out: string[] = [];
    for (const el of document.querySelectorAll<HTMLElement>("select, input[type=date], input[type=time]")) {
      const scheme = getComputedStyle(el).colorScheme;
      if (!scheme.includes(theme)) out.push(`${el.tagName.toLowerCase()}[${el.id || el.getAttribute("type")}]: color-scheme "${scheme}"`);
    }
    for (const option of document.querySelectorAll<HTMLOptionElement>("select option:not(:disabled)")) {
      const style = getComputedStyle(option);
      const bg = rgba(style.backgroundColor);
      const back = bg[3] === 1 ? bg : [255, 255, 255, 1]; // transparent → the UA's white list
      const fg = rgba(style.color);
      const text = fg.slice(0, 3).map((v, i) => v * fg[3] + back[i] * (1 - fg[3]));
      const [hi, lo] = [luminance(text), luminance(back)].sort((a, b) => b - a);
      const ratio = (hi + 0.05) / (lo + 0.05);
      if (ratio < 4.5) out.push(`option "${option.textContent?.trim()}": ${ratio.toFixed(2)}:1 (${style.color} on ${style.backgroundColor})`);
    }
    return out;
  }, theme);
  expect(problems, `${label}: native controls in the ${theme} theme`).toEqual([]);
}

test("native selects and date/time inputs are readable in both themes", async ({ page }, info) => {
  const { lang, theme } = variant(info);
  const t = T[lang];
  await page.goto(`/${lang}/admin`);
  await page.getByLabel(t.email, { exact: true }).fill(state().adminEmail);
  await page.getByLabel(t.password, { exact: true }).fill(state().adminPassword);
  await page.getByRole("main").getByRole("button", { name: t.login, exact: true }).click();
  await expect(page.getByRole("navigation", { name: lang === "de" ? "Bereiche" : "Sections" })).toBeVisible();
  await expect(page.locator(".skeleton")).toHaveCount(0, { timeout: 15_000 });
  await expect(page.locator("main select").first()).toBeVisible(); // calendar: barber filter + date
  await nativeControls(page, theme, "admin calendar");

  await page.goto(`/${lang}/admin/bookings`); // status filter, from/to dates
  await expect(page.locator(".skeleton")).toHaveCount(0, { timeout: 15_000 });
  await expect(page.locator("main select").first()).toBeVisible();
  await nativeControls(page, theme, "admin bookings");
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

test("landing: phones get the light scroll animation (sticky, native scrolling, never stuck), desktops the full one", async ({ page }, info) => {
  const { lang } = variant(info);
  const phone = info.project.name.startsWith("mobile");
  await page.goto(`/${lang}`);
  await page.locator("[data-preloader]").waitFor({ state: "detached", timeout: 60_000 });
  await expect(page.locator("canvas[data-turntable]")).toBeAttached(); // the animated scene, not the static one
  if (phone) {
    // no ScrollTrigger pin and no Lenis: the scene is sticky in a short track, the browser scrolls
    await expect(page.locator(".pin-spacer")).toHaveCount(0);
    expect(await page.locator("#scene").evaluate((el) => getComputedStyle(el).position)).toBe("sticky");
    expect(await page.evaluate(() => document.documentElement.classList.contains("lenis"))).toBe(false);
    const screens = await page.evaluate(() => document.querySelector(".scene-track")!.getBoundingClientRect().height / innerHeight);
    expect(screens).toBeLessThan(3.6); // one screen + the short track (desktop: 7)

    // the scene plays while scrolling: the services cards come in mid-way
    const card = page.locator("[data-card]").first();
    await expect(card).toBeHidden();
    await page.evaluate(() => document.querySelector<HTMLAnchorElement>('a[href$="#services"]')?.click());
    await expect(card).toBeVisible();

    // and it ends: plain scrolling reaches the footer, the scene has scrolled away
    await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight));
    await expect(page.locator("footer")).toBeInViewport();
    expect(await page.locator("#scene").evaluate((el) => el.getBoundingClientRect().bottom)).toBeLessThanOrEqual(0);
  } else {
    await expect(page.locator(".pin-spacer")).toHaveCount(1);
    await expect.poll(() => page.evaluate(() => document.documentElement.classList.contains("lenis"))).toBe(true);
  }

  // ?scene=static forces the fallback (the same one reduced motion and weak devices get)
  await page.goto(`/${lang}?scene=static`);
  await expect(page.locator("canvas[data-turntable]")).toHaveCount(0);
  await expect(page.locator(".pin-spacer")).toHaveCount(0);
  await expect(page.locator("section#services")).toBeAttached();
});

test("landing: a scene that keeps stuttering while scrolled falls back to the static one, without a reload, at the same chapter", async ({ page }, info) => {
  const { lang } = variant(info);
  await page.goto(`/${lang}`);
  await page.locator("[data-preloader]").waitFor({ state: "detached", timeout: 60_000 });
  await expect(page.locator("canvas[data-turntable]")).toBeAttached();
  await page.evaluate(() => ((window as unknown as { sameDocument: boolean }).sameDocument = true));
  await page.evaluate(() => document.querySelector<HTMLAnchorElement>('a[href$="#services"]')?.click());
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(200);
  await page.waitForTimeout(2000); // the smooth jump has ended
  // The chapter on screen (section dots) — normally the services. A headless desktop without a
  // GPU may stutter through the smooth jump and fall back already (then: -1, see below).
  const active = await page.evaluate(() =>
    [...document.querySelectorAll("#scene nav li button")].findIndex((b) => b.getAttribute("aria-current") === "step"),
  );

  // a device that can't keep up: every frame while scrolling takes ~70 ms
  await page.evaluate(async () => {
    for (let i = 0; i < 30 && document.querySelector("canvas[data-turntable]"); i++) {
      window.scrollBy(0, 2);
      const t = performance.now();
      while (performance.now() - t < 70);
      await new Promise((r) => requestAnimationFrame(r));
    }
  });
  await expect(page.locator("canvas[data-turntable]")).toHaveCount(0);
  await expect(page.locator(".pin-spacer")).toHaveCount(0);
  expect(await page.evaluate(() => (window as unknown as { sameDocument?: boolean }).sameDocument)).toBe(true);
  // the static scene continues at that chapter (fell back during the jump: at the one it was passing)
  if (active >= 0) await expect(page.locator("[data-chapter-section]").nth(active)).toBeInViewport();
  else expect(await page.evaluate(() => window.scrollY)).toBeGreaterThan(0);
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
