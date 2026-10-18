import { NextResponse, type NextRequest } from "next/server";
import createMiddleware from "next-intl/middleware";
import { landingPage } from "./config/landing-pages";
import { routing } from "./i18n/routing";

/**
 * Next 16 "proxy" (formerly middleware): redirects "/" and unprefixed paths to
 * /de or /en (cookie → Accept-Language → default de) and remembers the choice.
 *
 * Unknown ad campaigns (/de/lp/<slug> not in config/landing-pages.ts) are rewritten to a
 * path no route matches, so they get the global 404 with a real 404 status
 * (app/global-not-found.tsx). Rendered by the [campaign] page, notFound() would fire
 * mid-stream with cacheComponents and answer 200 ("soft 404").
 */
const intl = createMiddleware(routing);
const LANDING_PAGE = /^\/(de|en)\/lp\/([^/]+)\/?$/;

export default function proxy(request: NextRequest) {
  const lp = request.nextUrl.pathname.match(LANDING_PAGE);
  if (lp && !landingPage(decodeURIComponent(lp[2]))) {
    const url = request.nextUrl.clone();
    url.pathname = "/_404/unknown-campaign"; // two segments: matches no route at all
    return NextResponse.rewrite(url);
  }
  return intl(request);
}

export const config = {
  // Everything except API routes, Next internals and files with an extension.
  matcher: "/((?!api|trpc|_next|_vercel|.*\\..*).*)",
};
