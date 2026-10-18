import type { Metadata, Viewport } from "next";
import { Anton, Inter } from "next/font/google";
import { notFound } from "next/navigation";
import { hasLocale, NextIntlClientProvider } from "next-intl";
import { getMessages, getTranslations } from "next-intl/server";
import { BookingDialog } from "@/components/booking/BookingDialog";
import { ConsentManager } from "@/components/consent/ConsentManager";
import { ThemeProvider } from "@/components/ThemeProvider";
import { siteConfig } from "@/config/site.config";
import { themeCss } from "@/config/theme-css";
import { CONSENT_BOOT_SCRIPT } from "@/lib/consent-shared";
import { routing } from "@/i18n/routing";
import { AuthProvider } from "@/lib/auth/AuthProvider";
import "../globals.css";

// Condensed display face for headlines, Inter for body copy.
const anton = Anton({ variable: "--font-anton", weight: "400", subsets: ["latin"], display: "swap" });
const inter = Inter({ variable: "--font-inter", subsets: ["latin"], display: "swap" }); // latin covers ä ö ü ß

/** /de and /en are prerendered (required with cacheComponents for root params). */
export function generateStaticParams() {
  return routing.locales.map((locale) => ({ locale }));
}

/**
 * Site-wide defaults only. Canonical URL, hreflang and per-page Open Graph data are
 * set by each page via lib/seo.ts → pageMetadata (a canonical here would make every
 * page point at the home page).
 */
export async function generateMetadata({ params }: LayoutProps<"/[locale]">): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "meta" });
  return {
    metadataBase: new URL(siteConfig.siteUrl),
    title: { default: t("title"), template: `%s | ${siteConfig.brand.name}` },
    description: t("description"),
    applicationName: siteConfig.brand.name,
    formatDetection: { telephone: false },
    openGraph: { type: "website", siteName: siteConfig.brand.name, title: t("title"), description: t("description"), locale: t("ogLocale") },
    twitter: { card: "summary_large_image", title: t("title"), description: t("description") },
  };
}

export const viewport: Viewport = {
  themeColor: [
    { media: "(prefers-color-scheme: dark)", color: siteConfig.theme.dark.bg },
    { media: "(prefers-color-scheme: light)", color: siteConfig.theme.light.bg },
  ],
};

export default async function LocaleLayout({ children, params }: LayoutProps<"/[locale]">) {
  const { locale } = await params;
  if (!hasLocale(routing.locales, locale)) notFound();
  const messages = await getMessages();

  return (
    // suppressHydrationWarning: next-themes sets the theme class on <html> before React hydrates
    <html lang={locale} className={`${anton.variable} ${inter.variable}`} suppressHydrationWarning>
      <head>
        {/* colour tokens for both themes, generated from site.config.ts */}
        <style dangerouslySetInnerHTML={{ __html: themeCss() }} />
        {/* first visit: show the server-rendered consent banner before the first paint */}
        <script dangerouslySetInnerHTML={{ __html: CONSENT_BOOT_SCRIPT }} />
      </head>
      <body>
        <ThemeProvider>
          <NextIntlClientProvider locale={locale} messages={messages}>
            <AuthProvider>
              {/* first in the tab order; renders only while no decision exists (or when re-opened) */}
              <ConsentManager />
              {children}
              {/* opened by every "Book" button (lib/booking.ts → openBooking) */}
              <BookingDialog />
            </AuthProvider>
          </NextIntlClientProvider>
        </ThemeProvider>
        <div className="grain" aria-hidden="true" />
      </body>
    </html>
  );
}
