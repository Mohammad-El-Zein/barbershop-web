"use client";

import { useEffect, useRef } from "react";
import { useTranslations } from "next-intl";
import { Link, usePathname, useRouter } from "@/i18n/navigation";
import { isLeavingAfterLogout, useAuth, useLogout } from "@/lib/auth/AuthProvider";
import type { Role } from "@/lib/api/types";
import { Alert, Skeleton } from "../ui/Feedback";

/**
 * Renders children only for a logged-in user (optionally with one of `roles`), else → login.
 * Logged in with another role (a barber or admin on the customer page): says so, with
 * "to my profile" and "log out". The children are keyed by the user, so an account switch
 * (also in another tab) never shows the previous account's data.
 */
export function RequireAuth({ children, roles }: { children: React.ReactNode; roles?: Role[] }) {
  const { status, user } = useAuth();
  const router = useRouter();
  const pathname = usePathname();
  const allowed = status === "authenticated" && (!roles || roles.includes(user?.role as Role));
  const sawSession = useRef(false);

  useEffect(() => {
    if (status === "authenticated") sawSession.current = true;
    // a logout started on this page goes to its own target (home / login)
    if (status === "anonymous" && !(sawSession.current && isLeavingAfterLogout())) router.replace(`/account/login?next=${encodeURIComponent(pathname)}`);
  }, [status, router, pathname]);

  if (status === "authenticated" && !allowed) return <WrongRole />;
  if (!allowed) {
    return (
      <div className="space-y-4" aria-busy="true">
        <Skeleton className="h-12 w-1/2" />
        <Skeleton className="h-64" />
      </div>
    );
  }
  return <div key={user?.id}>{children}</div>;
}

function WrongRole() {
  const t = useTranslations("account.wrongRole");
  const tRole = useTranslations("account.roles");
  const tNav = useTranslations("nav");
  const { user } = useAuth();
  const logout = useLogout("/account/login");
  return (
    <Alert tone="warn" title={t("customerTitle")}>
      <p>{t("customerText", { name: user?.name ?? "", role: tRole(user?.role ?? "CUSTOMER") })}</p>
      <span className="mt-3 flex flex-wrap gap-2">
        <Link href="/account/profile" className="btn btn-ghost btn-sm">
          {t("toProfile")}
        </Link>
        <button type="button" className="btn btn-ghost btn-sm" onClick={logout}>
          {tNav("logout")}
        </button>
      </span>
    </Alert>
  );
}
