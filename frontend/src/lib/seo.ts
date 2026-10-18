import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { siteConfig } from "@/config/site.config";
import { routing } from "@/i18n/routing";

/**
 * Per-page metadata: title, description, canonical URL, hreflang alternates and
 * Open Graph / Twitter cards. All URLs are built from NEXT_PUBLIC_SITE_URL
 * (siteConfig.siteUrl → metadataBase in the locale layout), so relative paths here
 * become absolute in the HTML. The OG/Twitter image comes from the generated
 * app/[locale]/opengraph-image (file convention, added by Next.js automatically).
 *
 * Private pages (account, admin, email-token links) use `noindex: true`: they get
 * robots noindex/nofollow and no canonical / hreflang, and are excluded from the
 * sitemap and disallowed in robots.txt.
 */
export const SITE_URL = siteConfig.siteUrl.replace(/\/$/, "");

/** Absolute URL of a page, e.g. urlOf("de", "/booking") → https://…/de/booking. */
export function urlOf(locale: string, path = "") {
  return `${SITE_URL}/${locale}${path}`;
}

type PageMeta = {
  locale: string;
  /** Path after the locale, "" for the home page, e.g. "/booking". */
  path: string;
  /** Page title (the layout's template adds " | BRAND"); omitted → the site default title. */
  title?: string;
  description?: string;
  noindex?: boolean;
};

export async function pageMetadata({ locale, path, title, description, noindex }: PageMeta): Promise<Metadata> {
  const t = await getTranslations({ locale, namespace: "meta" });
  const desc = description ?? t("description");
  // an explicit `title: undefined` would remove the layout's default title
  const titled = title ? { title } : {};
  if (noindex) {
    return {
      ...titled,
      description: desc,
      robots: { index: false, follow: false, googleBot: { index: false, follow: false } },
    };
  }
  const languages = Object.fromEntries(routing.locales.map((l) => [l, `/${l}${path}`]));
  const shareTitle = title ? `${title} | ${siteConfig.brand.name}` : t("title");
  // a page-level openGraph object replaces the file-based image of app/[locale]/opengraph-image → name it here
  const image = { url: `/${locale}/opengraph-image`, width: 1200, height: 630, alt: siteConfig.brand.name };
  return {
    ...titled,
    description: desc,
    alternates: {
      canonical: `/${locale}${path}`,
      languages: { ...languages, "x-default": `/${routing.defaultLocale}${path}` },
    },
    openGraph: {
      type: "website",
      siteName: siteConfig.brand.name,
      title: shareTitle,
      description: desc,
      url: `/${locale}${path}`,
      locale: t("ogLocale"),
      alternateLocale: routing.locales.filter((l) => l !== locale).map((l) => (l === "de" ? "de_DE" : "en_US")),
      images: [image],
    },
    twitter: { card: "summary_large_image", title: shareTitle, description: desc, images: [image.url.replace("opengraph", "twitter")] },
  };
}

/** Account, admin and email-token pages: never indexed or followed, no canonical / hreflang. */
export const PRIVATE_PAGE: Metadata = {
  robots: { index: false, follow: false, googleBot: { index: false, follow: false } },
};
