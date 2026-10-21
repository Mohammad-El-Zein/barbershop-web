"use client";

/** Frames looked at together (~1 s of scrolling at 60 fps). */
const WINDOW = 60;
/** This many frames over 50 ms within the window = the scene keeps stuttering (≈ 1 in 5). */
const MAX_SLOW = 12;
const SLOW_MS = 50;
/** Longer gaps are a hidden tab or a paused page, not a slow frame. */
const IGNORE_MS = 500;
/** The loop stops this long after the last scroll event (nothing runs while the page rests). */
const IDLE_MS = 300;

/**
 * Watchdog for the pinned scene: measures frame times only while the page is scrolled and calls
 * {@code onStutter} once when the scene stutters persistently — ≥ 12 of the last 60 scroll
 * frames over 50 ms. A single slow frame (image decode, GC) never counts as stuttering.
 * Returns the stop function.
 */
export function watchSceneFrames(onStutter: () => void): () => void {
  const frames: number[] = [];
  let raf = 0;
  let last = 0;
  let lastScroll = 0;
  let stopped = false;

  const stop = () => {
    stopped = true;
    cancelAnimationFrame(raf);
    window.removeEventListener("scroll", onScroll);
  };

  function tick(now: number) {
    raf = 0;
    if (stopped) return;
    const d = now - last;
    last = now;
    if (d < IGNORE_MS) {
      frames.push(d);
      if (frames.length > WINDOW) frames.shift();
      if (frames.filter((f) => f > SLOW_MS).length >= MAX_SLOW) {
        stop();
        onStutter();
        return;
      }
    }
    if (now - lastScroll < IDLE_MS) raf = requestAnimationFrame(tick);
  }

  function onScroll() {
    lastScroll = performance.now();
    if (raf || stopped) return;
    last = lastScroll;
    raf = requestAnimationFrame(tick);
  }

  window.addEventListener("scroll", onScroll, { passive: true });
  return stop;
}
