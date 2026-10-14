"use client";

import { useRef } from "react";
import { gsap, ScrollTrigger, useGSAP } from "@/lib/gsap";

/** Thin gold page-progress bar at the very top (scaleX only, no layout work). */
export function ScrollProgress() {
  const barRef = useRef<HTMLDivElement>(null);

  useGSAP(() => {
    const setScale = gsap.quickSetter(barRef.current, "scaleX");
    const st = ScrollTrigger.create({
      start: 0,
      end: "max",
      onUpdate: (self) => setScale(self.progress),
    });
    return () => st.kill();
  });

  return (
    <div className="pointer-events-none fixed inset-x-0 top-0 z-[70] h-[2px] bg-white/5" aria-hidden="true">
      <div
        ref={barRef}
        className="h-full origin-left bg-gradient-to-r from-gold-deep via-gold to-[#f1d796] shadow-[0_0_10px_rgba(201,164,92,0.8)]"
        style={{ transform: "scaleX(0)" }}
      />
    </div>
  );
}
