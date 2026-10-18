import type { MetadataRoute } from "next";
import { routing } from "@/i18n/routing";
import { PUBLIC_PAGES } from "@/lib/public-pages";
import { urlOf } from "@/lib/seo";

/** sitemap.xml: every public page per locale, with hreflang alternates (+ x-default). */
export default function sitemap(): MetadataRoute.Sitemap {
  return PUBLIC_PAGES.flatMap(({ path, priority, changeFrequency }) =>
    routing.locales.map((locale) => ({
      url: urlOf(locale, path),
      changeFrequency,
      priority,
      alternates: {
        languages: {
          ...Object.fromEntries(routing.locales.map((l) => [l, urlOf(l, path)])),
          "x-default": urlOf(routing.defaultLocale, path),
        },
      },
    })),
  );
}
