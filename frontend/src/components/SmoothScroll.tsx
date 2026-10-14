"use client";

import { useEffect } from "react";
import Lenis from "lenis";
import { gsap, ScrollTrigger } from "@/lib/gsap";
import { scrollToTarget, setLenis } from "@/lib/scroll";
import { useReducedMotion } from "@/lib/useReducedMotion";

/**
 * Lenis smooth scrolling, driven by GSAP's ticker so Lenis and ScrollTrigger
 * update in the same frame (no jitter between smoothed scroll and pinned scene).
 * Disabled entirely for prefers-reduced-motion users (native scrolling).
 */
export function SmoothScroll() {
  const reduced = useReducedMotion();

  useEffect(() => {
    if (reduced) return;

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

    return () => {
      gsap.ticker.remove(tick);
      setLenis(null);
      lenis.destroy();
    };
  }, [reduced]);

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
