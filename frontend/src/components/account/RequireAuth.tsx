"use client";

import { useEffect } from "react";
import { usePathname, useRouter } from "@/i18n/navigation";
import { useAuth } from "@/lib/auth/AuthProvider";
import type { Role } from "@/lib/api/types";
import { Skeleton } from "../ui/Feedback";

/** Renders children only for a logged-in user (optionally with one of `roles`), else → login. */
export function RequireAuth({ children, roles }: { children: React.ReactNode; roles?: Role[] }) {
  const { status, user } = useAuth();
  const router = useRouter();
  const pathname = usePathname();
  const allowed = status === "authenticated" && (!roles || roles.includes(user?.role as Role));

  useEffect(() => {
    if (status === "anonymous") router.replace(`/account/login?next=${encodeURIComponent(pathname)}`);
  }, [status, router, pathname]);

  if (!allowed) {
    return (
      <div className="space-y-4" aria-busy="true">
        <Skeleton className="h-12 w-1/2" />
        <Skeleton className="h-64" />
      </div>
    );
  }
  return <>{children}</>;
}
