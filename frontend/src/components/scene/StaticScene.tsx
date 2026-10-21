"use client";
/* eslint-disable @next/next/no-img-element -- plain images keep this scene dependency-free */

import { useEffect, useRef } from "react";
import { siteConfig, type Chapter } from "@/config/site.config";
import { NAV_SURFACE_EVENT } from "../Nav";
import { ChapterText } from "./ChapterText";
import { ServiceCard } from "./ServiceCard";

const { scene, chapters, services } = siteConfig;

/**
 * The landing scene without pinning or scrubbing (lib/sceneMode: phones, small / touch tablets,
 * reduced motion, weak devices): the chapters as ordinary stacked sections with static imagery,
 * native scrolling, and a light entrance for what scrolls into view (opacity / transform only,
 * IntersectionObserver, nothing per scroll frame). Always dark, like the animated scene.
 *
 * Only the first screen's pictures load with the page; the rest carry data-src and load like in
 * the pinned scene (lib/sceneReady: phones on the first touch / scroll, desktops right after).
 *
 * Phones: picture and text stacked closely (the hero picture first, then each chapter's text
 * above its picture), section heights from their content — no full-screen sections. From 768 px
 * (reduced motion / weak desktops) two columns, one screen per chapter.
 */
export function StaticScene() {
  const rootRef = useRef<HTMLDivElement>(null);
  useReveal(rootRef);
  useNavSurface();

  return (
    <div ref={rootRef} id="scene" className="scene-dark bg-bg text-ink">
      {chapters.map((c, i) => {
        const hero = i === 0;
        return (
          <section
            key={c.id}
            id={c.anchor}
            className={`relative overflow-hidden border-b border-line md:flex md:min-h-svh md:items-center md:py-28 ${
              hero ? "pb-12 pt-[calc(var(--nav-h)+0.25rem)]" : "py-12 sm:py-16"
            }`}
          >
            {hero && (
              <>
                <img
                  src={scene.backdrop.mobileSrc}
                  srcSet={`${scene.backdrop.mobileSrc} 1280w, ${scene.backdrop.src} ${scene.backdrop.width}w`}
                  sizes="100vw"
                  alt=""
                  aria-hidden="true"
                  fetchPriority="high"
                  className="absolute inset-0 h-full w-full object-cover object-[70%_50%] opacity-60"
                />
                {/* phones: dark from the bottom (text), desktop: from the left (text column) */}
                <div
                  className="absolute inset-0 bg-[linear-gradient(0deg,rgb(10_10_10/0.97)_0%,rgb(10_10_10/0.75)_45%,rgb(10_10_10/0.35)_100%)] md:bg-[linear-gradient(90deg,rgb(10_10_10/0.95)_0%,rgb(10_10_10/0.5)_45%,transparent_70%)]"
                  aria-hidden="true"
                />
              </>
            )}
            <div className="relative grid w-full items-center gap-6 px-[var(--gutter)] md:grid-cols-[minmax(0,var(--text-col))_1fr] md:gap-[4vw]">
              <div data-reveal className={hero ? "max-md:order-2" : undefined}>
                <ChapterText chapter={c} index={i} animated={false} />
              </div>
              <div data-reveal className={hero ? "max-md:order-1" : undefined}>
                <StaticVisual chapter={c} />
              </div>
            </div>
          </section>
        );
      })}
    </div>
  );
}

