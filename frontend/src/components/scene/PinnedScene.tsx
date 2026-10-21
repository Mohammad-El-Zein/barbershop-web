"use client";
/* eslint-disable @next/next/no-img-element -- scene assets are preloaded manually; next/image lazy-loading would fight the preloader */

import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslations } from "next-intl";
import { siteConfig } from "@/config/site.config";
import type { ScrollTrigger } from "gsap/ScrollTrigger";
import { currentSceneMode, notifyPinnedSceneMounted } from "@/lib/sceneMode";
import { whenSceneReady } from "@/lib/sceneReady";
import { expectChapterResolver, scrollToY } from "@/lib/scroll";
import { PlaceholderBadge } from "../PlaceholderBadge";
import { ChairSequence, type TurntableController } from "./ChairSequence";
import { ChapterText } from "./ChapterText";
import { FrameCanvas, type FrameController } from "./FrameCanvas";
import { ServiceCard } from "./ServiceCard";
import { settledProgress } from "./settled";

const { scene, chapters, services } = siteConfig;

/** Depth of field: far products (depth < 0.4) get a slight blur. */
const blurFor = (depth: number) => (depth < 0.4 ? `blur(${((0.4 - depth) * 7).toFixed(1)}px)` : "");

/**
 * Object lighting, composed into one CSS filter (an inline filter would otherwise
 * override a class-based one): optional depth blur, a subtle warm-gold rim on the
 * upper-right edges (matches the key light of the backdrop), then the drop shadow.
 * Static filters on non-animating elements are rasterized once, so they cost nothing per frame.
 * The light variant drops them ([data-fx], globals.css): there they sit on moving layers.
 */
const GOLD_RIM = "drop-shadow(1.5px -1.5px 1px rgb(201 164 92 / 0.45))";
const objectFilter = (shadow: string, blur = "") => [blur, GOLD_RIM, `drop-shadow(${shadow})`].filter(Boolean).join(" ");
const CHAIR_FILTER = objectFilter("0 30px 50px rgb(0 0 0 / 0.75)");

/**
 * The pinned, scroll-scrubbed scene (technique B: layered objects, with the
 * optional technique-A frame canvas behind them, and the chair as a
 * scroll-driven turntable image sequence).
 *
 * Always dark (.scene-dark), whatever the site theme: it is built on dark photography.
 *
 * Layout (desktop): text column from the page gutter to 40vw on the left,
 * "stage box" with all objects from 42vw to the right. Mobile: stage box on top,
 * text at the bottom (low phones, ≤ 700 px: a little smaller stage, so they never touch).
 * Objects are centered with zero-size flex anchors, never with CSS transforms, so GSAP
 * fully owns every animated transform. The timeline: ./buildScene, ./timeline.
 *
 * Two variants (lib/sceneMode):
 * - full (desktops): ScrollTrigger pin over scene.scrollLengthVh, scrub smoothing, Lenis;
 * - light (phones, touch devices): no pin — the section is position: sticky inside a track of
 *   scene.lite.scrollLengthSvh (globals.css .scene-track), so the browser itself holds and
 *   releases it: it always ends and the page scrolls on natively, the address bar never moves
 *   it (svh), and there is no pin spacer to measure. Scrub without lag (follows the finger),
 *   no filters on moving layers, a small chair frame set, no preloader overlay.
 *
 * Both: a watchdog (lib/sceneWatchdog) switches to the static scene without a reload when the
 * scene keeps stuttering while it is scrolled.
 */
