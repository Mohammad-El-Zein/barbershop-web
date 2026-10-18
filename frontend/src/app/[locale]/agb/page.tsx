import { LegalPage } from "@/components/legal/Legal";
import { TermsDe, TermsEn } from "@/content/legal/Terms";
import { pageMetadata } from "@/lib/seo";
import { loadShopData } from "@/lib/shop-server";

const TITLE = { de: "AGB & Stornobedingungen", en: "Terms & cancellation policy" } as Record<string, string>;

export async function generateMetadata({ params }: PageProps<"/[locale]/agb">) {
  const { locale } = await params;
  const { rules } = await loadShopData(locale);
  const free = Math.round(rules.refundCutoffMin / 60);
  const online = Math.round(rules.onlineCancelCutoffMin / 60);
  const description =
    locale === "de"
      ? `Buchung, Anzahlung und Stornierung: kostenlos bis ${free} Stunden vorher, online bis ${online} Stunden vorher.`
      : `Booking, deposit and cancellation: free up to ${free} hours before, online up to ${online} hours before.`;
  return pageMetadata({ locale, path: "/agb", title: TITLE[locale], description });
}

/** Terms incl. deposit and cancellation rules (numbers from the shop's live rules) — TEMPLATE. */
export default async function Page({ params }: PageProps<"/[locale]/agb">) {
  const { locale } = await params;
  const { rules } = await loadShopData(locale);
  return (
    <LegalPage locale={locale} eyebrow={locale === "de" ? "Rechtliches" : "Legal"} title={TITLE[locale]}>
      {locale === "de" ? <TermsDe rules={rules} locale={locale} /> : <TermsEn rules={rules} locale={locale} />}
    </LegalPage>
  );
}
