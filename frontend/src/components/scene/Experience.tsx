"use client";

import { siteConfig } from "@/config/site.config";
import { useReducedMotion } from "@/lib/useReducedMotion";
import { PIN_PLACEHOLDER_ID, PinnedScene } from "./PinnedScene";
import { StaticScene } from "./StaticScene";

/** Picks the animated pinned scene or the static fallback for reduced-motion users. */
export function Experience() {
  const reduced = useReducedMotion();
  if (reduced) return <StaticScene />;
  return (
    <>
      <PinnedScene />
      {/*
        The scene's timeline (and its pin spacer) is built after the preloader. Until then
        this keeps the pinned scroll length reserved, so the footer doesn't sit right below
        the scene and jump ~600 viewport heights when the pin appears (a layout shift).
      */}
      <div id={PIN_PLACEHOLDER_ID} aria-hidden="true" style={{ height: `${siteConfig.scene.scrollLengthVh}svh` }} />
    </>
  );
}