export function PinnedScene() {
  const t = useTranslations();
  const trackRef = useRef<HTMLDivElement>(null);
  const rootRef = useRef<HTMLElement>(null);
  const stageBoxRef = useRef<HTMLDivElement>(null);
  const frameRef = useRef<FrameController | null>(null);
  const turntableRef = useRef<TurntableController | null>(null);
  const triggerRef = useRef<ScrollTrigger | null>(null);
  const activeRef = useRef(0);
  const [active, setActive] = useState(0);
  // The poster <img> (frame 0) shows until the canvas has drawn its first frame. Hidden
  // directly in the DOM: a state update here re-rendered the whole scene on phones.
  const posterRef = useRef<HTMLImageElement>(null);
  const onChairReady = useCallback(() => posterRef.current?.classList.add("invisible"), []);
  // the preloader waits for this before it collects the critical images
  useEffect(() => notifyPinnedSceneMounted(), []);

  useEffect(() => {
    // Built once the scene is ready — desktops: the preloader is done (fonts + first-screen
    // images in); phones: right after the first screen. One build and one measurement instead
    // of several refreshes during page load. GSAP and the timeline load only then.
    let cancelled = false;
    let revert: (() => void) | undefined;
    expectChapterResolver(true); // chapter links tapped before the build wait for it
    const stop = whenSceneReady(() => {
      // the server rendered this scene; a device that gets the static one never builds it
      if (currentSceneMode() !== "pinned") return;
      import("./buildScene").then(({ buildPinnedScene }) => {
        if (cancelled || !rootRef.current) return;
        revert = buildPinnedScene({
          track: trackRef.current!,
          root: rootRef.current,
          stageBox: stageBoxRef.current!,
          frameRef,
          turntableRef,
          triggerRef,
          activeRef,
          setActive,
        });
      });
    });
    return () => {
      cancelled = true;
      expectChapterResolver(false);
      stop();
      revert?.();
    };
  }, []);

  const goToChapter = (i: number) => {
    const st = triggerRef.current;
    if (!st) return;
    scrollToY(st.start + settledProgress(chapters[i], i) * (st.end - st.start));
  };

  return (
    <div
      ref={trackRef}
      className="scene-track"
      style={{ "--scene-lite-length": `${scene.lite.scrollLengthSvh}svh` } as React.CSSProperties}
    >
      <section
        ref={rootRef}
        id="scene"
        aria-label={t("scene.label", { brand: siteConfig.brand.name })}
        className="scene-dark relative h-svh w-full overflow-hidden bg-bg text-ink"
      >
        {/* Screen-reader copy of all chapters (the visual blocks are hidden while not on screen). */}
        <div className="sr-only">
          {chapters.slice(1).map((c) => (
            <div key={c.id}>
              <h2>{(t.raw(`${c.messageKey}.headline`) as string[]).join(" ")}</h2>
              <p>{t(`${c.messageKey}.body`)}</p>
            </div>
          ))}
        </div>

        {/* ───────── background ───────── */}
        <div className="absolute inset-0" aria-hidden="true">
          <div data-obj="backdrop" className="absolute -inset-[3%] will-change-transform">
            <img
              src={scene.backdrop.src}
              srcSet={`${scene.backdrop.mobileSrc} 1280w, ${scene.backdrop.src} ${scene.backdrop.width}w`}
              sizes="106vw"
              alt=""
              width={scene.backdrop.width}
              height={scene.backdrop.height}
              fetchPriority="high"
              data-critical
              className="h-full w-full object-cover object-[70%_50%]"
            />
          </div>
          {scene.frames.enabled && <FrameCanvas controlRef={frameRef} />}

          {/* hidden until the timeline sets it (the hero has no dimming; phones show this HTML first) */}
          <div data-obj="dim" className="invisible absolute inset-0 bg-black" />
          {/* warm gold rim light from the upper right */}
          <div
            data-obj="rim"
            className="absolute inset-0"
            style={{
              background:
                "radial-gradient(ellipse 45% 55% at 78% 34%, rgb(201 164 92 / 0.26), transparent 70%), radial-gradient(ellipse 30% 20% at 70% 92%, rgb(201 164 92 / 0.16), transparent 70%)",
            }}
          />
          {/* left-side darkening behind the text (bottom on mobile) */}
          <div className="absolute inset-0 bg-[linear-gradient(90deg,rgb(10_10_10/0.92)_0%,rgb(10_10_10/0.55)_35%,transparent_60%)] max-md:bg-[linear-gradient(0deg,rgb(10_10_10/0.97)_0%,rgb(10_10_10/0.8)_38%,transparent_62%)]" />
          {/* top fade: anything in the backdrop (e.g. ceiling lamps) dissolves before reaching the nav */}
          <div className="absolute inset-x-0 top-0 h-[calc(var(--nav-h)+12vh)] bg-[linear-gradient(180deg,var(--bg)_0%,rgb(10_10_10/0.85)_45%,transparent_100%)]" />
          <div className="absolute bottom-5 right-24">
            <PlaceholderBadge src={scene.backdrop.src} />
          </div>
        </div>

        {/*
          ───────── stage box: every animated object lives here ─────────
          Everything that is not on the first screen starts invisible (the timeline takes over;
          phones show this HTML, and may load the pictures, before it is built).
          Decoration only, so it takes no pointer events: hidden actors (e.g. the orbit
          wrappers, which keep their z-index while their image is faded out) must never
          swallow a tap meant for a service card. The cards opt back in.
        */}
        <div
          ref={stageBoxRef}
          className="pointer-events-none absolute bottom-[4%] [container-type:size] left-[42vw] right-[7%] top-[calc(var(--nav-h)+4vh)] max-md:bottom-[50%] max-md:left-[var(--gutter)] max-md:[@media(max-height:700px)]:bottom-[54%] max-md:right-[var(--gutter)] max-md:top-[calc(var(--nav-h)+1svh)]"
        >
          {/* spotlight glow + floor shadow behind the product cluster (products chapter) */}
          <div className="absolute inset-0 z-[1] flex items-center justify-center" aria-hidden="true">
            <div
              data-obj="spot"
              className="invisible aspect-square w-[95cqw] max-w-[140cqh] shrink-0 rounded-full bg-[radial-gradient(circle,rgb(201_164_92/0.46)_0%,rgb(201_164_92/0.16)_38%,transparent_68%)]"
            />
          </div>
          <div className="absolute inset-x-0 bottom-[2%] z-[2] flex justify-center" aria-hidden="true">
            <div
              data-obj="floor"
              className="invisible h-[16cqh] w-[78cqw] rounded-[50%] bg-[radial-gradient(ellipse_at_center,rgb(0_0_0/0.85)_0%,rgb(0_0_0/0.5)_45%,rgb(201_164_92/0.1)_62%,transparent_72%)]"
            />
          </div>

          {/* CTA ring (behind the chair) */}
          <div className="absolute inset-0 z-[5] flex items-center justify-center" aria-hidden="true">
            <div data-obj="ring" className="invisible relative aspect-square h-[98%] max-md:h-auto max-md:w-[92%]">
              <div className="glow-pulse absolute inset-[8%] rounded-full bg-[radial-gradient(circle,rgb(201_164_92/0.32)_0%,rgb(201_164_92/0.08)_45%,transparent_70%)]" />
              <svg viewBox="0 0 200 200" className="ring-spin absolute inset-0 h-full w-full overflow-visible">
                <defs>
                  <linearGradient id="ringGold" x1="0" y1="0" x2="1" y2="1">
                    <stop offset="0" stopColor="#f1d796" />
                    <stop offset="0.5" stopColor="#c9a45c" />
                    <stop offset="1" stopColor="#8a6d2f" />
                  </linearGradient>
                </defs>
                <circle data-ring-draw data-fx cx="100" cy="100" r="92" fill="none" stroke="url(#ringGold)" strokeWidth="1.4" pathLength={1} strokeDasharray="1" style={{ filter: "drop-shadow(0 0 6px rgb(201 164 92 / 0.9))" }} />
              </svg>
              <svg viewBox="0 0 200 200" className="ring-spin-reverse absolute inset-[5%] h-[90%] w-[90%]">
                <circle data-ring-draw cx="100" cy="100" r="96" fill="none" stroke="#c9a45c" strokeOpacity="0.45" strokeWidth="0.6" strokeDasharray="1" pathLength={1} />
                <circle cx="100" cy="100" r="96" fill="none" stroke="#c9a45c" strokeOpacity="0.5" strokeWidth="1.2" strokeDasharray="0.004 0.03" pathLength={1} />
              </svg>
            </div>
          </div>

          {/* hero object (chair): turntable canvas over a poster image (= frame 0) */}
          <div className="absolute inset-0 z-10 flex items-center justify-center">
            <div
              data-obj="hero"
              className="relative h-[86%] max-w-full will-change-transform"
              style={{ aspectRatio: `${scene.heroObject.width} / ${scene.heroObject.height}` }}
            >
              {/* contact shadow under the base; lives inside the chair so it moves and fades with it */}
              <div
                aria-hidden="true"
                className="absolute -bottom-[2%] left-[12%] h-[9%] w-[76%] rounded-[50%] bg-[radial-gradient(ellipse_at_center,rgb(0_0_0/0.9)_0%,rgb(0_0_0/0.55)_40%,rgb(201_164_92/0.08)_62%,transparent_72%)]"
              />
              <div data-fx className="absolute inset-0" style={{ filter: CHAIR_FILTER }}>
                <img
                  src={scene.heroObject.src}
                  srcSet={`${scene.heroObject.mobileSrc} 640w, ${scene.heroObject.src} ${scene.heroObject.width}w`}
                  sizes="(max-width: 767px) 75vw, 45vw"
                  alt={scene.heroObject.alt}
                  width={scene.heroObject.width}
                  height={scene.heroObject.height}
                  fetchPriority="high"
                  data-critical
                  ref={posterRef}
                  className="absolute inset-0 h-full w-full object-contain"
                />
                {scene.turntable.enabled && <ChairSequence controlRef={turntableRef} onReady={onChairReady} />}
              </div>
              <PlaceholderBadge src={scene.heroObject.src} />
            </div>
          </div>

          {/* products at the sides of the chair (orbit chapter) — x/y/scale/z set by timeline.placeOrbit */}
          {scene.products.map((p) => (
            <div key={p.id} className="absolute left-1/2 top-[56%] flex h-0 w-0 items-center justify-center">
              <div data-orbit={p.id} className="relative shrink-0 will-change-transform" style={{ width: `${p.size * 0.5}cqw` }}>
                <div data-inner className="invisible">
                  <img
                    data-src={p.src} // loaded once the first screen is in (lib/sceneReady)
                    alt=""
                    width={p.width}
                    height={p.height}
                    data-fx
                    className="block h-auto w-full"
                    style={{ filter: objectFilter("0 16px 24px rgb(0 0 0 / 0.75)") }}
                  />
                </div>
              </div>
            </div>
          ))}

          {/*
            Product cluster (signature chapter). Three nested layers so tweens never
            fight over the same transform:
              [data-product] fly in / fly out
              [data-drift]   parallax drift across the chapter (speed by depth)
              [data-inner]   slow scrubbed float + rotation; depth blur on the image
          */}
          {scene.products.map((p) => (
            <div
              key={p.id}
              className="absolute flex h-0 w-0 items-center justify-center"
              style={{ left: `${p.x}%`, top: `${p.y}%`, zIndex: 20 + Math.round(p.depth * 10) }}
            >
              <div data-product={p.id} className="invisible relative shrink-0 will-change-transform" style={{ width: `${p.size}cqw` }}>
                <div data-drift>
                  <div data-inner>
                    <img
                      data-src={p.src}
                      alt={p.alt}
                      width={p.width}
                      height={p.height}
                      data-fx
                      className="block h-auto w-full"
                      style={{ filter: objectFilter("0 24px 30px rgb(0 0 0 / 0.75)", blurFor(p.depth)) }}
                    />
                  </div>
                </div>
                <PlaceholderBadge src={p.src} />
              </div>
            </div>
          ))}

          {/* feature object (big scissors) */}
          <div className="absolute inset-0 z-20 flex items-center justify-center">
            <div data-obj="feature" className="invisible relative aspect-square w-[min(80%,90vh)] shrink-0 will-change-transform max-md:w-[80%]">
              <img
                data-src={scene.featureObject.src}
                alt={scene.featureObject.alt}
                width={scene.featureObject.width}
                height={scene.featureObject.height}
                data-fx
                className="h-full w-full object-contain"
                style={{ filter: objectFilter("0 40px 60px rgb(0 0 0 / 0.85)") }}
              />
              <PlaceholderBadge src={scene.featureObject.src} />
            </div>
          </div>

          {/*
            Service cards: one row, same top, same height, equal gaps. The stagger
            is entrance timing only (timeline), never a resting offset. Buttons sit
            on one baseline because every card has the same height and pushes its
            button to the bottom. On phones the column starts at the top of the stage
            (never under the nav) and may run into the free space above the chapter text.
          */}
          <div className="absolute inset-0 z-20 flex flex-col justify-start gap-3 md:flex-row md:items-center md:justify-center">
            <div className="flex flex-col gap-3 max-md:[@media(max-height:700px)]:gap-2 md:w-full md:flex-row md:items-stretch md:justify-center md:gap-[3%]">
              {services.map((s) => (
                <div key={s.messageKey} data-card className="pointer-events-auto invisible will-change-transform md:h-[clamp(330px,48vh,420px)] md:w-[31%] md:max-w-[300px]">
                  <ServiceCard service={s} />
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* ───────── text column (page gutter → 40vw) ───────── */}
        <div
          data-text-col
          className="absolute left-[var(--gutter)] top-1/2 z-30 w-[var(--text-col)] -translate-y-1/2 max-md:bottom-[5svh] max-md:right-[var(--gutter)] max-md:[@media(max-height:700px)]:bottom-[2.5svh] max-md:top-auto max-md:w-auto max-md:translate-y-0"
        >
          <div className="grid items-center max-md:items-end [&>*]:[grid-area:1/1]">
            {chapters.map((c, i) => (
              <ChapterText key={c.id} chapter={c} index={i} animated />
            ))}
          </div>
        </div>

        {/* vignette sits above everything visual, below UI */}
        <div className="vignette absolute inset-0 z-30" aria-hidden="true" />

        {/* scroll indicator */}
        <div data-obj="scroll-cue" className="absolute bottom-8 left-[var(--gutter)] z-40 flex items-center gap-4 max-md:hidden" aria-hidden="true">
          <span className="relative block h-12 w-px overflow-hidden bg-white/15">
            <span className="scroll-cue absolute inset-0 bg-gold" />
          </span>
          <span className="text-[0.65rem] uppercase tracking-[0.4em] text-ink/60">{t("scene.scroll")}</span>
        </div>

        {/* section dots (labels on hover/focus only, so they never cover the stage) */}
        <nav aria-label={t("scene.chapters")} className="absolute right-8 top-1/2 z-40 -translate-y-1/2 max-md:hidden">
          <ol className="flex flex-col gap-4">
            {chapters.map((c, i) => {
              const label = t(`${c.messageKey}.label`);
              return (
                <li key={c.id} className="group relative flex items-center justify-end">
                  <span
                    className={`pointer-events-none absolute right-6 whitespace-nowrap text-[0.6rem] uppercase tracking-[0.3em] opacity-0 transition-opacity duration-300 group-focus-within:opacity-100 group-hover:opacity-100 ${
                      i === active ? "text-gold" : "text-ink/60"
                    }`}
                  >
                    {label}
                  </span>
                  <button
                    type="button"
                    onClick={() => goToChapter(i)}
                    aria-label={t("scene.goTo", { label })}
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
              );
            })}
          </ol>
        </nav>
      </section>
    </div>
  );
}
