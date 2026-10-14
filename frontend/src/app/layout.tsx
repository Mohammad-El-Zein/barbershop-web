import type { Metadata, Viewport } from "next";
import { Anton, Inter } from "next/font/google";
import { SmoothScroll } from "@/components/SmoothScroll";
import { siteConfig } from "@/config/site.config";
import "./globals.css";

// Condensed display face for headlines, Inter for body copy.
const anton = Anton({ variable: "--font-anton", weight: "400", subsets: ["latin"], display: "swap" });
const inter = Inter({ variable: "--font-inter", subsets: ["latin"], display: "swap" });

export const metadata: Metadata = {
  title: `${siteConfig.brand.name} — ${siteConfig.brand.tagline}`,
  description: siteConfig.brand.description,
};

export const viewport: Viewport = {
  themeColor: siteConfig.theme.background,
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  const { theme } = siteConfig;
  // Brand colors from the config override the CSS defaults in globals.css.
  const themeVars = {
    "--bg": theme.background,
    "--gold": theme.gold,
    "--gold-deep": theme.goldDeep,
    "--text": theme.text,
  } as React.CSSProperties;

  return (
    <html lang={siteConfig.brand.locale} className={`${anton.variable} ${inter.variable}`} style={themeVars}>
      <body>
        <SmoothScroll />
        {children}
        <div className="grain" aria-hidden="true" />
      </body>
    </html>
  );
}
