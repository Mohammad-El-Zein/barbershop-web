/**
 * Date/time formatting in the SHOP's timezone (appointments are local times of
 * the shop, whatever timezone the visitor's device is in).
 */
export const SHOP_TZ = "Europe/Berlin";

function fmt(locale: string, opts: Intl.DateTimeFormatOptions, tz = SHOP_TZ) {
  return new Intl.DateTimeFormat(locale === "en" ? "en-GB" : "de-DE", { timeZone: tz, ...opts });
}

/** "Di., 20. Okt." / "Tue, 20 Oct" */
export function shortDate(iso: string, locale: string, tz?: string) {
  return fmt(locale, { weekday: "short", day: "numeric", month: "short" }, tz).format(new Date(iso));
}

/** "Dienstag, 20. Oktober 2026" */
export function longDate(iso: string, locale: string, tz?: string) {
  return fmt(locale, { weekday: "long", day: "numeric", month: "long", year: "numeric" }, tz).format(new Date(iso));
}

/** "10:00" */
export function time(iso: string, locale: string, tz?: string) {
  return fmt(locale, { hour: "2-digit", minute: "2-digit" }, tz).format(new Date(iso));
}

/** "Dienstag, 20. Oktober 2026 · 10:00–11:15" */
export function dateTimeRange(start: string, end: string, locale: string, tz?: string) {
  return `${longDate(start, locale, tz)} · ${time(start, locale, tz)}–${time(end, locale, tz)}`;
}

/** "20.10.2026, 10:00" */
export function dateTime(iso: string, locale: string, tz?: string) {
  return fmt(locale, { day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" }, tz)
    .format(new Date(iso));
}

/** Calendar date "YYYY-MM-DD" of an instant in the shop's timezone. */
export function shopDay(date: Date, tz = SHOP_TZ) {
  const p = new Intl.DateTimeFormat("en-CA", { timeZone: tz, year: "numeric", month: "2-digit", day: "2-digit" })
    .format(date);
  return p; // en-CA formats as YYYY-MM-DD
}

/** "YYYY-MM-DD" + n days (calendar arithmetic, timezone-free). */
export function addDays(day: string, n: number) {
  const d = new Date(`${day}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
}

/** Weekday / day-of-month labels of a "YYYY-MM-DD" (rendered at noon UTC → no DST edge). */
export function dayParts(day: string, locale: string) {
  const d = new Date(`${day}T12:00:00Z`);
  const l = locale === "en" ? "en-GB" : "de-DE";
  return {
    weekday: new Intl.DateTimeFormat(l, { weekday: "short", timeZone: "UTC" }).format(d),
    day: new Intl.DateTimeFormat(l, { day: "numeric", timeZone: "UTC" }).format(d),
    month: new Intl.DateTimeFormat(l, { month: "short", timeZone: "UTC" }).format(d),
    long: new Intl.DateTimeFormat(l, { weekday: "long", day: "numeric", month: "long", timeZone: "UTC" }).format(d),
  };
}

/** Monday of the week containing "YYYY-MM-DD". */
export function mondayOf(day: string) {
  const d = new Date(`${day}T12:00:00Z`);
  const shift = (d.getUTCDay() + 6) % 7;
  return addDays(day, -shift);
}

/** "75 Min." / "1 h 15 min" style, kept simple: minutes. */
export function minutes(n: number, locale: string) {
  return locale === "en" ? `${n} min` : `${n} Min.`;
}

/** Cents → "35 €" / "€35" (whole euros without decimals), like the backend. */
export function money(cents: number, locale: string, currency = "EUR") {
  const whole = cents % 100 === 0;
  return new Intl.NumberFormat(locale === "en" ? "en-IE" : "de-DE", {
    style: "currency",
    currency,
    minimumFractionDigits: whole ? 0 : 2,
    maximumFractionDigits: whole ? 0 : 2,
  }).format(cents / 100);
}
