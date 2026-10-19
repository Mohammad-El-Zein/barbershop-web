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

function loadDeferredImages() {
  for (const img of document.querySelectorAll<HTMLImageElement>("img[data-src]")) {
    if (!img.getAttribute("src")) img.src = img.dataset.src!;
  }
}

/**
 * Phones (lighter variant): the deferred images only start on the first interaction —
 * they appear further down the scroll scene, the first screen doesn't need them.
 */
function loadDeferredImagesOnFirstInteraction() {
  const events = ["scroll", "wheel", "touchstart", "pointerdown", "keydown"] as const;
  const go = () => {
    events.forEach((e) => window.removeEventListener(e, go));
    loadDeferredImages();
  };
  events.forEach((e) => window.addEventListener(e, go, { passive: true, once: true }));
}

export function markSceneReady() {
  if (ready) return;
  ready = true;
  if (window.innerWidth < 768) loadDeferredImagesOnFirstInteraction();
  else loadDeferredImages();
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
