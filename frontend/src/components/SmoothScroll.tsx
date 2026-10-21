"use client";

import { useEffect } from "react";
import { useSceneMode } from "@/lib/sceneMode";
import { scrollToTarget, setLenis } from "@/lib/scroll";

/**
 * Lenis smooth scrolling for the pinned scene only (desktops), driven by GSAP's ticker so
 * Lenis and ScrollTrigger update in the same frame (no jitter between smoothed scroll and
 * pinned scene). Lenis and GSAP are loaded on demand: the static scene (phones, reduced
 * motion, weak devices) scrolls natively and never downloads them.
 */
export function SmoothScroll() {
  const mode = useSceneMode();

  useEffect(() => {
    if (mode !== "pinned") return;
    let cleanup: (() => void) | undefined;
    let cancelled = false;

    Promise.all([import("lenis"), import("@/lib/gsap")]).then(([{ default: Lenis }, { gsap, ScrollTrigger }]) => {
      if (cancelled) return;
      const lenis = new Lenis({
        lerp: 0.1,
        wheelMultiplier: 1,
        autoRaf: false, // we drive it from gsap.ticker below
      });
      lenis.on("scroll", ScrollTrigger.update);
      const tick = (time: number) => lenis.raf(time * 1000);
      gsap.ticker.add(tick);
      gsap.ticker.lagSmoothing(0);
      setLenis(lenis);
      cleanup = () => {
        gsap.ticker.remove(tick);
        setLenis(null);
        lenis.destroy();
      };
    });

    return () => {
      cancelled = true;
      cleanup?.();
    };
  }, [mode]);

  // Any in-page "#anchor" link (footer etc.) goes through the scroll controller,
  // so anchors that point at chapters inside the pinned scene work too.
  useEffect(() => {
    const onClick = (e: MouseEvent) => {
      if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey) return;
      const link = (e.target as Element).closest?.("a[href^=\"#\"]");
      if (link && scrollToTarget(link.getAttribute("href")!)) e.preventDefault();
    };
    document.addEventListener("click", onClick);
    return () => document.removeEventListener("click", onClick);
  }, []);

  return null;
}
