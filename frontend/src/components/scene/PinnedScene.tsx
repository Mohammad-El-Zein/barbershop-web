"use client";
/* eslint-disable @next/next/no-img-element -- scene assets are preloaded manually; next/image lazy-loading would fight the preloader */

import { useRef, useState } from "react";
import { siteConfig } from "@/config/site.config";
import { gsap, ScrollTrigger, useGSAP } from "@/lib/gsap";
import { registerChapterResolver, scrollToY } from "@/lib/scroll";
import { ChapterText } from "./ChapterText";
import { FrameCanvas, type FrameController } from "./FrameCanvas";
import { ServiceCard } from "./ServiceCard";
import { buildSceneTimeline, settledProgress } from "./timeline";

const { scene, chapters, services } = siteConfig;

/**
 * The pinned, scroll-scrubbed scene (technique B: layered objects, with the
 * optional technique-A frame canvas behind them).
 *
 * Layout: full-viewport stage; objects live in the "stage box" on the right
 * (top half on mobile), chapter text on the left (bottom on mobile).
 */
export function PinnedScene() {
  const rootRef = useRef<HTMLElement>(null);
  const stageBoxRef = useRef<HTMLDivElement>(null);
  const frameRef = useRef<FrameController | null>(null);
  const triggerRef = useRef<ScrollTrigger | null>(null);
  const activeRef = useRef(0);
  const [active, setActive] = useState(0);

  useGSAP(
    () => {
      const mm = gsap.matchMedia();

      mm.add({ isDesktop: "(min-width: 768px)", isMobile: "(max-width: 767px)" }, (context) => {
        const { isMobile } = context.conditions as { isMobile: boolean };

        const tl = gsap.timeline({
          defaults: { ease: "none" },
          scrollTrigger: {
            trigger: rootRef.current,
            start: "top top",
            end: () => `+=${(window.innerHeight * scene.scrollLengthVh) / 100}`,
            pin: true,
            scrub: scene.scrub,
            anticipatePin: 1,
            onUpdate: (self) => {
              const p = self.progress;
              frameRef.current?.setProgress(p);
              // Active chapter for the section dots (state only changes on chapter switch).
              const idx = chapters.findIndex((c) => p >= c.range[0] && p < c.range[1]);
              const next = idx === -1 ? chapters.length - 1 : idx;
              if (next !== activeRef.current) {
                activeRef.current = next;
                setActive(next);
              }
            },
          },
        });

        const { placeOrbit } = buildSceneTimeline(tl, {
          root: rootRef.current!,
          stageBox: stageBoxRef.current!,
          chapters,
          products: scene.products,
          isMobile,
        });
        triggerRef.current = tl.scrollTrigger ?? null;

        // Orbit radius depends on stage size.
        const onResize = () => placeOrbit();
        window.addEventListener("resize", onResize);

        // Let nav links like "#services" jump to a chapter inside the pin.
        const unregister = registerChapterResolver((anchor) => {
          const i = chapters.findIndex((c) => c.anchor === anchor);
          const st = triggerRef.current;
          if (i === -1 || !st) return null;
          return st.start + settledProgress(chapters[i], i) * (st.end - st.start);
        });

        return () => {
          window.removeEventListener("resize", onResize);
          unregister();
          triggerRef.current = null;
        };
      });

      return () => mm.revert();
    },
    { scope: rootRef },
  );

  const goToChapter = (i: number) => {
    const st = triggerRef.current;
    if (!st) return;
    scrollToY(st.start + settledProgress(chapters[i], i) * (st.end - st.start));
  };

  return (
    <section ref={rootRef} id="scene" aria-label={`${siteConfig.brand.name} — introduction`} className="relative h-svh w-full overflow-hidden">
      {/* Screen-reader copy of all chapters (the visual blocks are hidden while not on screen). */}
      <div className="sr-only">
        {chapters.slice(1).map((c) => (
          <div key={c.id}>
            <h2>{c.headline.join(" ")}</h2>
            {c.body && <p>{c.body}</p>}
          </div>
        ))}
      </div>

      {/* ───────── stage ───────── */}
      <div className="absolute inset-0" aria-hidden="true">
        <div data-obj="backdrop" className="absolute -inset-[3%] will-change-transform">
          <img
            src={scene.backdrop.src}
            alt=""
            width={scene.backdrop.width}
            height={scene.backdrop.height}
            fetchPriority="high"
            className="h-full w-full object-cover object-[70%_50%]"
          />
        </div>
        {scene.frames.enabled && <FrameCanvas controlRef={frameRef} />}

        <div data-obj="dim" className="absolute inset-0 bg-black" />
        {/* warm gold rim light from the upper right */}
        <div
          data-obj="rim"
          className="absolute inset-0"
          style={{
            background:
              "radial-gradient(ellipse 45% 55% at 78% 30%, rgb(201 164 92 / 0.28), transparent 70%), radial-gradient(ellipse 30% 20% at 70% 92%, rgb(201 164 92 / 0.18), transparent 70%)",
          }}
        />
        {/* left-side darkening behind the text (bottom on mobile) */}
        <div className="absolute inset-0 bg-[linear-gradient(90deg,rgb(10_10_10/0.92)_0%,rgb(10_10_10/0.55)_35%,transparent_60%)] max-md:bg-[linear-gradient(0deg,rgb(10_10_10/0.97)_0%,rgb(10_10_10/0.8)_38%,transparent_62%)]" />
      </div>

      {/*
        Stage box: all objects live here. Centering is done with zero-size flex
        "anchors" (no CSS transforms), so GSAP owns the transform of every
        animated element without fighting a translate(-50%,-50%).
      */}
      <div
        ref={stageBoxRef}
        className="@container absolute bottom-[4%] left-[40%] right-[7%] top-[11%] max-md:bottom-[50%] max-md:left-4 max-md:right-4 max-md:top-[9%]"
      >
        {/* CTA ring (behind the chair) */}
        <div className="absolute inset-0 z-[5] flex items-center justify-center">
          <div data-obj="ring" className="relative aspect-square h-[98%] max-md:h-auto max-md:w-[92%]">
            <div className="glow-pulse absolute inset-[8%] rounded-full bg-[radial-gradient(circle,rgb(201_164_92/0.32)_0%,rgb(201_164_92/0.08)_45%,transparent_70%)]" />
            <svg viewBox="0 0 200 200" className="ring-spin absolute inset-0 h-full w-full overflow-visible">
              <defs>
                <linearGradient id="ringGold" x1="0" y1="0" x2="1" y2="1">
                  <stop offset="0" stopColor="#f1d796" />
                  <stop offset="0.5" stopColor="#c9a45c" />
                  <stop offset="1" stopColor="#8a6d2f" />
                </linearGradient>
              </defs>
              <circle data-ring-draw cx="100" cy="100" r="92" fill="none" stroke="url(#ringGold)" strokeWidth="1.4" pathLength={1} strokeDasharray="1" style={{ filter: "drop-shadow(0 0 6px rgb(201 164 92 / 0.9))" }} />
            </svg>
            <svg viewBox="0 0 200 200" className="ring-spin-reverse absolute inset-[5%] h-[90%] w-[90%]">
              <circle data-ring-draw cx="100" cy="100" r="96" fill="none" stroke="#c9a45c" strokeOpacity="0.45" strokeWidth="0.6" strokeDasharray="1" pathLength={1} />
              <circle cx="100" cy="100" r="96" fill="none" stroke="#c9a45c" strokeOpacity="0.5" strokeWidth="1.2" strokeDasharray="0.004 0.03" pathLength={1} />
            </svg>
          </div>
        </div>

        {/* hero object (chair) */}
        <div className="absolute inset-0 z-10 flex items-center justify-center">
          <div
            data-obj="hero"
            className="h-[94%] max-w-full will-change-transform"
            style={{ aspectRatio: `${scene.heroObject.width} / ${scene.heroObject.height}` }}
          >
            <img
              src={scene.heroObject.src}
              alt={scene.heroObject.alt}
              width={scene.heroObject.width}
              height={scene.heroObject.height}
              fetchPriority="high"
              className="h-full w-full object-contain drop-shadow-[0_30px_60px_rgba(0,0,0,0.8)]"
            />
          </div>
        </div>

        {/* products orbiting the chair (premium chapter) — x/y/scale/z set by timeline.placeOrbit */}
        {scene.products.map((p) => (
          <div key={p.id} className="absolute left-1/2 top-[58%] flex h-0 w-0 items-center justify-center">
            <div data-orbit={p.id} className="shrink-0 will-change-transform" style={{ width: `${p.size * 0.8}cqw` }}>
              <div data-inner>
                <img src={p.src} alt="" width={p.width} height={p.height} className="h-auto w-full drop-shadow-[0_16px_24px_rgba(0,0,0,0.7)]" />
              </div>
            </div>
          </div>
        ))}

        {/* products flying in (signature chapter) */}
        {scene.products.map((p) => (
          <div
            key={p.id}
            className="absolute z-20 flex h-0 w-0 items-center justify-center"
            style={{ left: `${p.x}%`, top: `${p.y}%` }}
          >
            <div data-product={p.id} className="shrink-0 will-change-transform" style={{ width: `${p.size}cqw` }}>
              <div data-inner>
                <img src={p.src} alt={p.alt} width={p.width} height={p.height} className="h-auto w-full drop-shadow-[0_20px_30px_rgba(0,0,0,0.7)]" />
              </div>
            </div>
          </div>
        ))}

        {/* feature object (big scissors) */}
        <div className="absolute inset-0 z-20 flex items-center justify-center">
          <div data-obj="feature" className="aspect-square w-[min(80%,90vh)] shrink-0 will-change-transform max-md:w-[80%]">
            <img
              src={scene.featureObject.src}
              alt={scene.featureObject.alt}
              width={scene.featureObject.width}
              height={scene.featureObject.height}
              className="h-full w-full object-contain drop-shadow-[0_40px_60px_rgba(0,0,0,0.85)]"
            />
          </div>
        </div>

        {/* service cards */}
        <div className="absolute inset-0 z-20 flex flex-col justify-center gap-3 md:flex-row md:items-center md:gap-5">
          {services.map((s, i) => (
            <div key={s.title} data-card className={`will-change-transform md:w-[32%] md:max-w-[310px] ${i === 1 ? "md:mt-20" : ""}`}>
              <ServiceCard service={s} />
            </div>
          ))}
        </div>
      </div>

      {/* ───────── text column ───────── */}
      <div className="absolute left-[6vw] top-1/2 z-30 w-[min(40vw,600px)] -translate-y-1/2 max-md:bottom-[5svh] max-md:left-5 max-md:right-5 max-md:top-auto max-md:w-auto max-md:translate-y-0">
        <div className="grid items-center max-md:items-end [&>*]:[grid-area:1/1]">
          {chapters.map((c, i) => (
            <ChapterText key={c.id} chapter={c} index={i} animated />
          ))}
        </div>
      </div>

      {/* vignette sits above everything visual, below UI */}
      <div className="vignette absolute inset-0 z-30" aria-hidden="true" />

      {/* scroll indicator */}
      <div data-obj="scroll-cue" className="absolute bottom-8 left-[6vw] z-40 flex items-center gap-4 max-md:hidden" aria-hidden="true">
        <span className="relative block h-12 w-px overflow-hidden bg-white/15">
          <span className="scroll-cue absolute inset-0 bg-gold" />
        </span>
        <span className="text-[0.65rem] uppercase tracking-[0.4em] text-ink/60">Scroll to explore</span>
      </div>

      {/* section dots */}
      <nav aria-label="Chapters" className="absolute right-8 top-1/2 z-40 -translate-y-1/2 max-md:hidden">
        <ol className="flex flex-col gap-4">
          {chapters.map((c, i) => (
            <li key={c.id} className="group relative flex items-center justify-end">
              <span
                className={`pointer-events-none absolute right-6 whitespace-nowrap text-[0.6rem] uppercase tracking-[0.3em] transition-opacity duration-300 max-md:hidden ${
                  i === active ? "text-gold opacity-100" : "text-ink/60 opacity-0 group-hover:opacity-100"
                }`}
              >
                {c.label}
              </span>
              <button
                type="button"
                onClick={() => goToChapter(i)}
                aria-label={`Go to ${c.label}`}
                aria-current={i === active ? "step" : undefined}
                className="flex h-4 w-4 items-center justify-center rounded-full focus-visible:outline focus-visible:outline-1 focus-visible:outline-gold"
              >
                <span
                  className={`block rounded-full transition-all duration-300 ${
                    i === active ? "h-2.5 w-2.5 bg-gold shadow-[0_0_12px_rgba(201,164,92,0.9)]" : "h-1.5 w-1.5 bg-ink/40 group-hover:bg-ink/80"
                  }`}
                />
              </button>
            </li>
          ))}
        </ol>
      </nav>
    </section>
  );
}
