"use client";

import { siteConfig } from "@/config/site.config";

/**
 * Frame cache for the optional canvas image sequence (technique A).
 * Frames are 1-based on disk (frame_0001.webp) and 0-based in code.
 */

const cfg = siteConfig.scene.frames;
const loaded = new Map<number, HTMLImageElement>();
const pending = new Map<number, Promise<void>>();

export const frameCount = cfg.count;

export function frameUrl(index: number) {
  return `${cfg.path}${String(index + 1).padStart(cfg.pad, "0")}.${cfg.ext}`;
}

export function loadFrame(index: number): Promise<void> {
  if (loaded.has(index)) return Promise.resolve();
  const existing = pending.get(index);
  if (existing) return existing;
  const img = new Image();
  img.decoding = "async";
  img.src = frameUrl(index);
  const p = img
    .decode()
    .then(() => {
      loaded.set(index, img);
    })
    .catch(() => {
      // Missing/broken frame: ignore, the canvas falls back to the nearest loaded one.
    })
    .finally(() => pending.delete(index));
  pending.set(index, p);
  return p;
}

/** Returns the requested frame, or the closest already-loaded one while it streams in. */
export function getFrame(index: number): HTMLImageElement | null {
  const exact = loaded.get(index);
  if (exact) return exact;
  for (let d = 1; d < frameCount; d++) {
    const before = loaded.get(index - d);
    if (before) return before;
    const after = loaded.get(index + d);
    if (after) return after;
  }
  return null;
}

/**
 * Load the remaining frames in small batches when the browser is idle,
 * so they never compete with the critical assets or the main thread.
 */
export function loadRemainingFramesLazily(start: number, batchSize = 6) {
  let next = start;
  const idle =
    window.requestIdleCallback ?? ((cb: () => void) => window.setTimeout(cb, 50) as unknown as number);

  const step = () => {
    if (next >= frameCount) return;
    const batch = [];
    for (let i = 0; i < batchSize && next < frameCount; i++, next++) batch.push(loadFrame(next));
    Promise.all(batch).then(() => idle(step));
  };
  idle(step);
}
