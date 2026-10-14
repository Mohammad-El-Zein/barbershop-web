"use client";

import { useReducedMotion } from "@/lib/useReducedMotion";
import { PinnedScene } from "./PinnedScene";
import { StaticScene } from "./StaticScene";

/** Picks the animated pinned scene or the static fallback for reduced-motion users. */
export function Experience() {
  const reduced = useReducedMotion();
  return reduced ? <StaticScene /> : <PinnedScene />;
}
