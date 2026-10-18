/**
 * The indexable pages (path after the locale). They are listed in sitemap.xml with
 * hreflang alternates; everything else is private (noindex) or an ad landing page.
 */
export const PUBLIC_PAGES: { path: string; priority: number; changeFrequency: "weekly" | "monthly" | "yearly" }[] = [
  { path: "", priority: 1, changeFrequency: "weekly" },
  { path: "/booking", priority: 0.9, changeFrequency: "weekly" },
  { path: "/agb", priority: 0.3, changeFrequency: "yearly" },
  { path: "/impressum", priority: 0.2, changeFrequency: "yearly" },
  { path: "/datenschutz", priority: 0.2, changeFrequency: "yearly" },
];

/**
 * Never crawled: admin area, email-token links (payment, manage, status, barber
 * decision, verification / reset). Account pages like login stay crawlable so
 * crawlers can see their noindex.
 */
export const DISALLOWED = [
  "/*/admin",
  "/*/pay/",
  "/*/booking/manage/",
  "/*/booking/status/",
  "/*/barber/",
  "/*/account/verify/",
  "/*/account/reset-password/",
];
