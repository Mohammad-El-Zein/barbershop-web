"use client";

import { useEffect, useRef } from "react";

/**
 * Thin gold page-progress bar at the very top (scaleX only, no layout work). A passive scroll
 * listener throttled to one update per frame — no GSAP, so phones don't load it for this.
 */
export function ScrollProgress() {
  const barRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let frame = 0;
    const update = () => {
      frame = 0;
      const max = document.documentElement.scrollHeight - window.innerHeight;
      const p = max > 0 ? Math.min(1, window.scrollY / max) : 0;
      if (barRef.current) barRef.current.style.transform = `scaleX(${p})`;
    };
    const onScroll = () => {
      if (!frame) frame = requestAnimationFrame(update);
    };
    window.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("resize", onScroll, { passive: true });
    update();
    return () => {
      window.removeEventListener("scroll", onScroll);
      window.removeEventListener("resize", onScroll);
      cancelAnimationFrame(frame);
    };
  }, []);

  return (
    <div className="pointer-events-none fixed inset-x-0 top-[var(--demo-bar)] z-[70] h-[2px] bg-white/5" aria-hidden="true">
      <div
        ref={barRef}
        className="h-full origin-left bg-gradient-to-r from-gold-deep via-gold to-[#f1d796] shadow-[0_0_10px_rgba(201,164,92,0.8)]"
        style={{ transform: "scaleX(0)" }}
      />
    </div>
  );
}
