import { jsonLdString, shopJsonLd } from "@/lib/jsonld";
import { loadShopData } from "@/lib/shop-server";

/** schema.org LocalBusiness (HairSalon) data for the shop, from the backend's (cached) shop data. */
export async function ShopJsonLd({ locale }: { locale: string }) {
  const data = await loadShopData(locale);
  return <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: jsonLdString(shopJsonLd(data, locale)) }} />;
}
