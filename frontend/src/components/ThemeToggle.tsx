"use client";

import { useSyncExternalStore } from "react";
import { useTranslations } from "next-intl";
import { useTheme } from "next-themes";

const noop = () => () => {};
/** true after hydration (false on the server and during the hydration render). */
const useHydrated = () => useSyncExternalStore(noop, () => true, () => false);

/**
 * Sun / moon toggle. Both icons are in the markup and CSS shows the right one
 * (`light:` variant), so the icon never flashes; only the aria-label waits for
 * hydration because the stored theme is unknown on the server.
 */
export function ThemeToggle({ className = "" }: { className?: string }) {
  const t = useTranslations("theme");
  const { resolvedTheme, setTheme } = useTheme();
  const hydrated = useHydrated();
  const isLight = hydrated && resolvedTheme === "light";
  const label = !hydrated ? t("toggle") : isLight ? t("toDark") : t("toLight");

  return (
    <button
      type="button"
      onClick={() => setTheme(isLight ? "dark" : "light")}
      aria-label={label}
      title={label}
      className={`flex h-9 w-9 items-center justify-center rounded-full border border-ink/20 text-ink/85 transition-colors hover:border-gold-text hover:text-gold-text focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-gold-text ${className}`}
    >
      {/* sun — shown in dark mode (switches to light) */}
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true" className="light:hidden">
        <circle cx="12" cy="12" r="4.2" stroke="currentColor" strokeWidth="1.6" />
        <path
          d="M12 2.5v2.2M12 19.3v2.2M2.5 12h2.2M19.3 12h2.2M5.3 5.3l1.6 1.6M17.1 17.1l1.6 1.6M5.3 18.7l1.6-1.6M17.1 6.9l1.6-1.6"
          stroke="currentColor"
          strokeWidth="1.6"
          strokeLinecap="round"
        />
      </svg>
      {/* moon — shown in light mode (switches to dark) */}
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true" className="hidden light:block">
        <path d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round" />
      </svg>
    </button>
  );
}
