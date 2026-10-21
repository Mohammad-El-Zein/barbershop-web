"use client";

import { useSyncExternalStore } from "react";
import { SCENE_AUTO_FALLBACK } from "@/config/site.config";

/**
 * Which landing scene a device gets:
 * - "pinned": the scroll-scrubbed GSAP scene — on desktops the full variant (pin, scrub
 *   smoothing, Lenis), on phones and touch devices the light one ({@link LITE_SCENE_QUERY});
 * - "static": stacked sections with light reveal effects and native scrolling — for
 *   prefers-reduced-motion; and, only when switched on (site.config SCENE_AUTO_FALLBACK, off by
 *   default, or ?fallback=on), for very weak devices and as the automatic fallback
 *   ({@link downgradeScene}): a frame rate that collapses at the start (preloader / first
 *   seconds) or a scene that keeps stuttering while it is scrolled (lib/sceneWatchdog).
 *
 * The server renders "pinned" (what almost every visitor gets). ?scene=static|pinned forces a
 * mode (testing and support); a forced mode is never downgraded.
 */
export type SceneMode = "pinned" | "static";

export const STATIC_SCENE_QUERY = "(prefers-reduced-motion: reduce)";

/**
 * Phones and touch devices: the light pinned variant — a short sticky track instead of a pin,
 * scrub without lag, native scrolling (no Lenis), no filters on moving layers, no preloader
 * overlay. Keep in sync with globals.css (.scene-track, [data-preloader]).
 */
export const LITE_SCENE_QUERY = "(max-width: 767px), (pointer: coarse)";

/** Element (rendered by Experience) that reserves the pinned scroll length until the pin spacer exists. */
export const PIN_PLACEHOLDER_ID = "scene-pin-placeholder";

let downgraded = false;
/** The chapter the pinned scene shows (reported by it); the static scene continues there after a downgrade. */
let pinnedChapter = 0;
let resumeChapter: number | null = null;
const listeners = new Set<() => void>();

/**
 * 2 cores and ≤ 2 GB memory, or the data saver. Browsers without deviceMemory (Safari,
 * Firefox) are judged by their cores alone.
 */
function weakDevice() {
  const n = navigator as Navigator & { deviceMemory?: number; connection?: { saveData?: boolean } };
  if (n.connection?.saveData === true) return true;
  return (n.hardwareConcurrency ?? 8) <= 2 && (n.deviceMemory ?? 2) <= 2;
}

function forcedScene(): SceneMode | null {
  const forced = new URLSearchParams(window.location.search).get("scene");
  return forced === "static" || forced === "pinned" ? forced : null;
}

/** ?scene=… forces the mode: no frame-rate probe, no watchdog, no downgrade. */
export function isSceneForced() {
  return forcedScene() !== null;
}

/** Is this automatic switch to the static scene on? (site.config, or ?fallback=on for one visit) */
export function autoFallback(kind: keyof typeof SCENE_AUTO_FALLBACK) {
  if (isSceneForced()) return false;
  return SCENE_AUTO_FALLBACK[kind] || new URLSearchParams(window.location.search).get("fallback") === "on";
}

export function currentSceneMode(): SceneMode {
  const forced = forcedScene();
  if (forced) return forced;
  if (downgraded || window.matchMedia(STATIC_SCENE_QUERY).matches) return "static";
  if (autoFallback("weakDevice") && weakDevice()) return "static";
  return "pinned";
}

/** A chapter jump (nav link, section dot) in progress: its target, and since when. */
let jump: { chapter: number; at: number } | null = null;
/** Longer than any smooth jump (lib/scroll: 1.6 s); after that the chapter on screen counts again. */
const JUMP_MS = 3000;

export function reportPinnedChapter(index: number) {
  pinnedChapter = index;
  if (jump?.chapter === index) jump = null;
}

/** The scene scrolls to this chapter (passing others on the way). */
export function reportChapterJump(index: number) {
  jump = { chapter: index, at: performance.now() };
}

/**
 * The safe fallback: switch to the static scene for the rest of the visit (never back), without
 * a reload; the static scene continues at the chapter that was on screen — or, in the middle of
 * a chapter jump, at the chapter the visitor asked for.
 */
export function downgradeScene() {
  if (downgraded || isSceneForced()) return;
  downgraded = true;
  resumeChapter = jump && performance.now() - jump.at < JUMP_MS ? jump.chapter : pinnedChapter;
  jump = null;
  listeners.forEach((l) => l());
}

/** The chapter the pinned scene showed when it was downgraded (once; null otherwise). */
export function takeResumeChapter() {
  const i = resumeChapter;
  resumeChapter = null;
  return i;
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

export function useSceneMode(): SceneMode {
  return useSyncExternalStore(subscribe, currentSceneMode, () => "pinned");
}

export function isLiteScene() {
  return window.matchMedia(LITE_SCENE_QUERY).matches;
}

function subscribeLite(cb: () => void) {
  const mql = window.matchMedia(LITE_SCENE_QUERY);
  mql.addEventListener("change", cb);
  return () => mql.removeEventListener("change", cb);
}

/** The server assumes a desktop (its CSS already hides the preloader on phones). */
export function useLiteScene(): boolean {
  return useSyncExternalStore(subscribeLite, isLiteScene, () => false);
}

// ── the pinned scene tells the preloader when it is in the DOM ──

let mountedResolve: () => void = () => undefined;
const pinnedMounted = new Promise<void>((r) => (mountedResolve = r));

export function notifyPinnedSceneMounted() {
  mountedResolve();
}

export function whenPinnedSceneMounted() {
  return pinnedMounted;
}
