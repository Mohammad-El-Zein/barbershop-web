import { Suspense } from "react";
import { PageShell } from "../PageShell";
import { Skeleton } from "../ui/Feedback";

/**
 * Server wrapper for the email-link pages: the token is URL data that only
 * exists at request time, so the client part renders inside Suspense (the
 * static shell with nav + skeleton is prerendered).
 */
export function TokenPage({ children, width = "md" }: { children: React.ReactNode; width?: "sm" | "md" | "lg" }) {
  return (
    <PageShell width={width}>
      <Suspense
        fallback={
          <div className="space-y-4">
            <Skeleton className="h-16 w-2/3" />
            <Skeleton className="h-72" />
          </div>
        }
      >
        {children}
      </Suspense>
    </PageShell>
  );
}
