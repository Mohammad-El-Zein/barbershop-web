"use client";

import { useEffect, useState } from "react";
import { siteConfig } from "@/config/site.config";
import { openBooking } from "@/lib/booking";
import { scrollToTarget } from "@/lib/scroll";
import { Logo } from "./Logo";

/** Slim fixed top navigation with a mobile menu. */
export function Nav() {
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open]);

  const go = (e: React.MouseEvent, href: string) => {
    if (scrollToTarget(href)) e.preventDefault();
    setOpen(false);
  };

  return (
    <header className="fixed inset-x-0 top-0 z-50">
      <a
        href="#main"
        className="sr-only-focusable absolute left-4 top-3 z-10 rounded bg-gold px-3 py-2 text-sm text-black"
      >
        Skip to content
      </a>
      {/* soft top gradient so links stay readable over the stage */}
      <div className="pointer-events-none absolute inset-0 h-28 bg-gradient-to-b from-black/80 via-black/30 to-transparent" />
      <nav
        aria-label="Main"
        className="relative mx-auto flex h-[72px] max-w-[1600px] items-center justify-between px-5 md:px-10"
      >
        <a href="#home" onClick={(e) => go(e, "#home")} aria-label={`${siteConfig.brand.name}, home`}>
          <Logo />
        </a>

        <div className="hidden items-center gap-10 md:flex">
          <ul className="flex items-center gap-8">
            {siteConfig.nav.map((item) => (
              <li key={item.href}>
                <a
                  href={item.href}
                  onClick={(e) => go(e, item.href)}
                  className="text-[0.72rem] font-medium uppercase tracking-[0.24em] text-ink/75 transition-colors hover:text-gold focus-visible:text-gold focus-visible:outline-none"
                >
                  {item.label}
                </a>
              </li>
            ))}
          </ul>
          <button type="button" className="btn btn-outline-gold" onClick={() => openBooking("nav")}>
            {siteConfig.bookCta.label}
          </button>
        </div>

        <button
          type="button"
          className="relative flex h-10 w-10 items-center justify-center md:hidden"
          aria-expanded={open}
          aria-controls="mobile-menu"
          aria-label={open ? "Close menu" : "Open menu"}
          onClick={() => setOpen((o) => !o)}
        >
          <span className={`absolute h-px w-6 bg-ink transition-transform ${open ? "rotate-45" : "-translate-y-1.5"}`} />
          <span className={`absolute h-px w-6 bg-ink transition-opacity ${open ? "opacity-0" : ""}`} />
          <span className={`absolute h-px w-6 bg-ink transition-transform ${open ? "-rotate-45" : "translate-y-1.5"}`} />
        </button>
      </nav>

      <div
        id="mobile-menu"
        hidden={!open}
        className="relative border-t border-white/10 bg-black/95 px-5 pb-8 pt-4 backdrop-blur md:hidden"
      >
        <ul className="flex flex-col">
          {siteConfig.nav.map((item) => (
            <li key={item.href}>
              <a
                href={item.href}
                onClick={(e) => go(e, item.href)}
                className="display block py-3 text-3xl text-ink hover:text-gold"
              >
                {item.label}
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
          {siteConfig.bookCta.label}
        </button>
      </div>
    </header>
  );
}
