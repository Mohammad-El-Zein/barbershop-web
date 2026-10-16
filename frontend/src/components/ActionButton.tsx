"use client";

import { useTranslations } from "next-intl";
import type { Cta } from "@/config/site.config";
import { openBooking } from "@/lib/booking";
import { scrollToTarget } from "@/lib/scroll";

type Props = {
  cta: Cta;
  size?: "md" | "lg";
  className?: string;
  /** Where the click came from — forwarded to the booking flow (later: analytics). */
  source?: string;
};

/** Renders a config CTA: "book" opens the booking flow, "#x" scrolls, anything else is a link. */
export function ActionButton({ cta, size = "md", className = "", source }: Props) {
  const t = useTranslations();
  const label = t(cta.labelKey);
  const cls = `btn ${cta.variant === "primary" ? "btn-primary" : "btn-ghost"} ${size === "lg" ? "btn-lg" : ""} ${className}`;

  if (cta.action === "book") {
    return (
      <button type="button" className={cls} onClick={() => openBooking(source)}>
        {label}
        {cta.variant === "primary" && <Arrow />}
      </button>
    );
  }

  return (
    <a
      href={cta.action}
      className={cls}
      onClick={(e) => {
        if (scrollToTarget(cta.action)) e.preventDefault();
      }}
    >
      {label}
    </a>
  );
}

function Arrow() {
  return (
    <svg width="16" height="10" viewBox="0 0 16 10" fill="none" aria-hidden="true">
      <path d="M0 5h14M10 1l4 4-4 4" stroke="currentColor" strokeWidth="1.5" />
    </svg>
  );
}
