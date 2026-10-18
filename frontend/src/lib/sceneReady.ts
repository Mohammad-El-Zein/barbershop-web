"use client";

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

export function markSceneReady() {
  if (ready) return;
  ready = true;
  for (const img of document.querySelectorAll<HTMLImageElement>("img[data-src]")) {
    if (!img.getAttribute("src")) img.src = img.dataset.src!;
  }
  window.dispatchEvent(new Event(EVENT));
}

export function isSceneReady() {
  return ready || !document.querySelector("[data-preloader]");
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
