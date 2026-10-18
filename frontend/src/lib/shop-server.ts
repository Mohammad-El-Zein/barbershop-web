import { cacheLife } from "next/cache";
import { API_URL, TENANT } from "./api/config";
import { DEFAULT_RULES } from "./rules";
import type { BookingRules, Service, Shop } from "./api/types";

/**
 * Shop data for server components (structured data, legal pages, ad landing pages).
 *
 * Server-side requests go to API_URL_INTERNAL when set (e.g. http://backend:8080
 * inside docker / Container Apps), otherwise to the public NEXT_PUBLIC_API_URL.
 * Results are cached ("use cache"): an hour when the backend answered, only a few
 * minutes when it did not (e.g. during `next build` without a running backend) —
 * then callers fall back to site.config / default rules.
 */
const SERVER_API_URL = (process.env.API_URL_INTERNAL ?? API_URL).replace(/\/$/, "");

export type ShopData = {
  shop: Shop | null;
  services: Service[];
  /** The shop's booking rules, or the defaults if the backend was unreachable. */
  rules: Required<BookingRules>;
};

async function get<T>(path: string, locale: string): Promise<T> {
  const res = await fetch(`${SERVER_API_URL}${path}`, {
    headers: { "Accept-Language": locale, Accept: "application/json" },
    signal: AbortSignal.timeout(4000),
  });
  if (!res.ok) throw new Error(`${res.status} ${path}`);
  return (await res.json()) as T;
}

export async function loadShopData(locale: string): Promise<ShopData> {
  "use cache";
  const [shop, services] = await Promise.all([
    get<Shop>(`/api/public/${TENANT}`, locale).catch(() => null),
    get<Service[]>(`/api/public/${TENANT}/services?lang=${locale}`, locale).catch(() => null),
  ]);
  if (shop && services) cacheLife("hours");
  else cacheLife("minutes"); // backend unreachable: retry soon
  return {
    shop,
    services: services ?? [],
    rules: { ...DEFAULT_RULES, ...(shop?.bookingRules ?? {}) } as Required<BookingRules>,
  };
}
