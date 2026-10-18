import type { MetadataRoute } from "next";
import { DISALLOWED } from "@/lib/public-pages";
import { SITE_URL } from "@/lib/seo";

/** robots.txt: everything public is crawlable except the admin area and email-token pages. */
export default function robots(): MetadataRoute.Robots {
  return {
    rules: [{ userAgent: "*", allow: "/", disallow: DISALLOWED }],
    sitemap: `${SITE_URL}/sitemap.xml`,
  };
}
