import { expect, type Page, test } from "@playwright/test";
import { dayLabel, T, type Texts } from "./texts";
import {
  BEARD,
  freeSlot,
  HAIRCUT,
  LUCA,
  linkIn,
  nameFor,
  shot,
  state,
  uniqueEmail,
  variant,
  waitForMail,
} from "./support";

/** "ab 60 €" / "from €60": booking amounts are exact, the prefix is only for the landing cards. */
const FROM_PRICE = /\bab \d|\bfrom €/;

async function openBookingFromLanding(page: Page, t: Texts, mobile: boolean) {
  await page.locator("[data-preloader]").waitFor({ state: "detached", timeout: 60_000 });
  if (mobile) {
    await page.getByRole("button", { name: t.menu }).click();
    await page.locator("#mobile-menu").getByRole("button", { name: t.book }).click();
  } else {
    await page.locator("header").getByRole("button", { name: t.book }).click();
  }
  const dialog = page.getByRole("dialog");
  await expect(dialog).toBeVisible();
  return dialog;
}

test("guest books → barber accepts via email link → demo payment → confirmed → my bookings, barber calendar → cancel with refund", async ({
  page,
  browser,
}, info) => {
  const { lang } = variant(info);
  const t = T[lang];
  const mobile = info.project.name.startsWith("mobile");
  const email = uniqueEmail(`guest-${lang}`);
  const name = nameFor(email);
  const { barberEmail, barberPassword } = state();
  const slot = await freeSlot(LUCA, [HAIRCUT, BEARD], 2);

  // ── landing → booking modal ──
  await page.goto(`/${lang}`);
  const dialog = await openBookingFromLanding(page, t, mobile);
  await shot(page, info, "01-modal-services");

  // 1) services: haircut + beard
  await dialog.getByRole("checkbox", { name: t.haircut }).check();
  await dialog.getByRole("checkbox", { name: t.beard }).check();
  await expect(dialog.getByText(/75 Min\.|75 min/).first()).toBeVisible();
  await shot(page, info, "02-modal-services-selected");
  await dialog.getByRole("button", { name: t.next }).click();

  // 2) day + Luca's slot (all barbers side by side)
  await dialog.getByRole("radiogroup", { name: t.date }).getByRole("radio", { name: dayLabel(slot.day, lang) }).click();
  const slotButton = dialog.getByRole("button", { name: `${slot.time} – Luca`, exact: true });
  await expect(slotButton).toBeEnabled();
  await shot(page, info, "03-modal-slots");
  await slotButton.click();
  await expect(slotButton).toHaveAttribute("aria-pressed", "true");
  await dialog.getByRole("button", { name: t.next }).click();

  // 3) details as guest
  await dialog.getByLabel(t.note).fill("E2E: please not too short on the sides");
  await dialog.getByLabel(t.name, { exact: true }).fill(name);
  await dialog.getByLabel(t.email, { exact: true }).fill(email);
  await dialog.getByLabel(t.phone, { exact: true }).fill("+49 151 2345678");
  await dialog.getByRole("checkbox", { name: t.consent }).check();
  await shot(page, info, "04-modal-details");
  await dialog.getByRole("button", { name: t.next }).click();

  // 4) summary → send
  await expect(dialog.getByText("Luca").first()).toBeVisible();
  await expect(dialog.getByText(/10\s€|€10/).first()).toBeVisible();
  await expect(dialog).not.toContainText(FROM_PRICE);
  await shot(page, info, "05-modal-summary");
  await dialog.getByRole("button", { name: t.send }).click();

  // 5) live status page
  await expect(page.getByRole("heading", { name: t.sent })).toBeVisible();
  const statusUrl = page.url();
  await shot(page, info, "06-status-pending");

  // barber: request email (German, Luca's own login) → decision page → accept (POST)
  const request = await waitForMail(barberEmail, `Neue Terminanfrage: ${name}`);
  expect(request.Text).toContain("Annehmen");
  expect(request.Text).toContain("Ablehnen");
  await page.goto(linkIn(request, "/barber/requests/"));
  await expect(page.getByRole("heading", { name: "Neue Anfrage" })).toBeVisible();
  await expect(page.getByText("E2E: please not too short on the sides")).toBeVisible();
  await shot(page, info, "07-barber-decision");
  await page.getByRole("button", { name: "Termin annehmen" }).click();
  await expect(page.getByRole("heading", { name: "Angenommen" })).toBeVisible();
  await shot(page, info, "08-barber-accepted");

  // status page shows "accepted: pay deposit" (from the backend status)
  await page.goto(statusUrl);
  await expect(page.getByRole("heading", { name: t.accepted })).toBeVisible();
  await shot(page, info, "09-status-accepted");
  await page.getByRole("button", { name: t.payNow }).click();

  // DEMO payment page: banner, amount, two buttons, no card fields
  await expect(page.getByRole("main").getByRole("note")).toHaveText(new RegExp(t.demo));
  // the site-wide DEMO strip is off unless the build asks for it (NEXT_PUBLIC_SHOW_DEMO_BANNER=true)
  await expect(page.locator(".demo-bar")).toHaveCount(0);
  await expect(page.locator("main input")).toHaveCount(0);
  await expect(page.getByRole("main")).not.toContainText(FROM_PRICE);
  await expect(page.locator('input[autocomplete^="cc-"], input[name*="card" i]')).toHaveCount(0);
  await shot(page, info, "10-pay-demo");
  await page.getByRole("button", { name: t.simulate }).click();
  await expect(page.getByRole("heading", { name: t.paid })).toBeVisible();
  await shot(page, info, "11-pay-success");

  // both confirmation emails, each in its recipient's language
  const confirmed = await waitForMail(email, t.confirmedMail);
  expect(confirmed.Text).not.toMatch(FROM_PRICE);
  expect((await waitForMail(email, lang === "de" ? "hat zugesagt" : "accepted")).Text).not.toMatch(FROM_PRICE);
  expect((await waitForMail(email, lang === "de" ? "Deine Terminanfrage" : "Your appointment request")).Text).not.toMatch(FROM_PRICE);
  expect(confirmed.Attachments.map((a) => a.FileName)).toContain(lang === "en" ? "appointment.ics" : "termin.ics");
  const paidMail = await waitForMail(barberEmail, `Anzahlung erhalten: ${name}`);
  expect(paidMail.Text).toMatch(/10\s€ Anzahlung bezahlt/);

  // the status page now says confirmed
  await page.goto(statusUrl);
  await expect(page.getByRole("heading", { name: lang === "de" ? "Bestätigt – bis bald!" : "Confirmed – see you soon!" })).toBeVisible();
  await shot(page, info, "12-status-confirmed");

  // register with the same email → verify → the guest booking appears in "my bookings"
  await page.goto(`/${lang}/account/register`);
  await page.getByLabel(t.name, { exact: true }).fill(name);
  await page.getByLabel(t.email, { exact: true }).fill(email);
  await page.getByLabel(t.phone, { exact: true }).fill("+49 151 2345678");
  await page.getByLabel(t.password, { exact: true }).fill("e2e-long-password");
  await page.getByRole("checkbox", { name: t.consent }).check();
  await shot(page, info, "13-register");
  await page.getByRole("button", { name: t.register, exact: true }).click();
  await expect(page.getByText(t.almost, { exact: true })).toBeVisible();
  const verify = await waitForMail(email, t.verifyMail);
  await page.goto(linkIn(verify, "/account/verify/"));
  await shot(page, info, "14-verify");
  await page.getByRole("button", { name: t.verifyBtn }).click();
  await expect(page.getByRole("heading", { name: t.verified })).toBeVisible();

  await page.goto(`/${lang}/account/login`);
  await page.getByLabel(t.email, { exact: true }).fill(email);
  await page.getByLabel(t.password, { exact: true }).fill("e2e-long-password");
  await shot(page, info, "15-login");
  await page.getByRole("main").getByRole("button", { name: t.login, exact: true }).click();
  await expect(page.getByRole("heading", { name: t.hello })).toBeVisible();
  const card = page.locator("li[data-booking-id]").filter({ hasText: "Luca" }).first();
  await expect(card.getByText(t.confirmedBadge, { exact: true })).toBeVisible();
  await expect(page.getByRole("main")).not.toContainText(FROM_PRICE);
  await shot(page, info, "16-my-bookings");

  // barber calendar (Luca's own login) shows the booking on that day
  const barberCtx = await browser.newContext({ ...info.project.use, baseURL: info.project.use.baseURL });
  const barber = await barberCtx.newPage();
  await barber.goto(`/${lang}/admin`);
  await barber.getByLabel(t.email, { exact: true }).fill(barberEmail);
  await barber.getByLabel(t.password, { exact: true }).fill(barberPassword);
  await barber.getByRole("main").getByRole("button", { name: t.login, exact: true }).click();
  await barber.waitForURL(/\/admin/);
  await barber.getByLabel(lang === "de" ? "Datum wählen" : "Pick a date").fill(slot.day);
  await expect(barber.locator("article[data-booking-id]").filter({ hasText: name })).toBeVisible();
  await shot(barber, info, "17-barber-calendar");
  await barberCtx.close();

  // cancel from "my bookings": the dialog says whether the deposit comes back
  await card.getByRole("button", { name: t.cancel, exact: true }).click();
  const confirmDialog = page.getByRole("dialog");
  await expect(confirmDialog.getByText(t.refund)).toBeVisible();
  await shot(page, info, "18-cancel-refund-info");
  await confirmDialog.getByRole("button", { name: t.yesCancel }).click();
  await expect(page.locator("li[data-booking-id]").filter({ hasText: "Luca" }).first().getByText(t.cancelledBadge, { exact: true })).toBeVisible();
  await shot(page, info, "19-cancelled");
  const cancelled = await waitForMail(email, t.cancelledMail);
  expect(cancelled.Text).toMatch(lang === "de" ? /erstattet/ : /refunded/);
});
