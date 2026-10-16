"use client";
/* eslint-disable @next/next/no-img-element -- scene assets are preloaded manually; next/image lazy-loading would fight the preloader */

import { useTranslations } from "next-intl";
import type { ServiceCard as ServiceCardData } from "@/config/site.config";
import { ActionButton } from "../ActionButton";
import { PlaceholderBadge } from "../PlaceholderBadge";

/**
 * Glass service card (copy from messages → service.messageKey).
 * Compact horizontal layout on mobile, tall card on desktop. It fills its
 * wrapper's height (h-full) so all cards in a row are equal height, and the
 * button is pushed to the bottom (mt-auto) so buttons share one baseline.
 */
export function ServiceCard({ service, className = "" }: { service: ServiceCardData; className?: string }) {
  const t = useTranslations(service.messageKey);
  const title = t("title");

  return (
    <article className={`glass flex h-full overflow-hidden rounded-2xl md:flex-col ${className}`}>
      <div className="relative w-28 shrink-0 overflow-hidden md:aspect-[16/10] md:w-full">
        <img
          src={service.image.src}
          alt={service.image.alt || title}
          width={service.image.width}
          height={service.image.height}
          decoding="async"
          className="h-full w-full object-cover"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-black/60 to-transparent" aria-hidden="true" />
        <PlaceholderBadge src={service.image.src} />
      </div>
      <div className="flex min-h-0 flex-1 flex-col gap-1.5 p-4 md:gap-2 md:p-5">
        <div className="flex items-baseline justify-between gap-3">
          <h3 className="display min-w-0 text-xl [overflow-wrap:anywhere] md:text-2xl">{title}</h3>
          <span className="shrink-0 text-xs font-semibold tracking-wider text-gold-text">{t("price")}</span>
        </div>
        <p className="line-clamp-2 text-xs leading-relaxed text-muted md:line-clamp-3 md:text-sm">{t("text")}</p>
        <div className="mt-auto hidden pt-3 md:block">
          <ActionButton cta={service.cta} className="!h-9 !px-4 !text-[0.65rem]" source={`service-${service.messageKey}`} />
        </div>
      </div>
    </article>
  );
}
