"use client";

export const BOOKING_OPEN_EVENT = "booking:open";

export type BookingOpenDetail = {
  /** Where the click came from (later: analytics). */
  source: string;
  /** Preselected service ids (service-card buttons, "rebook"). */
  serviceIds?: string[];
  /** Preselected barber ("rebook"). */
  staffId?: string;
};

/**
 * Single entry point used by every "Book" button. The booking modal
 * (components/booking/BookingDialog, mounted in the locale layout) listens for
 * the event; if nothing handles it (e.g. JS error), we fall back to the
 * full-page booking flow.
 */
export function openBooking(source = "unknown", preset: Omit<BookingOpenDetail, "source"> = {}) {
  const detail: BookingOpenDetail = { source, ...preset };
  const event = new CustomEvent<BookingOpenDetail>(BOOKING_OPEN_EVENT, { detail, cancelable: true });
  const notHandled = window.dispatchEvent(event); // false if a listener called preventDefault()
  if (notHandled) {
    const locale = document.documentElement.lang || "de";
    const q = new URLSearchParams();
    if (preset.serviceIds?.length) q.set("services", preset.serviceIds.join(","));
    if (preset.staffId) q.set("staff", preset.staffId);
    // no React tree to route with here: plain navigation to the booking page
    // eslint-disable-next-line @next/next/no-location-assign-relative-destination
    window.location.assign(`/${locale}/booking${q.size ? `?${q}` : ""}`);
  }
}
