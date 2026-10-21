"use client";

import { siteConfig } from "@/config/site.config";
import { isLiteScene } from "./sceneMode";

/**
 * Frame loading for the chair turntable (image sequence from
 * scripts/assets/turntable.py). Shared by the preloader (eager frames) and the
 * canvas (everything else), so each frame is fetched and decoded only once.
 *
 * Light scene variant (phones, touch; lib/sceneMode): every 4th frame of a set of at most
 * 540 px, kept as decoded ImageBitmaps (~13 MB on phones). An <img> may lose its decoded
 * pixels again, and drawing it then decodes on the main thread — in the middle of a swipe.
 */

const tt = siteConfig.scene.turntable;
const manifest = tt.manifest;

export type FrameSet = (typeof manifest.sets)[number];
/** A decoded frame; `src` identifies it (the canvas redraws when it changes). */
export type Frame = { src: string; image: CanvasImageSource };
export const turntableFrameCount = manifest.frames;
export const turntableCrossfade = manifest.crossfade;

const loaded = new Map<string, Frame>();
const pending = new Map<string, Promise<void>>();
/** Frames that failed (404 / decode error) are never requested again. */
const failed = new Set<string>();

/** Light variant: frames used (a multiple of every set's step). */
const LITE_STEP = 4;
const LITE_MAX_SET_HEIGHT = 540;

/**
 * Sets with "step" (the phone set) only contain every n-th frame (+ the last one);
 * the light variant uses every 4th.
 */
function stepOf(set: FrameSet) {
  const step = "step" in set && typeof set.step === "number" ? set.step : 1;
  return isLiteScene() ? Math.max(step, LITE_STEP) : step;
}

/** The frame of the set that is actually on disk for this index (the nearest one for stepped sets). */
function snap(set: FrameSet, index: number) {
  const step = stepOf(set);
  const last = turntableFrameCount - 1;
  if (step === 1 || index >= last) return Math.min(index, last);
  return Math.min(Math.round(index / step) * step, last);
}

export function frameUrl(set: FrameSet, index: number) {
  return `${tt.base}/${set.dir}/${String(snap(set, index)).padStart(3, "0")}.webp`;
}

/** Phones / small screens: lighter assets (fewer frames, lower pixel density for the moving chair). */
export function isSmallScreen() {
  return window.innerWidth < 768;
}

/**
 * Smallest set that still covers the needed device pixels (>= 90%),
 * else the largest. Phones get the small set (density capped at 1.5x for the
 * moving chair, also on touch tablets: light scene variant), desktops the big one.
 */
export function pickSet(cssHeight: number): FrameSet {
  const need = cssHeight * Math.min(window.devicePixelRatio || 1, isLiteScene() ? 1.5 : 2);
  const sets = isLiteScene() ? manifest.sets.filter((s) => s.height <= LITE_MAX_SET_HEIGHT) : manifest.sets;
  const asc = [...sets].sort((a, b) => a.height - b.height);
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
    .then(async () => {
      const image = isLiteScene() && "createImageBitmap" in window ? await createImageBitmap(img) : img;
      loaded.set(url, { src: url, image });
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
export function getFrame(set: FrameSet, index: number): Frame | null {
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

/** Frame indexes of a set in coarse-to-fine order (stepped sets: only the frames on disk). */
function setOrder(set: FrameSet) {
  const seen = new Set<string>();
  return loadOrder().filter((i) => {
    const url = frameUrl(set, i);
    if (seen.has(url)) return false;
    seen.add(url);
    return true;
  });
}

/** Frames the preloader waits for (coarse coverage of the whole turn); fewer on phones. */
export function preloadEagerFrames(set: FrameSet) {
  const count = isSmallScreen() ? tt.eagerFramesSmall : tt.eagerFrames;
  return setOrder(set).slice(0, count).map((i) => loadFrame(set, i));
}

/** Stream in every remaining frame, a few at a time, without blocking. */
export function loadAllFrames(set: FrameSet, concurrency = 4) {
  const queue = setOrder(set).filter((i) => !isLoaded(set, i) && !isFailed(set, i));
  const next = (): Promise<void> | void => {
    const i = queue.shift();
    if (i === undefined) return;
    return loadFrame(set, i).then(next);
  };
  for (let k = 0; k < concurrency; k++) next();
}
