import { expect, type Locator } from "@playwright/test";

/** UI texts per language (the tests click what a user reads). */
export const T = {
  de: {
    book: "Termin buchen",
    menu: "Menü öffnen",
    haircut: /Haarschnitt/,
    beard: /Bart-Styling/,
    next: "Weiter",
    date: "Datum",
    name: "Name",
    email: "E-Mail",
    phone: "Telefon",
    note: "Notiz für deinen Barber (optional)",
    consent: /Ich akzeptiere die/,
    send: "Anfrage senden",
    sent: "Anfrage gesendet",
    accepted: "Angenommen – bitte Anzahlung leisten",
    payNow: "Jetzt bezahlen",
    demo: "DEMO-MODUS: keine echte Zahlung",
    demoSite: "Demo-Shop",
    simulate: "Zahlung simulieren",
    paid: "Bezahlt – dein Termin steht!",
    confirmedMail: "Termin bestätigt",
    register: "Registrieren",
    password: "Passwort",
    almost: "Konto angelegt",
    verifyMail: "Bitte bestätige",
    verifyBtn: "E-Mail bestätigen",
    verified: "Bestätigt!",
    login: "Anmelden",
    asCustomer: "Kunde",
    asBarber: "Barber",
    registerLink: "Noch kein Konto? Registrieren",
    hello: /Hallo/,
    confirmedBadge: "Bestätigt",
    cancel: "Stornieren",
    refund: /bekommst du deine Anzahlung von 10\s€ zurück/,
    yesCancel: "Ja, stornieren",
    cancelledBadge: "Storniert",
    cancelledMail: "wurde storniert",
    taken: "vergeben",
    slotTaken: "Diese Uhrzeit wurde gerade vergeben",
    loyaltyReady: /Deine nächste Buchung bekommt 50\s%\sTreuerabatt/,
    loyaltyApplies: /Dein Treuerabatt ist dran/,
    loyaltyDiscount: "Treuerabatt",
    discountAmount: /17,50\s€/,
    expired: "Link abgelaufen",
    used: "Link schon verwendet",
  },
  en: {
    book: "Book Appointment",
    menu: "Open menu",
    haircut: /Haircut/,
    beard: /Beard Sculpting/,
    next: "Next",
    date: "Date",
    name: "Name",
    email: "Email",
    phone: "Phone",
    note: "Note for your barber (optional)",
    consent: /I accept the/,
    send: "Send request",
    sent: "Request sent",
    accepted: "Accepted – please pay the deposit",
    payNow: "Pay now",
    demo: "DEMO MODE: no real payment",
    demoSite: "Demo shop",
    simulate: "Simulate payment",
    paid: "Paid – you’re booked!",
    confirmedMail: "Appointment confirmed",
    register: "Register",
    password: "Password",
    almost: "Account created",
    verifyMail: "Please confirm",
    verifyBtn: "Confirm email",
    verified: "Confirmed!",
    login: "Log in",
    asCustomer: "Customer",
    asBarber: "Barber",
    registerLink: "No account yet? Register",
    hello: /Hi /,
    confirmedBadge: "Confirmed",
    cancel: "Cancel",
    refund: /you get your €10 deposit back/,
    yesCancel: "Yes, cancel",
    cancelledBadge: "Cancelled",
    cancelledMail: "has been cancelled",
    taken: "taken",
    slotTaken: "This time was just taken",
    loyaltyReady: /Your next booking gets 50% loyalty discount/,
    loyaltyApplies: /Your loyalty reward is due/,
    loyaltyDiscount: "Loyalty discount",
    discountAmount: /€17\.50/,
    expired: "Link expired",
    used: "Link already used",
  },
};

export type Texts = (typeof T)["de"];

/** The date label the strip uses (aria-label), e.g. "Dienstag, 20. Oktober". */
export function dayLabel(day: string, lang: "de" | "en") {
  return new Intl.DateTimeFormat(lang === "en" ? "en-GB" : "de-DE", {
    weekday: "long",
    day: "numeric",
    month: "long",
    timeZone: "UTC",
  }).format(new Date(`${day}T12:00:00Z`));
}

/** Step 2 of the flow: pick the day in the strip, then the barber's slot. */
export async function pickSlot(scope: Locator, t: Texts, lang: "de" | "en", slot: { day: string; time: string }, barber: string) {
  await scope.getByRole("radiogroup", { name: t.date }).getByRole("radio", { name: dayLabel(slot.day, lang) }).click();
  const button = scope.getByRole("button", { name: `${slot.time} – ${barber}`, exact: true });
  await expect(button).toBeEnabled();
  await button.click();
  await expect(button).toHaveAttribute("aria-pressed", "true");
  return button;
}
