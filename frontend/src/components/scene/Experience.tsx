"use client";

import { siteConfig } from "@/config/site.config";
import { PIN_PLACEHOLDER_ID, useSceneMode } from "@/lib/sceneMode";
import { PinnedScene } from "./PinnedScene";
import { StaticScene } from "./StaticScene";

/**
 * Picks the animated pinned scene (desktops: full, phones / touch: light variant) or the static
 * one (reduced motion, weak devices, automatic fallback) — lib/sceneMode. The server renders
 * the pinned scene.
 */
export function Experience() {
  const mode = useSceneMode();
  if (mode === "static") return <StaticScene />;
  return (
    <>
      <PinnedScene />
      {/*
        Desktops: the scene's timeline (and its pin spacer) is built after the preloader. Until
        then this keeps the pinned scroll length reserved, so the footer doesn't sit right below
        the scene and jump ~600 viewport heights when the pin appears (a layout shift). The light
        variant's sticky track has its length from the first paint (globals.css hides this).
      */}
      <div id={PIN_PLACEHOLDER_ID} aria-hidden="true" style={{ height: `${siteConfig.scene.scrollLengthVh}svh` }} />
    </>
  );
}
