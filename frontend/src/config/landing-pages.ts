/**
 * AD LANDING PAGES: /[locale]/lp/[slug]. One entry per campaign; the page is built
 * from this config (no code changes for a new campaign). Link ads to e.g.
 *   https://<site>/de/lp/herbst-fade?utm_source=google&utm_medium=cpc&utm_campaign=herbst-fade
 * The visit is attributed to the campaign (with marketing consent) and shows up in
 * the admin campaign stats; without UTM parameters it still counts as "lp:<slug>".
 *
 * Copy here is PLACEHOLDER marketing text — adapt per client/campaign.
 */
export type LandingCopy = {
  eyebrow: string;
  headline: string;
  subline: string;
  cta: string;
  benefits: [Benefit, Benefit, Benefit];
  servicesTitle: string;
  reviewsTitle: string;
  finalTitle: string;
  /** Page title / meta description (also used for social previews). */
  metaTitle: string;
  metaDescription: string;
};
type Benefit = { title: string; text: string };

export type LandingPage = {
  slug: string;
  /** Ad landing pages are noindex by default (duplicate content, campaign-only). */
  index?: boolean;
  hero: { src: string; width: number; height: number; alt: Record<"de" | "en", string> };
  /** Services preselected by the main CTA (empty = choose in the flow). */
  preselect: string[];
  /** Services listed with prices (empty = all bookable services). */
  services: string[];
  copy: Record<"de" | "en", LandingCopy>;
};

const HAIRCUT = "7b3f1c2e-0000-4000-8000-000000000101"; // PLACEHOLDER: demo shop ids
const BEARD = "7b3f1c2e-0000-4000-8000-000000000102";

export const landingPages: LandingPage[] = [
  {
    slug: "herbst-fade",
    hero: {
      src: "/scene/service-haircut.webp",
      width: 1280,
      height: 800,
      alt: { de: "Barber beim präzisen Fade-Haarschnitt", en: "Barber cutting a precise fade" },
    },
    preselect: [HAIRCUT],
    services: [],
    copy: {
      de: {
        eyebrow: "Barbershop Downtown",
        headline: "Der perfekte Fade. Heute noch gebucht.",
        subline: "Präzisionsschnitt vom Barber deiner Wahl – freie Termine live sehen und in unter einer Minute buchen.",
        cta: "Freien Termin finden",
        benefits: [
          { title: "Live-Verfügbarkeit", text: "Alle Barber nebeneinander, freie Zeiten sofort sichtbar." },
          { title: "Fairer Ablauf", text: "Dein Barber bestätigt – erst dann zahlst du 10 € Anzahlung, die verrechnet wird." },
          { title: "Flexibel", text: "Kostenlos stornieren bis 24 Stunden vorher, online bis 2 Stunden vorher." },
        ],
        servicesTitle: "Leistungen & Preise",
        reviewsTitle: "Das sagen unsere Kunden",
        finalTitle: "Bereit für deinen neuen Schnitt?",
        metaTitle: "Fade-Haarschnitt online buchen",
        metaDescription: "Präziser Fade vom Barber deiner Wahl. Freie Termine live sehen und in unter einer Minute buchen.",
      },
      en: {
        eyebrow: "Barbershop Downtown",
        headline: "The perfect fade. Booked today.",
        subline: "A precision cut from the barber of your choice – see free slots live and book in under a minute.",
        cta: "Find a free slot",
        benefits: [
          { title: "Live availability", text: "All barbers side by side, free times visible instantly." },
          { title: "Fair process", text: "Your barber confirms – only then you pay a €10 deposit that is deducted." },
          { title: "Flexible", text: "Cancel free up to 24 hours before, online up to 2 hours before." },
        ],
        servicesTitle: "Services & prices",
        reviewsTitle: "What our customers say",
        finalTitle: "Ready for your new cut?",
        metaTitle: "Book a fade haircut online",
        metaDescription: "A precise fade from the barber of your choice. See free slots live and book in under a minute.",
      },
    },
  },
  {
    slug: "bart-special",
    hero: {
      src: "/scene/service-beard.webp",
      width: 1280,
      height: 800,
      alt: { de: "Bart-Styling mit Rasiermesser", en: "Beard sculpting with a straight razor" },
    },
    preselect: [BEARD],
    services: [BEARD, HAIRCUT],
    copy: {
      de: {
        eyebrow: "Bart-Styling",
        headline: "Konturen, die sitzen.",
        subline: "Line-up, heißes Tuch und Rasiermesser-Finish – beim Bart-Experten deiner Wahl.",
        cta: "Bart-Termin buchen",
        benefits: [
          { title: "Rasiermesser-Finish", text: "Saubere Linien mit klassischer Technik." },
          { title: "In 30 Minuten", text: "Kurzer Termin, auch in der Mittagspause." },
          { title: "Ohne Risiko", text: "Anzahlung erst nach Zusage, kostenlos stornierbar bis 24 Stunden vorher." },
        ],
        servicesTitle: "Leistungen & Preise",
        reviewsTitle: "Das sagen unsere Kunden",
        finalTitle: "Zeit für einen frischen Bart?",
        metaTitle: "Bart-Styling online buchen",
        metaDescription: "Line-up, heißes Tuch und Rasiermesser-Finish. Bart-Termin in unter einer Minute online buchen.",
      },
      en: {
        eyebrow: "Beard sculpting",
        headline: "Lines that stay sharp.",
        subline: "Line-up, hot towel and straight-razor finish – with the beard expert of your choice.",
        cta: "Book a beard appointment",
        benefits: [
          { title: "Straight-razor finish", text: "Clean lines with classic technique." },
          { title: "In 30 minutes", text: "A short appointment, even in your lunch break." },
          { title: "No risk", text: "Deposit only after acceptance, cancel free up to 24 hours before." },
        ],
        servicesTitle: "Services & prices",
        reviewsTitle: "What our customers say",
        finalTitle: "Time for a fresh beard?",
        metaTitle: "Book beard sculpting online",
        metaDescription: "Line-up, hot towel and straight-razor finish. Book a beard appointment online in under a minute.",
      },
    },
  },
];

export function landingPage(slug: string) {
  return landingPages.find((p) => p.slug === slug);
}
