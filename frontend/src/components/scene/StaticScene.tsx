"use client";
/* eslint-disable @next/next/no-img-element -- plain images keep this fallback dependency-free */

import { siteConfig, type Chapter } from "@/config/site.config";
import { ChapterText } from "./ChapterText";
import { ServiceCard } from "./ServiceCard";

const { scene, chapters, services } = siteConfig;

/**
 * prefers-reduced-motion fallback: the same chapters as ordinary stacked
 * sections with static imagery (the chair is the single poster image, no
 * turntable). No pinning, no scrubbing, no smooth scroll. Always dark, like the
 * animated scene. Uses the shared page gutter so text aligns with the nav logo.
 */
export function StaticScene() {
  return (
    <div id="scene" className="scene-dark bg-bg text-ink">
      {chapters.map((c, i) => (
        <section
          key={c.id}
          id={c.anchor}
          className="relative flex min-h-svh items-center overflow-hidden border-b border-line py-28"
        >
          {i === 0 && (
            <img
              src={scene.backdrop.src}
              srcSet={`${scene.backdrop.mobileSrc} 1280w, ${scene.backdrop.src} ${scene.backdrop.width}w`}
              sizes="100vw"
              alt=""
              aria-hidden="true"
              className="absolute inset-0 h-full w-full object-cover opacity-60"
            />
          )}
          <div className="absolute inset-0 bg-[linear-gradient(90deg,rgb(10_10_10/0.95)_0%,rgb(10_10_10/0.5)_45%,transparent_70%)]" aria-hidden="true" />
          <div className="relative grid w-full items-center gap-12 px-[var(--gutter)] md:grid-cols-[minmax(0,var(--text-col))_1fr] md:gap-[4vw]">
            <ChapterText chapter={c} index={i} animated={false} />
            <StaticVisual chapter={c} />
          </div>
        </section>
      ))}
    </div>
  );
}

function StaticVisual({ chapter }: { chapter: Chapter }) {
  switch (chapter.stage) {
    case "services":
      return (
        <div className="grid gap-4 md:grid-cols-3">
          {services.map((s) => (
            <ServiceCard key={s.messageKey} service={s} />
          ))}
        </div>
      );
    case "products":
      // Trimmed cut-outs have different aspect ratios: give each the same box and contain it.
      return (
        <div className="grid grid-cols-2 gap-8">
          {scene.products.map((p) => (
            <div key={p.id} className="flex h-44 items-center justify-center md:h-56">
              <img
                src={p.src}
                alt={p.alt}
                width={p.width}
                height={p.height}
                loading="lazy"
                className="max-h-full max-w-full object-contain drop-shadow-[0_20px_30px_rgba(0,0,0,0.7)]"
                style={{ rotate: `${p.rotate}deg` }}
              />
            </div>
          ))}
        </div>
      );
    case "feature":
      return <img src={scene.featureObject.src} alt={scene.featureObject.alt} loading="lazy" className="mx-auto w-full max-w-lg" />;
    case "cta":
      return (
        <div className="relative mx-auto aspect-square w-full max-w-lg">
          <div className="absolute inset-0 rounded-full border border-gold shadow-[0_0_60px_rgba(201,164,92,0.35)]" />
          <img src={scene.heroObject.src} alt={scene.heroObject.alt} loading="lazy" className="absolute inset-[8%] h-[84%] w-[84%] object-contain" />
        </div>
      );
    default: // hero, orbit
      return <img src={scene.heroObject.src} alt={scene.heroObject.alt} loading="lazy" className="mx-auto max-h-[70svh] w-auto" />;
  }
}
