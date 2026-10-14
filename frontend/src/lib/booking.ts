"use client";

import { siteConfig } from "@/config/site.config";
import { scrollToTarget } from "./scroll";

export const BOOKING_OPEN_EVENT = "booking:open";

/**
 * Single entry point used by every "Book Appointment" button.
 *
 * Phase 1: the booking flow doesn't exist yet, so this dispatches an event
 * (which the Phase 3 booking modal will listen to) and, while nothing handles it,
 * falls back to scrolling to the contact block in the footer.
 */
export function openBooking(source = "unknown") {
  const event = new CustomEvent(BOOKING_OPEN_EVENT, { detail: { source }, cancelable: true });
  const notHandled = window.dispatchEvent(event); // false if a listener called preventDefault()
  if (notHandled) scrollToTarget(`#${siteConfig.contact.anchor}`);
}
