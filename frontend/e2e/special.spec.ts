import { expect, type Page, test } from "@playwright/test";
import {
  freeSlot,
  guestBooking,
  HAIRCUT,
  LUCA,
  linkIn,
  nameFor,
  shot,
  sql,
  state,
  uniqueEmail,
  variant,
  waitForMail,
} from "./support";
import { seededCustomer, uiLogin } from "./accounts";
import { pickSlot, T, type Texts } from "./texts";

/** /booking?services=haircut (opens at the date step) → slot → details (guest) → summary. */
async function guestToSummary(page: Page, t: Texts, lang: "de" | "en", slot: { day: string; time: string }, email: string) {
  await page.goto(`/${lang}/booking?services=${HAIRCUT}`);
  const flow = page.getByRole("main");
  // the preselected service skips step 1 and opens the date step
  await pickSlot(flow, t, lang, slot, "Luca");
  await flow.getByRole("button", { name: t.next }).click();
  await flow.getByLabel(t.name, { exact: true }).fill(nameFor(email));
  await flow.getByLabel(t.email, { exact: true }).fill(email);
  await flow.getByLabel(t.phone, { exact: true }).fill("+49 151 2345678");
  await flow.getByRole("checkbox", { name: t.consent }).check();
  await flow.getByRole("button", { name: t.next }).click();
  await expect(flow.getByRole("button", { name: t.send })).toBeVisible();
  return flow;
}

test("11th visit: a seeded account with 10 visits gets the loyalty discount", async ({ page }, info) => {
  const { lang } = variant(info);
  const t = T[lang];
  const email = await seededCustomer(lang, 10, "loyal");
  const slot = await freeSlot(LUCA, [HAIRCUT], 3);

  await uiLogin(page, t, lang, email);
  await expect(page.getByText(t.loyaltyReady)).toBeVisible();
  await shot(page, info, "30-loyalty-dashboard");

  await page.goto(`/${lang}/booking?services=${HAIRCUT}`);
  const flow = page.getByRole("main");
  await pickSlot(flow, t, lang, slot, "Luca");
  await flow.getByRole("button", { name: t.next }).click();
  await expect(flow.getByText(nameFor(email)).first()).toBeVisible(); // "booking with your account"
  await shot(page, info, "31-loyalty-details-logged-in");
  await flow.getByRole("button", { name: t.next }).click();

  // summary: price preview with 50 % off the 35 € haircut
  await expect(flow.getByText(t.loyaltyApplies)).toBeVisible();
  await expect(flow.getByText(t.loyaltyDiscount, { exact: true })).toBeVisible();
  await expect(flow.getByText(t.discountAmount).first()).toBeVisible();
  await shot(page, info, "32-loyalty-summary");
  await flow.getByRole("button", { name: t.send }).click();
  await expect(page.getByRole("heading", { name: t.sent })).toBeVisible();

  const stored = sql(
    `select b.loyalty_discount_cents || '/' || b.loyalty_status from booking b join app_user u on u.id = b.user_id
     where lower(u.email) = lower('${email}') order by b.created_at desc limit 1`,
  );
  expect(stored).toBe("1750/RESERVED");
});

test("expired and already-used email links show clear messages", async ({ page }, info) => {
  const { lang } = variant(info);
  const t = T[lang];
  const { barberEmail } = state();

  // expired: the barber's decision link after its deadline
  const emailA = uniqueEmail(`expired-${lang}`);
  const a = await guestBooking(LUCA, emailA, lang);
  const mailA = await waitForMail(barberEmail, `Neue Terminanfrage: ${nameFor(emailA)}`);
  sql(`update action_token set expires_at = now() - interval '1 minute' where booking_id = '${a.id}' and purpose = 'BARBER_DECISION'`);
  await page.goto(linkIn(mailA, "/barber/requests/").replace(/^\/de\//, `/${lang}/`));
  await expect(page.getByRole("heading", { name: t.expired })).toBeVisible();
  await expect(page.getByRole("button", { name: /Termin annehmen|Accept/ })).toHaveCount(0);
  await shot(page, info, "40-link-expired");

  // used: accept once, then open the same link again
  const emailB = uniqueEmail(`used-${lang}`);
  await guestBooking(LUCA, emailB, lang, 1);
  const mailB = await waitForMail(barberEmail, `Neue Terminanfrage: ${nameFor(emailB)}`);
  const link = linkIn(mailB, "/barber/requests/");
  await page.goto(link);
  await page.getByRole("button", { name: "Termin annehmen" }).click();
  await expect(page.getByRole("heading", { name: "Angenommen" })).toBeVisible();
  await page.goto(link.replace(/^\/de\//, `/${lang}/`));
  await expect(page.getByRole("heading", { name: t.used })).toBeVisible();
  await shot(page, info, "41-link-used");

  // and the GET of the page did not change anything: A is still waiting for the barber
  expect(sql(`select status from booking where id = '${a.id}'`)).toBe("PENDING_BARBER");
});

test("double booking: the second customer for the same slot is told it's taken", async ({ browser }, info) => {
  const { lang } = variant(info);
  const t = T[lang];
  const slot = await freeSlot(LUCA, [HAIRCUT], 4);
  const ctxA = await browser.newContext(info.project.use);
  const ctxB = await browser.newContext(info.project.use);
  const pageA = await ctxA.newPage();
  const pageB = await ctxB.newPage();

  // both reach the summary for the same free slot
  const flowA = await guestToSummary(pageA, t, lang, slot, uniqueEmail(`first-${lang}`));
  const flowB = await guestToSummary(pageB, t, lang, slot, uniqueEmail(`second-${lang}`));

  await flowA.getByRole("button", { name: t.send }).click();
  await expect(pageA.getByRole("heading", { name: t.sent })).toBeVisible();

  await flowB.getByRole("button", { name: t.send }).click();
  await expect(flowB.getByText(t.slotTaken)).toBeVisible();
  // back on the slot step with fresh data: the slot is greyed out and can't be chosen
  const takenSlot = flowB.getByRole("button", { name: `${slot.time} – Luca (${t.taken})`, exact: true });
  await expect(takenSlot).toBeDisabled();
  await shot(pageB, info, "50-double-booking");

  await ctxA.close();
  await ctxB.close();
});
