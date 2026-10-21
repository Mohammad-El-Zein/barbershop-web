"use client";

import type { Dispatch, RefObject, SetStateAction } from "react";
import { siteConfig } from "@/config/site.config";
import { gsap, ScrollTrigger } from "@/lib/gsap";
import {
  downgradeScene,
  isSceneForced,
  LITE_SCENE_QUERY,
  PIN_PLACEHOLDER_ID,
  reportChapterJump,
  reportPinnedChapter,
} from "@/lib/sceneMode";
import { watchSceneFrames } from "@/lib/sceneWatchdog";
import { registerChapterResolver } from "@/lib/scroll";
import { turntableFrameCount } from "@/lib/turntable";
import { NAV_SURFACE_EVENT } from "../Nav";
import type { TurntableController } from "./ChairSequence";
import type { FrameController } from "./FrameCanvas";
import { settledProgress } from "./settled";
import { buildSceneTimeline } from "./timeline";

const { scene, chapters } = siteConfig;

export type SceneElements = {
  track: HTMLDivElement;
  root: HTMLElement;
  stageBox: HTMLDivElement;
  frameRef: RefObject<FrameController | null>;
  turntableRef: RefObject<TurntableController | null>;
  triggerRef: RefObject<ScrollTrigger | null>;
  activeRef: RefObject<number>;
  setActive: Dispatch<SetStateAction<number>>;
};

/**
 * Builds the scroll timeline of the pinned scene (PinnedScene loads this module, and with it
 * GSAP, only when the scene is ready — so neither costs anything during hydration).
 * Everything lives in one gsap.context; the returned function reverts it.
 */
export function buildPinnedScene(el: SceneElements): () => void {
  const ctx = gsap.context(() => {
    const mm = gsap.matchMedia();

    mm.add({ isDesktop: "(min-width: 768px)", isMobile: "(max-width: 767px)", lite: LITE_SCENE_QUERY }, (context) => {
      const { isMobile, lite } = context.conditions as { isMobile: boolean; lite: boolean };
      const { root, track } = el;

      // full: the pin spacer takes over the reserved scroll length in this same task, nothing
      // below moves; light: the sticky track has its length already (CSS hides the placeholder)
      const placeholder = document.getElementById(PIN_PLACEHOLDER_ID);
      if (placeholder) placeholder.style.display = "none";
      const onUpdate = (self: ScrollTrigger) => {
        const p = self.progress;
        el.frameRef.current?.setProgress(p);
        // Active chapter for the section dots (state only changes on chapter switch).
        const idx = chapters.findIndex((c) => p >= c.range[0] && p < c.range[1]);
        const next = idx === -1 ? chapters.length - 1 : idx;
        if (next !== el.activeRef.current) {
          el.activeRef.current = next;
          reportPinnedChapter(next);
          el.setActive(next);
        }
      };
      const tl = gsap.timeline({
        defaults: { ease: "none" },
        scrollTrigger: lite
          ? {
              // the track's extra length (scene.lite) in px: svh, so it never follows the address bar
              trigger: track,
              start: "top top",
              end: () => `+=${track.offsetHeight - root.offsetHeight}`,
              scrub: true,
              onUpdate,
            }
          : {
              trigger: root,
              start: "top top",
              end: () => `+=${(window.innerHeight * scene.scrollLengthVh) / 100}`,
              pin: true,
              scrub: scene.scrub,
              anticipatePin: 1,
              onUpdate,
            },
      });

      const { placeOrbit, syncTurntable } = buildSceneTimeline(tl, {
        root,
        stageBox: el.stageBox,
        chapters,
        products: scene.products,
        isMobile,
        lite,
        turntable: scene.turntable.enabled
          ? {
              frameCount: turntableFrameCount,
              segments: scene.turntable.segments,
              onFrame: (f) => el.turntableRef.current?.setFrame(f),
            }
          : undefined,
      });
      el.triggerRef.current = tl.scrollTrigger ?? null;
      // Light variant: GSAP sets up each tween (reading computed styles) the first time the
      // playhead reaches it — on phones that was a long task in the middle of the first
      // swipe. Run through the whole timeline once, silently, and back to where the scroll
      // is: right after this build, as a task of its own.
      const warmUp = lite
        ? window.setTimeout(() => {
            const p = tl.progress();
            tl.progress(1, true).progress(p, true);
            syncTurntable();
          }, 0)
        : 0;

      // Nav surface: "over the page" once the content after the scene (footer) is under it.
      const after = document.querySelector("[data-after-scene]");
      if (after) {
        ScrollTrigger.create({
          trigger: after,
          start: "top top+=72",
          // footer = last element: its bottom never passes the viewport top, so the
          // trigger stays active down to the last pixel ("max" would deactivate there)
          end: "bottom top",
          onToggle: (self) => window.dispatchEvent(new CustomEvent(NAV_SURFACE_EVENT, { detail: self.isActive })),
        });
      }

      // Orbit radius depends on stage size.
      const onResize = () => placeOrbit();
      window.addEventListener("resize", onResize);
      // Refreshes render the timeline with callbacks suppressed: re-sync the chair frame.
      ScrollTrigger.addEventListener("refresh", syncTurntable);

      // Keeps stuttering while scrolled → the static scene, at the chapter on screen.
      const stopWatchdog = isSceneForced() ? () => undefined : watchSceneFrames(downgradeScene);

      // Let nav links like "#services" jump to a chapter inside the pin.
      const unregister = registerChapterResolver((anchor) => {
        const i = chapters.findIndex((c) => c.anchor === anchor);
        const st = el.triggerRef.current;
        if (i === -1 || !st) return null;
        reportChapterJump(i);
        return st.start + settledProgress(chapters[i], i) * (st.end - st.start);
      });

      return () => {
        window.clearTimeout(warmUp);
        stopWatchdog();
        window.removeEventListener("resize", onResize);
        ScrollTrigger.removeEventListener("refresh", syncTurntable);
        unregister();
        el.triggerRef.current = null;
      };
    });
  }, el.root);
  // one revert only (it includes the matchMedia): reverting twice left stale inline
  // transforms behind in React StrictMode's dev double-mount
  return () => ctx.revert();
}
