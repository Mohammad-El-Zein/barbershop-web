"use client";

import { useSyncExternalStore } from "react";

/**
 * Which landing scene a device gets:
 * - "pinned": the scroll-scrubbed GSAP scene (pin, turntable canvas, Lenis) — desktops only;
 * - "static": stacked sections with light reveal effects and native scrolling — phones,
 *   small or touch tablets, landscape phones, prefers-reduced-motion, weak devices, and any
 *   device whose frame rate collapses while the preloader runs ({@link downgradeScene}).
 *
 * The server renders "static" (fast first paint, no layout jump on phones); a desktop switches
 * to "pinned" right after hydration, under the preloader. ?scene=static|pinned forces a mode
 * (testing and support).
 */
export type SceneMode = "pinned" | "static";

/** Keep in sync with the [data-preloader] rule in globals.css (no overlay where the scene is static). */
export const STATIC_SCENE_QUERY =
  "(max-width: 767px), (max-height: 500px), (prefers-reduced-motion: reduce), (pointer: coarse) and (max-width: 1024px)";

/** Element (rendered by Experience) that reserves the pinned scroll length until the pin spacer exists. */
export const PIN_PLACEHOLDER_ID = "scene-pin-placeholder";

let downgraded = false;
const listeners = new Set<() => void>();

/** 2 cores, ≤ 2 GB memory (Chromium only) or the data saver: not worth the heavy scene. */
function weakDevice() {
  const n = navigator as Navigator & { deviceMemory?: number; connection?: { saveData?: boolean } };
  return (n.hardwareConcurrency ?? 8) <= 2 || (n.deviceMemory ?? 8) <= 2 || n.connection?.saveData === true;
}

export function currentSceneMode(): SceneMode {
  const forced = new URLSearchParams(window.location.search).get("scene");
  if (forced === "static" || forced === "pinned") return forced;
  if (downgraded || window.matchMedia(STATIC_SCENE_QUERY).matches || weakDevice()) return "static";
  return "pinned";
}

/** The safe fallback: switch to the static scene for the rest of the visit (never back). */
export function downgradeScene() {
  if (downgraded) return;
  downgraded = true;
  listeners.forEach((l) => l());
}

function subscribe(cb: () => void) {
  const mql = window.matchMedia(STATIC_SCENE_QUERY);
  mql.addEventListener("change", cb);
  listeners.add(cb);
  return () => {
    mql.removeEventListener("change", cb);
    listeners.delete(cb);
  };
}

/** {@code serverMode}: what the server HTML assumed (the scene: "static"; the preloader overlay: "pinned"). */
export function useSceneMode(serverMode: SceneMode = "static"): SceneMode {
  return useSyncExternalStore(subscribe, currentSceneMode, () => serverMode);
}

// ── the pinned scene tells the preloader when its images are in the DOM (it loads lazily) ──

let mountedResolve: () => void = () => undefined;
const pinnedMounted = new Promise<void>((r) => (mountedResolve = r));

export function notifyPinnedSceneMounted() {
  mountedResolve();
}

export function whenPinnedSceneMounted() {
  return pinnedMounted;
}
