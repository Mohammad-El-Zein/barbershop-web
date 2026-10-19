"use client";

import { useEffect, useRef, useState } from "react";
import { useTranslations } from "next-intl";
import { siteConfig } from "@/config/site.config";
import { frameUrl, loadFrame, loadRemainingFramesLazily } from "@/lib/frames";
import { markSceneReady } from "@/lib/sceneReady";
import { lockScroll, unlockScroll } from "@/lib/scroll";
import { estimateChairCssHeight, isSmallScreen, pickSet, preloadEagerFrames } from "@/lib/turntable";

const MIN_VISIBLE_MS = 300; // avoid a flash on fast connections
// never trap the user behind the loader (phones: shorter, the scene copes with missing frames)
const maxWaitMs = () => (isSmallScreen() ? 4000 : 8000);

/**
 * Only what the first screen shows: the <img data-critical> elements of the scene
 * (backdrop + chair poster, in whatever size their srcset picked). Products, the
 * feature object and the service photos appear later in the scroll and load after
 * the preloader (lib/sceneReady).
 */
function criticalImages(): Promise<void>[] {
  return [...document.querySelectorAll<HTMLImageElement>("img[data-critical]")].map((img) =>
    img.complete ? Promise.resolve() : img.decode().catch(() => undefined),
  );
}

/**
 * Full-screen loading screen shown while the first screen's assets load (always
 * dark: it opens the dark cinematic scene). Waits for the critical images, the
 * first turntable frames (coarse coverage of the whole rotation; none on phones),
 * optional frame-sequence frames and the fonts — then marks the scene ready. Scroll
 * is locked until it finishes; then
 * the scene timeline is built (lib/sceneReady), so its pin is measured once with the final font metrics.
 */
export function Preloader() {
  const t = useTranslations("scene");
  const [progress, setProgress] = useState(0);
  const [done, setDone] = useState(false);
  const [gone, setGone] = useState(false);
  const started = useRef(false);

  useEffect(() => {
    if (started.current) return; // StrictMode double-invoke guard
    started.current = true;

    // Always start at the top: the pinned scene reads best from the beginning.
    if ("scrollRestoration" in history) history.scrollRestoration = "manual";
    window.scrollTo(0, 0);
    lockScroll();

    const { frames, turntable } = siteConfig.scene;
    const tasks: Promise<void>[] = [
      ...criticalImages(),
      // phones: no turntable frames at all here (the static chair poster is critical instead)
      ...(turntable.enabled && !isSmallScreen() ? preloadEagerFrames(pickSet(estimateChairCssHeight())) : []),
      ...(frames.enabled
        ? Array.from({ length: Math.min(frames.eagerCount, frames.count) }, (_, i) => loadFrame(i))
        : []),
      document.fonts?.ready.then(() => undefined) ?? Promise.resolve(),
    ];

    let finished = 0;
    tasks.forEach((task) =>
      task.then(() => {
        finished++;
        setProgress(finished / tasks.length);
      }),
    );

    const startedAt = performance.now();
    const all = Promise.race([Promise.all(tasks), new Promise((r) => setTimeout(r, maxWaitMs()))]);

    all.then(() => {
      const wait = Math.max(0, MIN_VISIBLE_MS - (performance.now() - startedAt));
      setTimeout(() => {
        setProgress(1);
        setDone(true);
        unlockScroll();
        // builds the scene timeline (its ScrollTrigger measures itself with the final fonts)
        markSceneReady();
        if (frames.enabled) loadRemainingFramesLazily(frames.eagerCount);
      }, wait);
    });
  }, []);

  if (gone) return null;

  const pct = Math.round(progress * 100);

  return (
    <div
      role="status"
      aria-live="polite"
      aria-busy={!done}
      data-preloader
      onTransitionEnd={() => done && setGone(true)}
      className={`scene-dark fixed inset-0 z-[100] flex flex-col items-center justify-center bg-bg text-ink transition-opacity duration-700 ${
        done ? "pointer-events-none opacity-0" : "opacity-100"
      }`}
    >
      {siteConfig.scene.frames.enabled && <link rel="preload" as="image" href={frameUrl(0)} />}
      <div className="display text-gold-gradient text-3xl tracking-[0.12em] md:text-4xl">{siteConfig.brand.name}</div>
      <div className="mt-6 h-px w-48 overflow-hidden bg-white/10">
        <div
          className="h-full origin-left bg-gold transition-transform duration-300 ease-out"
          style={{ transform: `scaleX(${progress})` }}
        />
      </div>
      <div className="mt-3 text-[0.65rem] uppercase tracking-[0.4em] text-muted tabular-nums">
        {t("loading")} {pct}%
      </div>
      <span className="sr-only">{t("loadingStatus", { pct })}</span>
    </div>
  );
}
