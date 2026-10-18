"use client";

import { useEffect } from "react";
import { Nav } from "@/components/Nav";
import { ErrorView } from "@/components/errors/ErrorView";

/** Unexpected error inside a page: designed fallback with "try again" (no personal data is shown or logged). */
export default function Error({ error, retry }: { error: Error & { digest?: string }; retry: () => void }) {
  useEffect(() => {
    // only the digest: it matches the server log line without exposing details in the browser
    console.error("page error", error.digest ?? "");
  }, [error]);
  return (
    <>
      <Nav hasScene={false} />
      <main id="main" className="mx-auto w-full max-w-3xl px-4 pb-20 pt-[calc(var(--nav-h)+3rem)] sm:px-6">
        <ErrorView kind="error" onRetry={retry} digest={error.digest} />
      </main>
    </>
  );
}
