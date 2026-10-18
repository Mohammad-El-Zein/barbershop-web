"use client";

import { useLocale, useTranslations } from "next-intl";
import { siteConfig } from "@/config/site.config";
import { openBooking } from "@/lib/booking";

/** Body of the 404 and error pages: big code, short text, ways back (home, booking, retry). */
export function ErrorView({ kind, onRetry, digest }: { kind: "notFound" | "error"; onRetry?: () => void; digest?: string }) {
  const t = useTranslations(`errorPages.${kind}`);
  const tc = useTranslations("errorPages");
  const locale = useLocale();
  return (
    <section aria-labelledby="error-title" className="py-10 text-center md:py-16">
      {/* React hoists <title> into the head (the not-found / error boundaries have no metadata) */}
      <title>{`${t("title")} | ${siteConfig.brand.name}`}</title>
      <p className="display text-[clamp(5rem,22vw,11rem)] leading-none text-gold-text" aria-hidden="true">
        {kind === "notFound" ? "404" : "500"}
      </p>
      <h1 id="error-title" className="display mt-4 text-4xl sm:text-5xl">
        {t("title")}
      </h1>
      <p className="mx-auto mt-4 max-w-md text-muted">{t("text")}</p>
      <div className="mt-8 flex flex-wrap justify-center gap-3">
        {onRetry && (
          <button type="button" className="btn btn-primary" onClick={onRetry}>
            {tc("retry")}
          </button>
        )}
        <a href={`/${locale}`} className={onRetry ? "btn btn-ghost" : "btn btn-primary"}>
          {tc("home")}
        </a>
        <button type="button" className="btn btn-ghost" onClick={() => openBooking(`error-${kind}`)}>
          {tc("book")}
        </button>
      </div>
      {digest && <p className="mt-8 text-xs text-muted">{tc("reference", { id: digest })}</p>}
    </section>
  );
}
