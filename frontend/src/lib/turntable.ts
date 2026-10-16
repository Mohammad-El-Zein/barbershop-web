"use client";

import { siteConfig } from "@/config/site.config";

/**
 * Frame loading for the chair turntable (image sequence from
 * scripts/assets/turntable.py). Shared by the preloader (eager frames) and the
 * canvas (everything else), so each frame is fetched and decoded only once.
 */

const tt = siteConfig.scene.turntable;
const manifest = tt.manifest;

export type FrameSet = (typeof manifest.sets)[number];
export const turntableFrameCount = manifest.frames;
export const turntableCrossfade = manifest.crossfade;

const loaded = new Map<string, HTMLImageElement>();
const pending = new Map<string, Promise<void>>();
/** Frames that failed (404 / decode error) are never requested again. */
const failed = new Set<string>();

export function frameUrl(set: FrameSet, index: number) {
  return `${tt.base}/${set.dir}/${String(index).padStart(3, "0")}.webp`;
}

/**
 * Smallest set that still covers the needed device pixels (>= 90%),
 * else the largest. Phones get the small set, desktops the big one.
 */
export function pickSet(cssHeight: number): FrameSet {
  const need = cssHeight * Math.min(window.devicePixelRatio || 1, 2);
  const asc = [...manifest.sets].sort((a, b) => a.height - b.height);
  return asc.find((s) => s.height >= need * 0.9) ?? asc[asc.length - 1];
}

/** Rough on-screen chair height before layout exists (mirrors PinnedScene's stage box). */
export function estimateChairCssHeight() {
  const vh = window.innerHeight;
  const box = window.innerWidth < 768 ? vh * 0.49 - 72 : vh * 0.92 - 72;
  return Math.max(200, box * 0.86);
}

export function loadFrame(set: FrameSet, index: number): Promise<void> {
  const url = frameUrl(set, index);
  if (loaded.has(url) || failed.has(url)) return Promise.resolve();
  const existing = pending.get(url);
  if (existing) return existing;
  const img = new Image();
  img.decoding = "async";
  img.src = url;
  const p = img
    .decode()
    .then(() => {
      loaded.set(url, img);
    })
    .catch(() => {
      // missing frame: remembered so it is not retried; the canvas falls back
      // to the nearest loaded frame (or keeps the poster image)
      failed.add(url);
    })
    .finally(() => pending.delete(url));
  pending.set(url, p);
  return p;
}

/** Exact frame if loaded, else the nearest loaded neighbour (null if none yet). */
export function getFrame(set: FrameSet, index: number): HTMLImageElement | null {
  const exact = loaded.get(frameUrl(set, index));
  if (exact) return exact;
  for (let d = 1; d < turntableFrameCount; d++) {
    const a = loaded.get(frameUrl(set, index - d));
    if (a) return a;
    const b = loaded.get(frameUrl(set, index + d));
    if (b) return b;
  }
  return null;
}

export function isLoaded(set: FrameSet, index: number) {
  return loaded.has(frameUrl(set, index));
}

export function isFailed(set: FrameSet, index: number) {
  return failed.has(frameUrl(set, index));
}

/**
 * Coarse-to-fine order: first/last frame, then the middle, then quarters...
 * After a few frames the whole rotation is covered roughly, so fast scrolling
 * never shows a frozen chair while the rest streams in.
 */
export function loadOrder(n = turntableFrameCount) {
  const order: number[] = [];
  const seen = new Set<number>();
  const add = (i: number) => {
    if (i >= 0 && i < n && !seen.has(i)) {
      seen.add(i);
      order.push(i);
    }
  };
  add(0);
  add(n - 1);
  for (let step = 2 ** Math.ceil(Math.log2(n)); step >= 1; step /= 2) {
    for (let i = 0; i < n; i += step) add(i);
  }
  return order;
}

/** Frames the preloader waits for (coarse coverage of the whole turn). */
export function preloadEagerFrames(set: FrameSet) {
  return loadOrder().slice(0, tt.eagerFrames).map((i) => loadFrame(set, i));
}

/** Stream in every remaining frame, a few at a time, without blocking. */
export function loadAllFrames(set: FrameSet, concurrency = 4) {
  const queue = loadOrder().filter((i) => !isLoaded(set, i) && !isFailed(set, i));
  const next = (): Promise<void> | void => {
    const i = queue.shift();
    if (i === undefined) return;
    return loadFrame(set, i).then(next);
  };
  for (let k = 0; k < concurrency; k++) next();
}
