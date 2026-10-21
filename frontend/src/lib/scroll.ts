"use client";

import type Lenis from "lenis";

/**
 * Tiny module-level scroll controller shared by the nav, buttons, preloader
 * and the pinned scene — avoids threading a React context through everything.
 */

let lenis: Lenis | null = null;
let locked = true; // the page starts locked behind the preloader
/** The pinned scene registers a resolver so "#services" can jump to a chapter inside the pin. */
let chapterResolver: ((anchor: string) => number | null) | null = null;
/** The pinned scene is mounted but its timeline not built yet: chapter links wait for it. */
let resolverExpected = false;
let pendingAnchor: string | null = null;

export function setLenis(instance: Lenis | null) {
  lenis = instance;
  if (lenis && locked) lenis.stop();
}

export function getLenis() {
  return lenis;
}

export function lockScroll() {
  locked = true;
  document.documentElement.classList.add("is-locked");
  lenis?.stop();
}

export function unlockScroll() {
  locked = false;
  document.documentElement.classList.remove("is-locked");
  lenis?.start();
}

/** Set by the pinned scene while it is mounted (its timeline is built a little later). */
export function expectChapterResolver(expected: boolean) {
  resolverExpected = expected;
  if (!expected) pendingAnchor = null;
}

export function registerChapterResolver(fn: typeof chapterResolver) {
  chapterResolver = fn;
  // a chapter link tapped before the timeline existed: go there now
  const anchor = pendingAnchor;
  pendingAnchor = null;
  const y = anchor && fn ? fn(anchor) : null;
  if (y != null) scrollToY(y);
  return () => {
    if (chapterResolver === fn) chapterResolver = null;
  };
}

/** Scroll smoothly to an absolute Y position. */
export function scrollToY(y: number, opts: { immediate?: boolean } = {}) {
  if (lenis) {
    lenis.scrollTo(y, { duration: opts.immediate ? 0 : 1.6, immediate: opts.immediate, force: true });
  } else {
    window.scrollTo({ top: y, behavior: opts.immediate ? "auto" : "smooth" });
  }
}

/**
 * Scroll to "#anchor": a pinned chapter if the scene knows it, otherwise
 * a regular element with that id. Returns true if it handled the navigation.
 */
export function scrollToTarget(href: string): boolean {
  if (!href.startsWith("#")) return false;
  const anchor = href.slice(1);
  const chapterY = chapterResolver?.(anchor);
  if (chapterY != null) {
    scrollToY(chapterY);
    return true;
  }
  const el = anchor ? document.getElementById(anchor) : document.body;
  if (!el && resolverExpected && !chapterResolver) {
    pendingAnchor = anchor; // most likely a chapter of the scene that is still being built
    return true;
  }
  if (!el) return false;
  const y = el.getBoundingClientRect().top + window.scrollY - 72; // nav height
  scrollToY(Math.max(0, y));
  return true;
}
