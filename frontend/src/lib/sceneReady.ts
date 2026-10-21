"use client";

import { isLiteScene } from "./sceneMode";

/**
 * "The landing scene's first screen is in" — fired by the preloader.
 *
 * Until then non-critical scene images (products, feature object, service photos)
 * only carry data-src, so they don't compete with the chair poster and backdrop
 * (the LCP candidates) for bandwidth on slow phones. markSceneReady() copies
 * data-src → src directly in the DOM: no React re-render (a state update here
 * re-rendered the whole scene in one long task).
 */
const EVENT = "scene:ready";
let ready = false;

function loadDeferredImages() {
  for (const img of document.querySelectorAll<HTMLImageElement>("img[data-src]")) {
    if (img.getAttribute("src")) continue;
    if (img.dataset.srcset) img.srcset = img.dataset.srcset; // before src: the browser picks once
    img.src = img.dataset.src!;
    // decoded now, not in the middle of the first swipe
    img.decode().catch(() => undefined);
  }
}

const INTERACT = ["scroll", "wheel", "touchstart", "pointerdown", "keydown"] as const;

/**
 * Phones and touch devices (no preloader there): runs `fn` once the first screen is in — after
 * the load event, in an idle moment (at most 1.5 s later) — or at the first touch / scroll,
 * whichever comes first. So the scene's images and chair frames are loaded and decoded before
 * the scene needs them, without competing with the first screen (LCP).
 */
export function afterFirstScreen(fn: () => void) {
  let done = false;
  let timer = 0;
  const go = () => {
    if (done) return;
    done = true;
    INTERACT.forEach((e) => window.removeEventListener(e, go));
    window.removeEventListener("load", onLoad);
    window.clearTimeout(timer);
    fn();
  };
  const idle = () => (window.requestIdleCallback ? window.requestIdleCallback(go, { timeout: 1500 }) : (timer = window.setTimeout(go, 200)));
  const onLoad = () => idle();
  INTERACT.forEach((e) => window.addEventListener(e, go, { passive: true, once: true }));
  if (document.readyState === "complete") idle();
  else window.addEventListener("load", onLoad, { once: true });
}

let deferredStarted = false;

function startDeferredImages() {
  deferredStarted = true;
  loadDeferredImages();
}

export function markSceneReady() {
  if (ready) return;
  ready = true;
  if (isLiteScene()) afterFirstScreen(startDeferredImages);
  else startDeferredImages();
  window.dispatchEvent(new Event(EVENT));
}

/**
 * A scene mounted after that (the static one, after hydration or as the fallback) loads its
 * deferred images the same way; before the first screen is in, they simply wait for it.
 */
export function loadLateSceneImages() {
  if (deferredStarted) loadDeferredImages();
}

/** Only markSceneReady counts: on phones the preloader element is gone long before (no overlay). */
export function isSceneReady() {
  return ready;
}

/** Runs `fn` once the scene is ready (immediately if it already is). */
export function whenSceneReady(fn: () => void) {
  if (isSceneReady()) {
    fn();
    return () => undefined;
  }
  const once = () => {
    window.removeEventListener(EVENT, once);
    fn();
  };
  window.addEventListener(EVENT, once);
  return () => window.removeEventListener(EVENT, once);
}
