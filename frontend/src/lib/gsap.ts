"use client";

import gsap from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";
import { useGSAP } from "@gsap/react";

// Register once, on the client only.
if (typeof window !== "undefined") {
  gsap.registerPlugin(ScrollTrigger, useGSAP);
  // Mobile browsers resize the viewport when the URL bar shows/hides;
  // ignoring that avoids a jarring refresh of the pinned scene mid-scroll.
  ScrollTrigger.config({ ignoreMobileResize: true });
  // Dev only: expose GSAP for debugging / automated scroll tests.
  if (process.env.NODE_ENV !== "production") {
    (window as unknown as { __gsap: unknown }).__gsap = { gsap, ScrollTrigger };
  }
}

export { gsap, ScrollTrigger, useGSAP };
