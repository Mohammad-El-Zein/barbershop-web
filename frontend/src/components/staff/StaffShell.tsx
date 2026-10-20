"use client";

import { Suspense } from "react";
import { useTranslations } from "next-intl";
import { Link, usePathname } from "@/i18n/navigation";
import { useAuth } from "@/lib/auth/AuthProvider";
import { LoginForm } from "../account/AuthForms";
import { Alert, Skeleton } from "../ui/Feedback";

const TABS = [
  { href: "/admin", key: "calendar", roles: ["BARBER", "ADMIN"] },
  { href: "/admin/bookings", key: "bookings", roles: ["ADMIN"] },
  { href: "/admin/services", key: "services", roles: ["ADMIN"] },
  { href: "/admin/staff", key: "staff", roles: ["ADMIN"] },
  { href: "/admin/time-off", key: "timeOff", roles: ["ADMIN"] },
  { href: "/admin/customers", key: "customers", roles: ["ADMIN"] },
  { href: "/admin/stats", key: "stats", roles: ["ADMIN"] },
] as const;

/**
 * Staff area frame: login for barbers/admins, role-based tabs. Barbers see their
 * own calendar and requests; admins everything of their shop.
 */
export function StaffShell({ children }: { children: React.ReactNode }) {
  const t = useTranslations("staff");
  const { status, user } = useAuth();
  const pathname = usePathname();

  if (status === "loading") return <Skeleton className="h-96" />;
  if (status === "anonymous")
    return (
      <div className="mx-auto max-w-xl">
        <p className="eyebrow">{t("eyebrow")}</p>
        <h1 className="display mb-6 mt-3 text-4xl sm:text-5xl">{t("loginTitle")}</h1>
        <Suspense fallback={<Skeleton className="h-72" />}>
          <LoginForm staff />
        </Suspense>
      </div>
    );
  const role = user?.role as "BARBER" | "ADMIN" | "CUSTOMER";
  if (role === "CUSTOMER")
    return (
      <Alert tone="warn" title={t("noAccessTitle")}>
        {t("noAccess")}{" "}
        <Link href="/account" className="underline">
          {t("toAccount")}
        </Link>
      </Alert>
    );

  // signed in with a start password from the admin: an own password first
  if (user?.mustChangePassword)
    return (
      <Alert tone="warn" title={t("mustChangeTitle")}>
        {t("mustChange")}{" "}
        <Link href="/account/profile" className="underline">
          {t("toProfile")}
        </Link>
      </Alert>
    );

  const tabs = TABS.filter((tab) => (tab.roles as readonly string[]).includes(role));
  return (
    <div>
      <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="eyebrow">{t(role === "ADMIN" ? "adminArea" : "barberArea")}</p>
          <p className="mt-1 text-sm text-muted">{user?.name} · {user?.email}</p>
        </div>
        <Link href="/account/profile" className="btn btn-ghost btn-sm">
          {t("profile")}
        </Link>
      </div>
      {tabs.length > 1 && (
        <nav aria-label={t("sections")} className="-mx-4 mb-8 overflow-x-auto px-4">
          <ul className="flex min-w-max gap-1 border-b border-line">
            {tabs.map((tab) => {
              const active = tab.href === "/admin" ? pathname === "/admin" : pathname.startsWith(tab.href);
              return (
                <li key={tab.href}>
                  <Link
                    href={tab.href}
                    aria-current={active ? "page" : undefined}
                    // on phones the tab row scrolls sideways: keep the current tab visible
                    ref={active ? (el) => el?.scrollIntoView({ block: "nearest", inline: "center" }) : undefined}
                    className={`-mb-px block border-b-2 px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em] ${
                      active ? "border-gold text-gold-text" : "border-transparent text-ink/65 hover:text-ink"
                    }`}
                  >
                    {t(`tabs.${tab.key}`)}
                  </Link>
                </li>
              );
            })}
          </ul>
        </nav>
      )}
      {children}
    </div>
  );
}

/** Admin-only content (barbers get a short notice). */
export function AdminOnly({ children }: { children: React.ReactNode }) {
  const t = useTranslations("staff");
  const { user } = useAuth();
  if (user?.role !== "ADMIN") return <Alert tone="warn">{t("adminOnly")}</Alert>;
  return <>{children}</>;
}
