/* eslint-disable @next/next/no-img-element -- plain images keep this fallback dependency-free */
import { siteConfig, type Chapter } from "@/config/site.config";
import { ChapterText } from "./ChapterText";
import { ServiceCard } from "./ServiceCard";

const { scene, chapters, services } = siteConfig;

/**
 * prefers-reduced-motion fallback: the same chapters as ordinary stacked
 * sections with static imagery. No pinning, no scrubbing, no smooth scroll.
 */
export function StaticScene() {
  return (
    <div id="scene">
      {chapters.map((c, i) => (
        <section
          key={c.id}
          id={c.anchor}
          className="relative flex min-h-svh items-center overflow-hidden border-b border-white/5 py-28"
        >
          {i === 0 && (
            <img
              src={scene.backdrop.src}
              alt=""
              aria-hidden="true"
              className="absolute inset-0 h-full w-full object-cover opacity-60"
            />
          )}
          <div className="absolute inset-0 bg-[linear-gradient(90deg,rgb(10_10_10/0.95)_0%,rgb(10_10_10/0.5)_45%,transparent_70%)]" aria-hidden="true" />
          <div className="relative mx-auto grid w-full max-w-[1400px] items-center gap-12 px-5 md:grid-cols-2 md:px-10">
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
            <ServiceCard key={s.title} service={s} />
          ))}
        </div>
      );
    case "products":
      return (
        <div className="grid grid-cols-2 gap-6">
          {scene.products.map((p) => (
            <img key={p.id} src={p.src} alt={p.alt} width={p.width} height={p.height} loading="lazy" className="w-full" style={{ rotate: `${p.rotate}deg` }} />
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
