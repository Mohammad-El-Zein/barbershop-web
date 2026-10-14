"use client";

import { useEffect, useRef, type RefObject } from "react";
import { frameCount, getFrame } from "@/lib/frames";

export type FrameController = { setProgress: (p: number) => void };

/**
 * Technique A (optional): draws frame = progress × (count − 1) of a preloaded
 * image sequence onto a canvas, "object-fit: cover" style. Only redraws when
 * the frame index actually changes, inside requestAnimationFrame.
 */
export function FrameCanvas({ controlRef }: { controlRef: RefObject<FrameController | null> }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current!;
    const ctx = canvas.getContext("2d", { alpha: false })!;
    let target = 0; // frame we want
    let drawn = -1; // frame currently on the canvas
    let drawnImg: HTMLImageElement | null = null;
    let raf = 0;

    const draw = () => {
      raf = 0;
      const img = getFrame(target);
      if (!img || (target === drawn && img === drawnImg)) return;
      const cw = canvas.width;
      const ch = canvas.height;
      // cover-fit
      const scale = Math.max(cw / img.naturalWidth, ch / img.naturalHeight);
      const w = img.naturalWidth * scale;
      const h = img.naturalHeight * scale;
      ctx.drawImage(img, (cw - w) / 2, (ch - h) / 2, w, h);
      drawn = target;
      drawnImg = img;
      // If we drew a fallback neighbour, try again next frame (the real one may have arrived).
      if (getFrame(target) !== img) request();
    };
    const request = () => {
      if (!raf) raf = requestAnimationFrame(draw);
    };

    const resize = () => {
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      canvas.width = Math.round(canvas.clientWidth * dpr);
      canvas.height = Math.round(canvas.clientHeight * dpr);
      drawn = -1; // force redraw after resize (canvas was cleared)
      request();
    };
    const ro = new ResizeObserver(resize);
    ro.observe(canvas);

    controlRef.current = {
      setProgress(p) {
        const next = Math.min(frameCount - 1, Math.max(0, Math.round(p * (frameCount - 1))));
        if (next !== target) {
          target = next;
          request();
        }
      },
    };
    // Frames may still be decoding on first mount; poll briefly until frame 0 paints.
    const warmup = window.setInterval(() => (drawn === -1 ? request() : window.clearInterval(warmup)), 150);

    return () => {
      ro.disconnect();
      cancelAnimationFrame(raf);
      window.clearInterval(warmup);
      controlRef.current = null;
    };
  }, [controlRef]);

  return <canvas ref={canvasRef} aria-hidden="true" className="absolute inset-0 h-full w-full" />;
}
