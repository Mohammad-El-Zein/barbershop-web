"use client";

import { openBooking } from "@/lib/booking";

/** Booking CTA of an ad landing page (source "lp-<slug>" for analytics). */
export function LpBookButton({
  label,
  slug,
  serviceIds,
  variant = "primary",
  className = "",
}: {
  label: string;
  slug: string;
  serviceIds?: string[];
  variant?: "primary" | "ghost";
  className?: string;
}) {
  return (
    <button
      type="button"
      className={`btn ${variant === "primary" ? "btn-primary btn-lg" : "btn-ghost btn-sm"} ${className}`}
      onClick={() => openBooking(`lp-${slug}`, { serviceIds })}
    >
      {label}
    </button>
  );
}
