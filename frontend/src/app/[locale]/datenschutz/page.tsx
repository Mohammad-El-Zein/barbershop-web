import { LegalPage } from "@/components/legal/Legal";
import { PrivacyDe, PrivacyEn } from "@/content/legal/Privacy";
import { pageMetadata } from "@/lib/seo";

const TITLE = { de: "Datenschutzerklärung", en: "Privacy policy" } as Record<string, string>;
const DESCRIPTION = {
  de: "Welche Daten wir bei Website, Online-Buchung und Kundenkonto verarbeiten, wie lange und welche Rechte du hast.",
  en: "Which data we process for the website, online booking and customer accounts, for how long, and your rights.",
} as Record<string, string>;

export async function generateMetadata({ params }: PageProps<"/[locale]/datenschutz">) {
  const { locale } = await params;
  return pageMetadata({ locale, path: "/datenschutz", title: TITLE[locale], description: DESCRIPTION[locale] });
}

/** Privacy policy — TEMPLATE, see src/content/legal/README.md. */
export default async function Page({ params }: PageProps<"/[locale]/datenschutz">) {
  const { locale } = await params;
  return (
    <LegalPage locale={locale} eyebrow={locale === "de" ? "Rechtliches" : "Legal"} title={TITLE[locale]}>
      {locale === "de" ? <PrivacyDe /> : <PrivacyEn />}
    </LegalPage>
  );
}
