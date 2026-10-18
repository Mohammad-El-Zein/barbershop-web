import type { DayOfWeek, HairSalon, Offer, OpeningHoursSpecification, WithContext } from "schema-dts";
import { siteConfig } from "@/config/site.config";
import type { ShopData } from "./shop-server";
import { SITE_URL, urlOf } from "./seo";

/**
 * schema.org structured data for the shop: a HairSalon (a LocalBusiness subtype)
 * with address, contact, opening hours (derived from the barbers' working hours)
 * and one Offer per bookable service. Built from the backend's shop data; fields
 * the backend doesn't have (or can't deliver) fall back to site.config.
 * Typed with schema-dts, so invalid properties fail the type-check.
 */
const DAYS: DayOfWeek[] = [
  "https://schema.org/Monday",
  "https://schema.org/Tuesday",
  "https://schema.org/Wednesday",
  "https://schema.org/Thursday",
  "https://schema.org/Friday",
  "https://schema.org/Saturday",
  "https://schema.org/Sunday",
] as DayOfWeek[];

export function shopJsonLd({ shop, services }: ShopData, locale: string): WithContext<HairSalon> {
  const p = shop?.profile ?? {};
  const fallback = siteConfig.contact;
  const currency = shop?.currency ?? "EUR";

  const openingHours: OpeningHoursSpecification[] = (shop?.openingHours ?? []).map((r) => ({
    "@type": "OpeningHoursSpecification",
    dayOfWeek: DAYS[(r.weekday ?? 1) - 1],
    opens: r.opens,
    closes: r.closes,
  }));

  const offers: Offer[] = services.map((s) => {
    const amount = (s.price?.amountCents ?? 0) / 100;
    return {
      "@type": "Offer",
      itemOffered: { "@type": "Service", name: s.name, description: s.description ?? undefined },
      // "from" prices are a minimum, exact prices are the price
      priceSpecification: s.price?.from
        ? { "@type": "PriceSpecification", minPrice: amount, priceCurrency: currency }
        : { "@type": "PriceSpecification", price: amount, priceCurrency: currency },
      url: `${urlOf(locale, "/booking")}?services=${s.id}`,
      availability: "https://schema.org/InStock",
    };
  });

  return {
    "@context": "https://schema.org",
    "@type": "HairSalon",
    "@id": `${SITE_URL}/#business`,
    name: shop?.name ?? siteConfig.brand.name,
    url: urlOf(locale),
    image: `${urlOf(locale)}/opengraph-image`,
    telephone: p.phone ?? fallback.phone,
    email: p.email ?? fallback.email,
    priceRange: p.priceRange ?? fallback.priceRange,
    currenciesAccepted: currency,
    address: {
      "@type": "PostalAddress",
      streetAddress: p.street ?? fallback.address.street,
      postalCode: p.postalCode ?? fallback.address.postalCode,
      addressLocality: p.city ?? fallback.address.city,
      addressCountry: p.country ?? fallback.address.country,
    },
    ...(openingHours.length ? { openingHoursSpecification: openingHours } : {}),
    ...(offers.length ? { makesOffer: offers } : {}),
    potentialAction: {
      "@type": "ReserveAction",
      target: {
        "@type": "EntryPoint",
        urlTemplate: urlOf(locale, "/booking"),
        actionPlatform: ["https://schema.org/DesktopWebPlatform", "https://schema.org/MobileWebPlatform"],
      },
      result: { "@type": "Reservation", name: locale === "de" ? "Termin" : "Appointment" },
    },
  };
}

/** Serialises JSON-LD for a <script> tag; "<" is escaped so the payload can't close the tag. */
export function jsonLdString(data: object) {
  return JSON.stringify(data).replace(/</g, "\\u003c");
}
