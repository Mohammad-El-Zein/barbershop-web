"use client";

import { useEffect, useId, useRef, useState } from "react";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { useAuth, useLogout } from "@/lib/auth/AuthProvider";

const RING =
  "focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-gold-text";
const PERSON = (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
    <circle cx="12" cy="8" r="4" stroke="currentColor" strokeWidth="1.6" />
    <path d="M4 21c1.5-4 4.5-6 8-6s6.5 2 8 6" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
  </svg>
);

/**
 * Person icon in the nav. Anonymous: a link to the login. Logged in: the initials open a small
 * menu with name, email and role, "My account" and "Log out" — never a jump into the staff
 * area (that one is only reached at /admin directly).
 *
 * "My account": customers → /account (bookings, loyalty); barbers and admins → /account/profile
 * (the customer page is for customer accounts).
 *
 * Keyboard (WAI-ARIA menu button): Enter / Space / ↓ open it on the first item, ↑ on the last;
 * ↑ ↓ Home End move, Escape closes and returns to the button, Tab closes. A click or tap
 * outside closes it.
 */
export function AccountMenu() {
  const t = useTranslations("nav");
  const tRole = useTranslations("account.roles");
  const { status, user } = useAuth();
  const logout = useLogout("/");
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLUListElement>(null);
  const focusOnOpen = useRef<"first" | "last">("first");
  const id = useId();

  const items = () => [...(menuRef.current?.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? [])];
  const close = (refocus: boolean) => {
    setOpen(false);
    if (refocus) buttonRef.current?.focus();
  };

  useEffect(() => {
    if (!open) return;
    const list = items();
    (focusOnOpen.current === "last" ? list[list.length - 1] : list[0])?.focus();
    const outside = (e: PointerEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("pointerdown", outside);
    return () => document.removeEventListener("pointerdown", outside);
  }, [open]);

  // logged out or switched (here or in another tab): the menu goes with the session
  const [seenUser, setSeenUser] = useState(user?.id);
  if (seenUser !== user?.id) {
    setSeenUser(user?.id);
    setOpen(false);
  }

  if (status !== "authenticated" || !user) {
    return (
      <Link
        href="/account/login"
        aria-label={t("login")}
        title={t("login")}
        className={`flex h-9 w-9 items-center justify-center rounded-full border border-ink/20 text-ink/85 transition-colors hover:border-gold-text hover:text-gold-text ${RING}`}
      >
        {PERSON}
      </Link>
    );
  }

  const staff = user.role === "BARBER" || user.role === "ADMIN";
  const initials = user.name
    ?.split(/\s+/)
    .map((p) => p[0])
    .join("")
    .slice(0, 2)
    .toUpperCase();
  const label = t("accountMenu", { name: user.name ?? user.email ?? "" });

  const onButtonKey = (e: React.KeyboardEvent) => {
    if (e.key === "ArrowDown" || e.key === "ArrowUp") {
      e.preventDefault();
      focusOnOpen.current = e.key === "ArrowUp" ? "last" : "first";
      setOpen(true);
    }
  };
  const onMenuKey = (e: React.KeyboardEvent) => {
    const list = items();
    const i = list.indexOf(document.activeElement as HTMLElement);
    const move = (to: number) => {
      e.preventDefault();
      list[(to + list.length) % list.length]?.focus();
    };
    if (e.key === "ArrowDown") move(i + 1);
    else if (e.key === "ArrowUp") move(i - 1);
    else if (e.key === "Home") move(0);
    else if (e.key === "End") move(list.length - 1);
    else if (e.key === "Escape") {
      e.preventDefault();
      close(true);
    } else if (e.key === "Tab") close(false);
  };

  const itemClass = `block w-full rounded-lg px-3 py-2.5 text-left text-sm text-ink hover:bg-ink/5 focus:bg-ink/5 focus:outline-none ${RING}`;

  return (
    <div ref={rootRef} className="relative" data-account-menu>
      <button
        ref={buttonRef}
        type="button"
        aria-label={label}
        title={label}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={open ? `${id}-menu` : undefined}
        onClick={() => {
          focusOnOpen.current = "first";
          setOpen((o) => !o);
        }}
        onKeyDown={onButtonKey}
        className={`flex h-9 w-9 items-center justify-center rounded-full border text-[0.65rem] font-semibold transition-colors hover:border-gold-text hover:text-gold-text ${
          open ? "border-gold-text text-gold-text" : "border-ink/20 text-ink/85"
        } ${RING}`}
      >
        {initials ? <span aria-hidden="true">{initials}</span> : PERSON}
      </button>

      {open && (
        <div data-account-popup className="absolute right-0 top-[calc(100%+0.5rem)] z-50 w-[min(17rem,calc(100vw-2rem))] rounded-2xl border border-line bg-surface p-2 text-ink shadow-[0_20px_50px_-20px_rgb(0_0_0/0.6)]">
          <div className="border-b border-line px-3 pb-3 pt-2">
            <p id={`${id}-name`} className="truncate font-semibold">
              {user.name}
            </p>
            <p className="truncate text-xs text-muted">{user.email}</p>
            <p className="mt-1 text-[0.65rem] font-semibold uppercase tracking-[0.14em] text-gold-text">
              {tRole(user.role ?? "CUSTOMER")}
            </p>
          </div>
          <ul ref={menuRef} id={`${id}-menu`} role="menu" aria-labelledby={`${id}-name`} onKeyDown={onMenuKey} className="pt-2">
            <li role="none">
              <Link
                href={staff ? "/account/profile" : "/account"}
                role="menuitem"
                tabIndex={-1}
                onClick={() => close(false)}
                className={itemClass}
              >
                {t("myAccount")}
              </Link>
            </li>
            <li role="none">
              <button
                type="button"
                role="menuitem"
                tabIndex={-1}
                disabled={busy}
                aria-busy={busy}
                onClick={async () => {
                  setBusy(true);
                  try {
                    await logout();
                  } finally {
                    setBusy(false);
                  }
                }}
                className={itemClass}
              >
                {busy ? t("loggingOut") : t("logout")}
              </button>
            </li>
          </ul>
        </div>
      )}
    </div>
  );
}
