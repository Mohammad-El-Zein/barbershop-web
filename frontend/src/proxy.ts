import createMiddleware from "next-intl/middleware";
import { routing } from "./i18n/routing";

/**
 * Next 16 "proxy" (formerly middleware): redirects "/" and unprefixed paths to
 * /de or /en (cookie → Accept-Language → default de) and remembers the choice.
 */
export default createMiddleware(routing);

export const config = {
  // Everything except API routes, Next internals and files with an extension.
  matcher: "/((?!api|trpc|_next|_vercel|.*\\..*).*)",
};
