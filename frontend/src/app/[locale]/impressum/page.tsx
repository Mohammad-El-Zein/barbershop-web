import { LegalPage } from "@/components/legal/Legal";
import { ImpressumDe, ImpressumEn } from "@/content/legal/Impressum";
import { pageMetadata } from "@/lib/seo";

const TITLE = { de: "Impressum", en: "Legal notice" } as Record<string, string>;
const DESCRIPTION = {
  de: "Impressum: Anbieterkennzeichnung, Kontakt und Registerangaben des Barbershops.",
  en: "Legal notice: provider identification, contact and register details of the barbershop.",
} as Record<string, string>;

export async function generateMetadata({ params }: PageProps<"/[locale]/impressum">) {
  const { locale } = await params;
  return pageMetadata({ locale, path: "/impressum", title: TITLE[locale], description: DESCRIPTION[locale] });
}

/** Impressum / legal notice — TEMPLATE, see src/content/legal/README.md. */
export default async function Page({ params }: PageProps<"/[locale]/impressum">) {
  const { locale } = await params;
  return (
    <LegalPage locale={locale} eyebrow={locale === "de" ? "Rechtliches" : "Legal"} title={TITLE[locale]}>
      {locale === "de" ? <ImpressumDe /> : <ImpressumEn />}
    </LegalPage>
  );
}
