/* eslint-disable @next/next/no-img-element -- scene assets are preloaded manually; next/image lazy-loading would fight the preloader */
import type { ServiceCard as ServiceCardData } from "@/config/site.config";
import { ActionButton } from "../ActionButton";

/** Glass service card. Compact horizontal layout on mobile, tall card on desktop. */
export function ServiceCard({ service, className = "" }: { service: ServiceCardData; className?: string }) {
  return (
    <article className={`glass flex overflow-hidden rounded-2xl md:flex-col ${className}`}>
      <div className="relative w-28 shrink-0 overflow-hidden md:aspect-[4/3] md:w-full">
        <img
          src={service.image.src}
          alt={service.image.alt}
          width={service.image.width}
          height={service.image.height}
          decoding="async"
          className="h-full w-full object-cover"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-black/60 to-transparent" aria-hidden="true" />
      </div>
      <div className="flex flex-1 flex-col gap-1.5 p-4 md:gap-2 md:p-5">
        <div className="flex items-baseline justify-between gap-3">
          <h3 className="display text-xl md:text-2xl">{service.title}</h3>
          <span className="text-xs font-semibold tracking-wider text-gold">{service.price}</span>
        </div>
        <p className="line-clamp-2 text-xs leading-relaxed text-muted md:line-clamp-none md:text-sm">{service.text}</p>
        <div className="mt-auto hidden pt-3 md:block">
          <ActionButton cta={service.cta} className="!h-9 !px-4 !text-[0.65rem]" source={`service-${service.title}`} />
        </div>
      </div>
    </article>
  );
}
