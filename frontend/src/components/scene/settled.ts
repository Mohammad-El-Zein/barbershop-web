import type { Chapter } from "@/config/site.config";

/** Length of a chapter transition, as a fraction of the total pinned scroll. */
export const T = 0.045;

/**
 * Scroll fraction at which a chapter's text is fully revealed — used by
 * the section dots and nav links to land on a "settled" frame.
 * (Its own module: the scene renders the dots before GSAP and the timeline have loaded.)
 */
export function settledProgress(ch: Chapter, index: number) {
  return index === 0 ? 0 : Math.min(ch.range[1] - 0.001, ch.range[0] + T * 1.7);
}
