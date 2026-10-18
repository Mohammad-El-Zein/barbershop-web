import { readFile } from "node:fs/promises";
import { join } from "node:path";
import { ImageResponse } from "next/og";
import { getTranslations } from "next-intl/server";
import { siteConfig } from "@/config/site.config";

/**
 * Generated share image (Open Graph + Twitter, 1200×630) per locale, in the site's
 * dark/gold look. Prerendered at build time (the locale params are static).
 */
export const alt = siteConfig.brand.name;
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

export default async function Image({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "meta" });
  const anton = await readFile(join(process.cwd(), "src/assets/fonts/Anton-Regular.ttf"));
  const { bg, gold, text } = { bg: siteConfig.theme.dark.bg, gold: siteConfig.theme.dark.gold, text: siteConfig.theme.dark.text };

  return new ImageResponse(
    (
      <div
        style={{
          width: "100%",
          height: "100%",
          display: "flex",
          flexDirection: "column",
          justifyContent: "space-between",
          padding: "72px 80px",
          background: `radial-gradient(circle at 78% 40%, rgba(201,164,92,0.28) 0%, rgba(201,164,92,0.06) 38%, ${bg} 70%)`,
          color: text,
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: 18, fontSize: 26, letterSpacing: 8, color: gold }}>
          <div style={{ width: 64, height: 2, background: gold }} />
          {t("ogEyebrow")}
        </div>
        <div style={{ display: "flex", flexDirection: "column" }}>
          <div style={{ fontFamily: "Anton", fontSize: 150, lineHeight: 0.95, letterSpacing: 2 }}>{siteConfig.brand.name}</div>
          <div style={{ marginTop: 28, fontSize: 40, color: gold }}>{t("ogTagline")}</div>
        </div>
        <div style={{ display: "flex", justifyContent: "space-between", fontSize: 24, color: "rgba(242,239,233,0.7)" }}>
          <span>{t("ogFooter")}</span>
          <span style={{ color: gold }}>{siteConfig.siteUrl.replace(/^https?:\/\//, "")}</span>
        </div>
      </div>
    ),
    { ...size, fonts: [{ name: "Anton", data: anton, style: "normal", weight: 400 }] },
  );
}
