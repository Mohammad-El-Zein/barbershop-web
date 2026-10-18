"use client";

import { useEffect, useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { siteConfig } from "@/config/site.config";
import { openBooking } from "@/lib/booking";
import { scrollToTarget } from "@/lib/scroll";
import { LanguageSwitcher } from "./LanguageSwitcher";
import { Logo } from "./Logo";
import { AccountLink } from "./AccountLink";
import { ThemeToggle } from "./ThemeToggle";

/**
 * Slim fixed top navigation with a mobile menu, theme toggle and language switcher.
 *
 * Readability over both backgrounds:
 * - over the cinematic scene (always dark): dark tokens (.scene-dark); in dark
 *   theme a soft gradient, in light theme a dark glass bar
 * - over page content (footer, later pages): a glass bar in the current theme
 * The switch happens when the first element marked [data-after-scene] reaches the nav.
 *
 * Horizontal padding is the shared page gutter (--gutter), so the logo's left
 * edge matches the scene text column in every chapter.
 */
/** Dispatched by the landing scene: detail = true while the nav is over the page after the scene. */
export const NAV_SURFACE_EVENT = "nav:over-page";

export function Nav({ hasScene = true }: { hasScene?: boolean }) {
  const t = useTranslations();
  const locale = useLocale();
  const [open, setOpen] = useState(false);
  const [overPage, setOverPage] = useState(!hasScene);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open]);

  // Landing page: the scene (which owns GSAP / ScrollTrigger) reports when the nav is over
  // the page after the scene — the nav itself carries no GSAP code (every page includes it).
  useEffect(() => {
    if (!hasScene) return;
    const onSurface = (e: Event) => setOverPage((e as CustomEvent<boolean>).detail);
    window.addEventListener(NAV_SURFACE_EVENT, onSurface);
    return () => window.removeEventListener(NAV_SURFACE_EVENT, onSurface);
  }, [hasScene]);

  // on app pages the "#anchor" links point back to the landing page
  const hrefOf = (href: string) => (hasScene ? href : `/${locale}${href === "#home" ? "" : href}`);
  const go = (e: React.MouseEvent, href: string) => {
    if (hasScene && scrollToTarget(href)) e.preventDefault();
    setOpen(false);
  };

  const overScene = !overPage;

  return (
    <header data-nav-surface={overScene ? "scene" : "page"} className={`fixed inset-x-0 top-[var(--demo-bar)] z-50 text-ink ${overScene ? "scene-dark" : ""}`}>
      <a
        href="#main"
        className="sr-only-focusable absolute left-4 top-3 z-10 rounded bg-gold px-3 py-2 text-sm text-black"
      >
        {t("nav.skip")}
      </a>

      {/* backgrounds (cross-faded) */}
      <div
        aria-hidden="true"
        className={`pointer-events-none absolute inset-x-0 top-0 transition-opacity duration-300 ${overScene ? "opacity-100" : "opacity-0"}`}
      >
        {/* dark theme: soft gradient — light theme: dark glass bar */}
        <div className="h-28 bg-gradient-to-b from-black/85 via-black/40 to-transparent light:h-[var(--nav-h)] light:border-b light:border-white/10 light:bg-[rgb(10_10_10/0.72)] light:bg-none light:backdrop-blur-md" />
      </div>
      <div
        aria-hidden="true"
        className={`pointer-events-none absolute inset-x-0 top-0 h-[var(--nav-h)] border-b border-line bg-[color-mix(in_srgb,var(--bg)_86%,transparent)] backdrop-blur-md transition-opacity duration-300 ${
          overScene ? "opacity-0" : "opacity-100"
        }`}
      />

      <nav
        aria-label={t("nav.label")}
        className="relative flex h-[var(--nav-h)] items-center justify-between gap-4 px-[var(--gutter)]"
      >
        <a
          href={hrefOf("#home")}
          data-nav-logo
          onClick={(e) => go(e, "#home")}
          aria-label={t("nav.homeLink", { brand: siteConfig.brand.name })}
          className="min-w-0"
        >
          <Logo />
        </a>

        <div className="flex items-center gap-3 lg:gap-8">
          <ul className="hidden items-center gap-6 lg:flex xl:gap-8">
            {siteConfig.nav.map((item) => (
              <li key={item.href}>
                <a
                  href={hrefOf(item.href)}
                  onClick={(e) => go(e, item.href)}
                  className="text-[0.72rem] font-medium uppercase tracking-[0.24em] text-ink/75 transition-colors hover:text-gold-text focus-visible:text-gold-text focus-visible:outline-none"
                >
                  {t(item.labelKey)}
                </a>
              </li>
            ))}
          </ul>
          <div className="flex items-center gap-2 sm:gap-3">
            {/* phones: the language switcher lives in the mobile menu (bar width) */}
            <LanguageSwitcher className="hidden sm:flex" />
            <ThemeToggle />
            <AccountLink />
          </div>
          <button type="button" className="btn btn-outline-gold hidden md:inline-flex" onClick={() => openBooking("nav")}>
            {t(siteConfig.bookCta.labelKey)}
          </button>
          <button
            type="button"
            className="relative -mr-2 flex h-10 w-10 shrink-0 items-center justify-center lg:hidden"
            aria-expanded={open}
            aria-controls="mobile-menu"
            aria-label={open ? t("nav.menuClose") : t("nav.menuOpen")}
            onClick={() => setOpen((o) => !o)}
          >
            <span className={`absolute h-px w-6 bg-ink transition-transform ${open ? "rotate-45" : "-translate-y-1.5"}`} />
            <span className={`absolute h-px w-6 bg-ink transition-opacity ${open ? "opacity-0" : ""}`} />
            <span className={`absolute h-px w-6 bg-ink transition-transform ${open ? "-rotate-45" : "translate-y-1.5"}`} />
          </button>
        </div>
      </nav>

      <div
        id="mobile-menu"
        hidden={!open}
        className="relative border-t border-line bg-[color-mix(in_srgb,var(--bg)_96%,transparent)] px-[var(--gutter)] pb-8 pt-4 backdrop-blur lg:hidden"
      >
        <LanguageSwitcher className="mb-2 flex sm:hidden" />
        <ul className="flex flex-col">
          {siteConfig.nav.map((item) => (
            <li key={item.href}>
              <a
                href={hrefOf(item.href)}
                onClick={(e) => go(e, item.href)}
                className="display block py-3 text-3xl text-ink hover:text-gold-text"
              >
                {t(item.labelKey)}
              </a>
            </li>
          ))}
        </ul>
        <button
          type="button"
          className="btn btn-primary mt-6 w-full"
          onClick={() => {
            setOpen(false);
            openBooking("nav-mobile");
          }}
        >
          {t(siteConfig.bookCta.labelKey)}
        </button>
      </div>
    </header>
  );
}
