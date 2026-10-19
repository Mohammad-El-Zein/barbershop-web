import type { Metadata } from "next";
import { anton, inter } from "./fonts";
import Link from "next/link";
import { siteConfig } from "@/config/site.config";
import { themeCss } from "@/config/theme-css";
import de from "../../messages/app.de.json";
import en from "../../messages/app.en.json";
import "./globals.css";

/**
 * 404 for every URL that matches no route (experimental.globalNotFound): unknown paths below
 * /de and /en, unknown campaigns (the proxy rewrites /…/lp/<unknown> here) and unknown
 * locales. Next.js returns it at the routing level — no layout, no streaming — so the response
 * is a real 404 with the complete page in the HTML (with cacheComponents a notFound() thrown
 * while rendering a page would stream an empty shell). There is no locale context here, so the
 * page is bilingual. Noindex is added by Next.js.
 */

const brand = siteConfig.brand.name;
const T = { de: de.errorPages, en: en.errorPages };

export const metadata: Metadata = {
  title: `${T.de.notFound.title} · ${T.en.notFound.title} | ${brand}`,
  description: T.en.notFound.text,
};

// same as next-themes (ThemeProvider): stored choice, else the OS preference; default dark
const THEME_SCRIPT = `try{var t=localStorage.getItem("theme");if(t==="light"||(t!=="dark"&&matchMedia("(prefers-color-scheme: light)").matches))document.documentElement.classList.add("light")}catch(e){}`;

export default function GlobalNotFound() {
  return (
    <html lang="de" className={`${anton.variable} ${inter.variable}`} suppressHydrationWarning>
      <head>
        <style dangerouslySetInnerHTML={{ __html: themeCss() }} />
        <script dangerouslySetInnerHTML={{ __html: THEME_SCRIPT }} />
      </head>
      <body>
        <header className="mx-auto flex h-[var(--nav-h)] w-full max-w-5xl items-center px-4 sm:px-6">
          <Link href="/de" className="display text-xl tracking-wide text-ink">
            {brand}
          </Link>
        </header>
        <main id="main" className="mx-auto w-full max-w-3xl px-4 pb-20 pt-8 text-center sm:px-6 md:pt-14">
          <p className="display text-[clamp(5rem,22vw,11rem)] leading-none text-gold-text" aria-hidden="true">
            404
          </p>
          <h1 className="display mt-4 text-4xl sm:text-5xl">{T.de.notFound.title}</h1>
          <p className="mx-auto mt-4 max-w-md text-muted">{T.de.notFound.text}</p>
          <div className="mt-8 flex flex-wrap justify-center gap-3">
            <Link href="/de" className="btn btn-primary">
              {T.de.home}
            </Link>
            <Link href="/de/booking" className="btn btn-ghost">
              {T.de.book}
            </Link>
          </div>

          <section lang="en" aria-labelledby="nf-en" className="mx-auto mt-14 max-w-md border-t border-line pt-8">
            <h2 id="nf-en" className="display text-2xl">
              {T.en.notFound.title}
            </h2>
            <p className="mt-3 text-sm text-muted">{T.en.notFound.text}</p>
            <div className="mt-6 flex flex-wrap justify-center gap-3">
              <Link href="/en" className="btn btn-ghost">
                {T.en.home}
              </Link>
              <Link href="/en/booking" className="btn btn-ghost">
                {T.en.book}
              </Link>
            </div>
          </section>
        </main>
      </body>
    </html>
  );
}
