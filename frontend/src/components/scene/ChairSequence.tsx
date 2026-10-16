"use client";

import { useEffect, useRef, type RefObject } from "react";
import {
  estimateChairCssHeight,
  getFrame,
  isFailed,
  isLoaded,
  loadAllFrames,
  loadFrame,
  pickSet,
  turntableCrossfade,
  turntableFrameCount,
  type FrameSet,
} from "@/lib/turntable";

export type TurntableController = {
  /** Fractional frame index (0 .. frameCount-1), driven by the scroll timeline. */
  setFrame: (frame: number) => void;
};

/** Per-frame easing toward the target frame (on top of ScrollTrigger's scrub smoothing). */
const LERP = 0.3;

/**
 * Scroll-driven chair rotation: draws the turntable sequence onto a canvas.
 *
 * - the timeline sets a fractional target frame; a rAF loop eases toward it
 *   (lerp), so the rotation never steps even when scroll input is coarse
 * - manifest.crossfade (30-frame fallback sequences): blends the two nearest
 *   frames; with interpolated sequences the nearest frame is drawn
 * - device pixels: canvas = CSS size × min(DPR, 2); the frame set is chosen to
 *   match (phones load the small set)
 * - no flicker: the canvas is only cleared when a decoded frame is ready to draw;
 *   onReady fires after the first draw so the poster <img> can hide
 */
export function ChairSequence({
  controlRef,
  onReady,
}: {
  controlRef: RefObject<TurntableController | null>;
  onReady: () => void;
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const onReadyRef = useRef(onReady);
  useEffect(() => {
    onReadyRef.current = onReady;
  }, [onReady]);

  useEffect(() => {
    const canvas = canvasRef.current!;
    const ctx = canvas.getContext("2d")!;
    const last = turntableFrameCount - 1;
    let set: FrameSet = pickSet(canvas.clientHeight || estimateChairCssHeight());
    let target = 0;
    let current = 0;
    let drawnKey = "";
    let raf = 0;
    let ready = false;

    const request = () => {
      if (!raf) raf = requestAnimationFrame(draw);
    };

    function draw() {
      raf = 0;
      current += (target - current) * LERP;
      if (Math.abs(target - current) < 0.002) current = target;

      const i0 = turntableCrossfade ? Math.floor(current) : Math.round(current);
      const i1 = Math.min(last, i0 + 1);
      const t = turntableCrossfade ? current - i0 : 0;
      const a = getFrame(set, i0);
      if (!a) {
        // nothing decoded yet: wait for this frame (never retry a failed one —
        // the poster image simply stays visible)
        if (!isFailed(set, i0)) loadFrame(set, i0).then(request);
        return;
      }
      const b = t > 0.01 ? getFrame(set, i1) : null;
      const key = `${set.dir}|${a.src}|${b ? `${b.src}|${t.toFixed(2)}` : ""}|${canvas.width}x${canvas.height}`;
      if (key !== drawnKey) {
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        ctx.globalAlpha = 1;
        ctx.drawImage(a, 0, 0, canvas.width, canvas.height);
        if (b && b !== a) {
          ctx.globalAlpha = t;
          ctx.drawImage(b, 0, 0, canvas.width, canvas.height);
          ctx.globalAlpha = 1;
        }
        drawnKey = key;
        canvas.dataset.frame = String(i0); // inspected by the automated scroll tests
        if (!ready) {
          ready = true;
          onReadyRef.current();
        }
      }
      // keep easing; also redraw once the exact frame has streamed in
      if (current !== target) request();
      else if (!isLoaded(set, i0) && !isFailed(set, i0)) loadFrame(set, i0).then(request);
    }

    const resize = () => {
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      const w = Math.round(canvas.clientWidth * dpr);
      const h = Math.round(canvas.clientHeight * dpr);
      if (!w || !h) return;
      if (w !== canvas.width || h !== canvas.height) {
        canvas.width = w;
        canvas.height = h;
        drawnKey = "";
      }
      const next = pickSet(canvas.clientHeight);
      if (next.dir !== set.dir) {
        set = next;
        drawnKey = "";
        loadAllFrames(set);
      }
      request();
    };
    const ro = new ResizeObserver(resize);
    ro.observe(canvas);
    resize();

    controlRef.current = {
      setFrame(frame) {
        target = Math.min(last, Math.max(0, frame));
        request();
      },
    };

    // first frame now, the rest when the browser is idle
    loadFrame(set, 0).then(request);
    const idle = window.requestIdleCallback ?? ((cb: () => void) => window.setTimeout(cb, 200));
    const idleId = idle(() => loadAllFrames(set));

    return () => {
      ro.disconnect();
      cancelAnimationFrame(raf);
      window.cancelIdleCallback?.(idleId as number);
      controlRef.current = null;
    };
  }, [controlRef]);

  return <canvas ref={canvasRef} aria-hidden="true" data-turntable className="absolute inset-0 h-full w-full" />;
}
