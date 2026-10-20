import { expect, type Page, test } from "@playwright/test";
import { api, guestBooking, LUCA, login, MO, nameFor, shot, state, uniqueEmail, variant } from "./support";
import { T } from "./texts";

/**
 * Admin deletion: a booking card opens its details (customer, money, history) with a delete
 * button for closed bookings; a service is deleted for good; a customer's history is erased
 * while the active booking stays. Rules in detail: backend ErasureIT.
 */

const L = {
  de: {
    sections: "Bereiche",
    bookingSearch: "Suche (Name, E-Mail, Telefon)",
    customerSearch: "Suche",
    details: "Buchungsdetails",
    booked: "Gebucht",
    declined: "Abgelehnt",
    deleteBooking: "Termin löschen",
    confirmTitle: "Termin endgültig löschen?",
    deleteFull: "Der Termin wird mit allen Daten vollständig gelöscht.",
    deleted: "Termin gelöscht.",
    activeHint: "Aktive Termine (angefragt, Zahlung offen oder bestätigt) können nicht gelöscht werden. Bitte zuerst stornieren.",
    del: "Löschen",
    forGood: "Endgültig löschen",
    noBookings: "Es sind keine Buchungen betroffen.",
    deleteHistory: "Verlauf löschen",
    willDelete: "1 Buchung wird vollständig gelöscht.",
    keptActive: "1 aktive Buchung bleibt bestehen.",
    done: "Gelöscht: 1, anonymisiert: 0.",
  },
  en: {
    sections: "Sections",
    bookingSearch: "Search (name, email, phone)",
    customerSearch: "Search",
    details: "Booking details",
    booked: "Booked",
    declined: "Declined",
    deleteBooking: "Delete booking",
    confirmTitle: "Delete this booking for good?",
    deleteFull: "The booking is deleted completely with all its data.",
    deleted: "Booking deleted.",
    activeHint: "Active bookings (requested, payment open or confirmed) can’t be deleted. Please cancel first.",
    del: "Delete",
    forGood: "Delete for good",
    noBookings: "No bookings are affected.",
    deleteHistory: "Delete history",
    willDelete: "1 booking is deleted completely.",
    keptActive: "1 active booking stays.",
    done: "Deleted: 1, anonymised: 0.",
  },
};

async function adminLogin(page: Page, lang: "de" | "en") {
  await page.goto(`/${lang}/admin`);
  await page.getByLabel(T[lang].email, { exact: true }).fill(state().adminEmail);
  await page.getByLabel(T[lang].password, { exact: true }).fill(state().adminPassword);
  await page.keyboard.press("Enter");
  await expect(page.getByRole("navigation", { name: L[lang].sections })).toBeVisible();
}

test("booking card → details with history → delete a declined booking; an active one can't be", async ({ page }, info) => {
  const { lang } = variant(info);
  const t = L[lang];
  const token = await login(state().adminEmail, state().adminPassword);
  const email = uniqueEmail("del-booking");
  const closed = await guestBooking(LUCA, email, lang, 0);
  expect((await api("POST", `/api/admin/bookings/${closed.id}/decline`, {}, { token })).status).toBe(200);
  const active = await guestBooking(MO, email, lang, 1);

  await adminLogin(page, lang);
  await page.goto(`/${lang}/admin/bookings`);
  await page.getByLabel(t.bookingSearch).fill(nameFor(email));
  const closedCard = page.locator(`[data-booking-id="${closed.id}"]`);
  const activeCard = page.locator(`[data-booking-id="${active.id}"]`);
  await expect(closedCard).toBeVisible();
  await expect(activeCard).toBeVisible();

  // the active one: details, but no delete
  await activeCard.getByText(nameFor(email)).click(); // anywhere on the card
  let details = page.getByRole("dialog", { name: t.details });
  await expect(details.getByText(t.activeHint)).toBeVisible();
  await expect(details.getByRole("button", { name: t.deleteBooking })).toHaveCount(0);
  await page.keyboard.press("Escape");
  await expect(details).toHaveCount(0);

  // the declined one: contact, history, delete
  await closedCard.getByRole("button", { name: "Details" }).click(); // the keyboard way
  details = page.getByRole("dialog", { name: t.details });
  await expect(details.getByRole("link", { name: email })).toHaveAttribute("href", `mailto:${email}`);
  await expect(details.getByRole("listitem").filter({ hasText: t.booked })).toBeVisible();
  await expect(details.getByRole("listitem").filter({ hasText: t.declined })).toBeVisible();
  await shot(page, info, "130-booking-details");
  await details.getByRole("button", { name: t.deleteBooking }).click();
  const confirm = page.getByRole("dialog", { name: t.confirmTitle });
  await expect(confirm.getByText(t.deleteFull)).toBeVisible();
  await confirm.getByRole("button", { name: t.deleteBooking }).click();

  await expect(page.getByText(t.deleted)).toBeVisible();
  await expect(closedCard).toHaveCount(0);
  await expect(activeCard).toBeVisible();
  expect((await api("GET", `/api/admin/bookings/${closed.id}`, undefined, { token })).status).toBe(404);
  await api("POST", `/api/admin/bookings/${active.id}/cancel`, {}, { token }); // free the slot again
});

test("a service in no booking is deleted for good", async ({ page }, info) => {
  const { lang } = variant(info);
  const t = L[lang];
  const token = await login(state().adminEmail, state().adminPassword);
  const name = `E2E Löschtest ${Date.now().toString(36)}`;
  const created = await api<{ id: string }>("POST", "/api/admin/services", {
    nameDe: name, nameEn: name, durationMin: 15, priceCents: 1000, priceFrom: false, active: true, sortOrder: 99,
  }, { token });
  expect(created.status).toBe(201);

  await adminLogin(page, lang);
  await page.goto(`/${lang}/admin/services`);
  const item = page.getByRole("listitem").filter({ hasText: name });
  await item.getByRole("button", { name: t.del, exact: true }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog.getByText(t.noBookings)).toBeVisible();
  await dialog.getByRole("button", { name: t.forGood }).click();
  await expect(item).toHaveCount(0);
  expect((await api("GET", `/api/admin/services/${created.body.id}`, undefined, { token })).status).toBe(404);
});

test("a customer's history is deleted, the active booking stays", async ({ page }, info) => {
  const { lang } = variant(info);
  const t = L[lang];
  const token = await login(state().adminEmail, state().adminPassword);
  const email = uniqueEmail("del-history");
  const old = await guestBooking(LUCA, email, lang, 2);
  expect((await api("POST", `/api/admin/bookings/${old.id}/cancel`, {}, { token })).status).toBe(200);
  const open = await guestBooking(MO, email, lang, 3);

  await adminLogin(page, lang);
  await page.goto(`/${lang}/admin/customers`);
  await page.getByLabel(t.customerSearch, { exact: true }).fill(email);
  const row = page.getByRole("row").filter({ hasText: email });
  await row.getByRole("button", { name: t.deleteHistory }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog.getByText(t.willDelete)).toBeVisible();
  await expect(dialog.getByText(t.keptActive)).toBeVisible();
  await dialog.getByRole("button", { name: t.forGood }).click();

  await expect(page.getByText(t.done)).toBeVisible();
  await expect(row).toHaveCount(1); // the active booking keeps the customer in the list
  expect((await api("GET", `/api/admin/bookings/${old.id}`, undefined, { token })).status).toBe(404);
  expect((await api("GET", `/api/admin/bookings/${open.id}`, undefined, { token })).status).toBe(200);
  await api("POST", `/api/admin/bookings/${open.id}/cancel`, {}, { token });
});
