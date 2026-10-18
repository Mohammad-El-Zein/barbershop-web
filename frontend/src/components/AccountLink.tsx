"use client";

import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { useAuth } from "@/lib/auth/AuthProvider";

/**
 * Person icon in the nav: "Log in" when anonymous, the account (customers) or the
 * staff area (barbers / admins) when logged in — with the user's initials.
 */
export function AccountLink() {
  const t = useTranslations("nav");
  const { status, user } = useAuth();
  const staff = user?.role === "BARBER" || user?.role === "ADMIN";
  const href = status === "authenticated" ? (staff ? "/admin" : "/account") : "/account/login";
  const label =
    status === "authenticated" ? (staff ? t("staffArea") : t("account")) : t("login");
  const initials = user?.name
    ?.split(/\s+/)
    .map((p) => p[0])
    .join("")
    .slice(0, 2)
    .toUpperCase();

  return (
    <Link
      href={href}
      aria-label={label}
      title={label}
      className="flex h-9 w-9 items-center justify-center rounded-full border border-ink/20 text-[0.65rem] font-semibold text-ink/85 transition-colors hover:border-gold-text hover:text-gold-text focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-gold-text"
    >
      {status === "authenticated" && initials ? (
        <span aria-hidden="true">{initials}</span>
      ) : (
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <circle cx="12" cy="8" r="4" stroke="currentColor" strokeWidth="1.6" />
          <path d="M4 21c1.5-4 4.5-6 8-6s6.5 2 8 6" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
        </svg>
      )}
    </Link>
  );
}
