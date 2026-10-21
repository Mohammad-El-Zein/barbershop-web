"use client";

import dynamic from "next/dynamic";
import { siteConfig } from "@/config/site.config";
import { PIN_PLACEHOLDER_ID, useSceneMode } from "@/lib/sceneMode";
import { StaticScene } from "./StaticScene";

// GSAP, ScrollTrigger and the turntable only load where the pinned scene runs (desktops)
const PinnedScene = dynamic(() => import("./PinnedScene").then((m) => m.PinnedScene), { ssr: false });

/** Picks the animated pinned scene (desktops) or the static, phone-friendly scene (lib/sceneMode). */
export function Experience() {
  const mode = useSceneMode();
  if (mode === "static") return <StaticScene />;
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
