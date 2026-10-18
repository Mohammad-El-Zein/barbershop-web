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
 * The Book button preselects this service. On phones it shares the bottom row
 * with the price (one-line description), so three cards still fit the stage.
 */
export function ServiceCard({ service, className = "" }: { service: ServiceCardData; className?: string }) {
  const t = useTranslations(service.messageKey);
  const title = t("title");

  return (
    <article className={`glass flex h-full overflow-hidden rounded-2xl md:flex-col ${className}`}>
      <div className="relative w-28 shrink-0 overflow-hidden md:aspect-[16/10] md:w-full">
        <img
          data-src={service.image.src} // the cards appear late in the scroll: loaded after the first screen (lib/sceneReady)
          alt={service.image.alt || title}
          width={service.image.width}
          height={service.image.height}
          decoding="async"
          className="h-full w-full object-cover"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-black/60 to-transparent" aria-hidden="true" />
        <PlaceholderBadge src={service.image.src} />
      </div>
      <div className="flex min-h-0 min-w-0 flex-1 flex-col gap-1 p-3 md:gap-2 md:p-5">
        <div className="flex items-baseline justify-between gap-3">
          <h3 className="display min-w-0 text-lg [overflow-wrap:anywhere] max-md:leading-tight md:text-2xl">{title}</h3>
          <span className="hidden shrink-0 text-xs font-semibold tracking-wider text-gold-text md:inline">{t("price")}</span>
        </div>
        <p className="line-clamp-1 text-xs leading-relaxed text-muted md:line-clamp-3 md:text-sm">{t("text")}</p>
        <div className="mt-auto flex items-center justify-between gap-3 pt-1.5 md:block md:pt-3">
          <span className="text-xs font-semibold tracking-wider text-gold-text md:hidden">{t("price")}</span>
          <ActionButton
            cta={service.cta}
            className="!h-8 !px-4 !text-[0.65rem] md:!h-9"
            source={`service-${service.messageKey}`}
            serviceIds={[service.serviceId]}
          />
        </div>
      </div>
    </article>
  );
}
