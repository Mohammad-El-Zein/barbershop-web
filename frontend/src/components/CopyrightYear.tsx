import { cacheLife } from "next/cache";

/**
 * Current year for the footer. Next 16 prerendering forbids a bare `new Date()`
 * in components, so the value is cached and refreshed daily.
 */
export async function CopyrightYear() {
  "use cache";
  cacheLife("days");
  return <>{new Date().getFullYear()}</>;
}