function StaticVisual({ chapter }: { chapter: Chapter }) {
  switch (chapter.stage) {
    case "services":
      return (
        <div className="grid gap-3 md:grid-cols-3 md:gap-4">
          {services.map((s) => (
            <ServiceCard key={s.messageKey} service={s} />
          ))}
        </div>
      );
    case "products":
      // Trimmed cut-outs have different aspect ratios: give each the same box and contain it.
      return (
        <div className="mx-auto grid max-w-md grid-cols-2 gap-x-6 gap-y-4 md:max-w-none md:gap-8">
          {scene.products.map((p) => (
            <div key={p.id} className="flex h-28 items-center justify-center sm:h-36 md:h-56">
              <img
                data-src={p.src}
                alt={p.alt}
                width={p.width}
                height={p.height}
                decoding="async"
                className="max-h-full max-w-full object-contain drop-shadow-[0_14px_20px_rgba(0,0,0,0.7)]"
                style={{ rotate: `${p.rotate}deg` }}
              />
            </div>
          ))}
        </div>
      );
    case "feature":
      return (
        <img
          data-src={scene.featureObject.src}
          alt={scene.featureObject.alt}
          width={scene.featureObject.width}
          height={scene.featureObject.height}
          decoding="async"
          className="mx-auto h-auto w-full max-w-[15rem] sm:max-w-xs md:max-w-lg"
        />
      );
    case "orbit":
      // the chair between two of the tools (the animated scene lets them circle it)
      return (
        <div className="mx-auto flex max-w-sm items-center justify-center gap-3 px-2 md:max-w-lg">
          {scene.products.slice(0, 2).map((p, i) => (
            <img
              key={p.id}
              data-src={p.src}
              alt=""
              width={p.width}
              height={p.height}
              decoding="async"
              className={`h-auto w-[17%] object-contain drop-shadow-[0_14px_20px_rgba(0,0,0,0.7)] ${i === 0 ? "order-first" : "order-last"}`}
              style={{ rotate: `${p.rotate}deg` }}
            />
          ))}
          <ChairImage className="h-[clamp(11rem,30svh,18rem)] w-auto md:h-[55svh]" />
        </div>
      );
    case "cta":
      return (
        <div className="relative mx-auto aspect-square w-full max-w-[17rem] sm:max-w-sm md:max-w-lg">
          <div className="absolute inset-0 rounded-full border border-gold shadow-[0_0_60px_rgba(201,164,92,0.35)]" />
          <ChairImage className="absolute inset-[8%] h-[84%] w-[84%] object-contain" />
        </div>
      );
    default: // hero: the first screen's picture (LCP on phones), loaded at once
      return <ChairImage eager className="mx-auto h-[clamp(12rem,38svh,22rem)] w-auto md:h-auto md:max-h-[70svh]" />;
  }
}

function ChairImage({ className, eager = false }: { className: string; eager?: boolean }) {
  const h = scene.heroObject;
  const srcSet = `${h.mobileSrc} 640w, ${h.src} ${h.width}w`;
  return (
    <img
      {...(eager
        ? { src: h.mobileSrc, srcSet, fetchPriority: "high" as const }
        : { "data-src": h.mobileSrc, "data-srcset": srcSet })}
      sizes="(max-width: 767px) 70vw, 40vw"
      alt={h.alt}
      width={h.width}
      height={h.height}
      decoding="async"
      className={`object-contain ${className}`}
    />
  );
}

/**
 * Light entrance: [data-reveal] blocks that are below the first screen start slightly lower
 * and transparent, and settle when they scroll into view (CSS .reveal-pending / .is-revealed,
 * transform + opacity). Anything already visible is never hidden; reduced motion: nothing moves.
 */
function useReveal(rootRef: React.RefObject<HTMLElement | null>) {
  useEffect(() => {
    const root = rootRef.current;
    if (!root || window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    const blocks = [...root.querySelectorAll<HTMLElement>("[data-reveal]")].filter(
      (el) => el.getBoundingClientRect().top > window.innerHeight,
    );
    blocks.forEach((el) => el.classList.add("reveal-pending"));
    const io = new IntersectionObserver(
      (entries) => {
        for (const e of entries) {
          if (!e.isIntersecting) continue;
          e.target.classList.add("is-revealed");
          io.unobserve(e.target);
        }
      },
      { rootMargin: "0px 0px -8% 0px" },
    );
    blocks.forEach((el) => io.observe(el));
    return () => io.disconnect();
  }, [rootRef]);
}

/**
 * The nav switches from the dark scene look to the page look once the content after the scene
 * ([data-after-scene], the footer) is under it — the pinned scene does this with ScrollTrigger.
 */
function useNavSurface() {
  useEffect(() => {
    const after = document.querySelector("[data-after-scene]");
    if (!after) return;
    let io: IntersectionObserver | null = null;
    let over: boolean | null = null;
    const observe = () => {
      io?.disconnect();
      // the root is the strip under the nav: intersecting = the footer is behind the nav
      const navBottom = after.ownerDocument.querySelector("header")?.getBoundingClientRect().bottom ?? 72;
      io = new IntersectionObserver(
        ([e]) => {
          if (e.isIntersecting === over) return;
          over = e.isIntersecting;
          window.dispatchEvent(new CustomEvent(NAV_SURFACE_EVENT, { detail: over }));
        },
        { rootMargin: `0px 0px -${Math.max(0, window.innerHeight - Math.round(navBottom))}px 0px` },
      );
      io.observe(after);
    };
    observe();
    window.addEventListener("resize", observe);
    return () => {
      window.removeEventListener("resize", observe);
      io?.disconnect();
    };
  }, []);
}
