"use client";

import { ThemeProvider as NextThemesProvider } from "next-themes";

/**
 * Light / dark theme via next-themes.
 * - first visit: follows the OS preference (prefers-color-scheme)
 * - the user's toggle choice is remembered in localStorage ("theme")
 * - the class ("light" / "dark") is set on <html> by an inline script before
 *   first paint, so there is no flash; without JS the CSS default is dark
 */
export function ThemeProvider({ children }: { children: React.ReactNode }) {
  return (
    <NextThemesProvider
      attribute="class"
      defaultTheme="system"
      enableSystem
      themes={["light", "dark"]}
      storageKey="theme"
      disableTransitionOnChange
    >
      {children}
    </NextThemesProvider>
  );
}
