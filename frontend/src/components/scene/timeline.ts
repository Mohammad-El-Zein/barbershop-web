import { gsap } from "@/lib/gsap";
import type { Chapter, Product, StagePreset } from "@/config/site.config";

/**
 * Builds the master scroll timeline for the pinned scene.
 *
 * The timeline is normalized to a duration of exactly 1, so a position on the
 * timeline == a fraction of the pinned scroll == the `range` values in the config.
 *
 * Structure:
 *  - every chapter's text block: masked line reveal in, line wipe out
 *  - every actor (chair, products, cards, scissors, orbit, ring, lights) gets
 *    enter / hold / exit tweens depending on the stage preset of each chapter
 *
 * Only transform + opacity (autoAlpha) are animated, except the ring's
 * stroke-dashoffset (one SVG circle, cheap). All fromTo tweens use
 * immediateRender:false so building the timeline never disturbs the start state.
 */

/** Length of a chapter transition, as a fraction of the total pinned scroll. */
const T = 0.045;

export type SceneRefs = {
  root: HTMLElement;
  stageBox: HTMLElement;
  chapters: Chapter[];
  products: Product[];
  isMobile: boolean;
};

type Pose = { autoAlpha: number; xPercent: number; yPercent: number; scale: number };

/** Where the hero object (chair) sits in each stage preset. */
const CHAIR_POSES: Record<StagePreset, Pose> = {
  hero: { autoAlpha: 1, xPercent: 0, yPercent: 0, scale: 1 },
  products: { autoAlpha: 0, xPercent: 45, yPercent: 6, scale: 0.78 },
  services: { autoAlpha: 0, xPercent: 45, yPercent: 6, scale: 0.78 },
  feature: { autoAlpha: 0, xPercent: 45, yPercent: 6, scale: 0.78 },
  orbit: { autoAlpha: 1, xPercent: 0, yPercent: 4, scale: 0.74 },
  cta: { autoAlpha: 1, xPercent: 0, yPercent: 2, scale: 0.84 },
};

/** Darkening of the backdrop per preset (lets foreground objects pop). */
const DIM: Record<StagePreset, number> = {
  hero: 0,
  products: 0.35,
  services: 0.6,
  feature: 0.55,
  orbit: 0.3,
  cta: 0.4,
};

/** Warm gold rim light intensity per preset. */
const RIM: Record<StagePreset, number> = {
  hero: 0.75,
  products: 0.55,
  services: 0.35,
  feature: 0.9,
  orbit: 0.75,
  cta: 1,
};

export function buildSceneTimeline(tl: gsap.core.Timeline, refs: SceneRefs) {
  const { root, chapters, products, isMobile } = refs;
  const q = gsap.utils.selector(root);
  const one = (sel: string) => q(sel)[0] as HTMLElement;

  const chair = one('[data-obj="hero"]');
  const feature = one('[data-obj="feature"]');
  const ring = one('[data-obj="ring"]');
  const ringDraw = q("[data-ring-draw]");
  const dim = one('[data-obj="dim"]');
  const rim = one('[data-obj="rim"]');
  const backdrop = one('[data-obj="backdrop"]');
  const cue = one('[data-obj="scroll-cue"]');
  const cards = q("[data-card]");
  const flyers = products.map((p) => one(`[data-product="${p.id}"]`));
  const flyerInners = products.map((p) => one(`[data-product="${p.id}"] [data-inner]`));
  const orbiters = products.map((p) => one(`[data-orbit="${p.id}"]`));
  const orbiterInners = products.map((p) => one(`[data-orbit="${p.id}"] [data-inner]`));

  const first = chapters[0].stage;

  // ── initial state (what the scene looks like at progress 0) ────────────────
  gsap.set(chair, CHAIR_POSES[first]);
  gsap.set(dim, { autoAlpha: DIM[first] });
  gsap.set(rim, { autoAlpha: RIM[first] });
  gsap.set([...flyers, ...cards, feature, ring, ...orbiterInners], { autoAlpha: 0 });
  gsap.set(ringDraw, { strokeDashoffset: 1 });

  // Depth factor is exaggerated a bit on desktop where there's more room.
  const travel = isMobile ? 0.7 : 1;

  // ── continuous background motion across the whole scene ─────────────────────
  tl.fromTo(backdrop, { scale: 1.02, xPercent: 0 }, { scale: 1.14, xPercent: -2, duration: 1, immediateRender: false }, 0);
  tl.to(cue, { autoAlpha: 0, y: 20, duration: 0.03 }, 0.005);

  // ── stage presets: enter / hold / exit ──────────────────────────────────────
  type Hook = (at: number, ch: Chapter) => void;
  const enter: Partial<Record<StagePreset, Hook>> = {};
  const hold: Partial<Record<StagePreset, Hook>> = {};
  const exit: Partial<Record<StagePreset, Hook>> = {};

  // HERO: slow push-in on the chair
  hold.hero = (_, ch) => {
    const [s, e] = ch.range;
    tl.to(chair, { scale: CHAIR_POSES.hero.scale * 1.05, duration: Math.max(0.01, e - s - T), ease: "none" }, s);
  };

  // PRODUCTS: fly in from the sides with depth-based speed & rotation, parallax drift, fly out
  enter.products = (at) => {
    products.forEach((p, i) => {
      const dir = p.x < 50 ? -1 : 1;
      const d = p.depth;
      tl.fromTo(
        flyers[i],
        { autoAlpha: 0, xPercent: dir * (160 + 260 * d) * travel, yPercent: (120 + 180 * d) * travel, rotate: p.rotate + dir * (50 + 70 * d), scale: 0.6 },
        // closer objects (higher depth) arrive faster
        { autoAlpha: 1, xPercent: 0, yPercent: 0, rotate: p.rotate, scale: 1, duration: T * (1.9 - d * 0.8), ease: "power3.out", immediateRender: false },
        at + i * T * 0.12,
      );
    });
  };
  hold.products = (_, ch) => {
    const [s, e] = ch.range;
    products.forEach((p, i) => {
      const d = p.depth;
      tl.fromTo(
        flyerInners[i],
        { yPercent: 25 * d, rotate: 0 },
        { yPercent: -(30 + 70 * d) * travel, rotate: (p.x < 50 ? -1 : 1) * 10 * d, duration: e - s, ease: "none", immediateRender: false },
        s,
      );
    });
  };
  exit.products = (at) => {
    products.forEach((p, i) => {
      const d = p.depth;
      tl.to(
        flyers[i],
        { autoAlpha: 0, yPercent: -(180 + 260 * d) * travel, xPercent: (p.x < 50 ? -1 : 1) * 60, rotate: p.rotate + 40, duration: T * (1.4 - 0.5 * d), ease: "power2.in" },
        at + (1 - d) * T * 0.25,
      );
    });
  };

  // SERVICES: glass cards slide up, staggered
  enter.services = (at) => {
    tl.fromTo(
      cards,
      { autoAlpha: 0, yPercent: isMobile ? 60 : 120 },
      { autoAlpha: 1, yPercent: 0, duration: T * 1.1, stagger: T * 0.25, ease: "power3.out", immediateRender: false },
      at,
    );
  };
  exit.services = (at) => {
    tl.to(cards, { autoAlpha: 0, yPercent: -50, duration: T * 0.8, stagger: T * 0.12, ease: "power2.in" }, at);
  };

  // FEATURE: big scissors scale up and rotate slowly across the stage
  enter.feature = (at) => {
    tl.fromTo(
      feature,
      { autoAlpha: 0, scale: 0.35, rotate: -70, xPercent: 18 },
      { autoAlpha: 1, scale: 0.62, rotate: -32, xPercent: 8, duration: T * 1.2, ease: "power3.out", immediateRender: false },
      at,
    );
  };
  hold.feature = (_, ch) => {
    const [s, e] = ch.range;
    const from = s + T * 1.1;
    const to = e - T * 0.9;
    tl.to(feature, { scale: 1.12, rotate: 18, xPercent: -6, duration: Math.max(0.01, to - from), ease: "none" }, from);
  };
  exit.feature = (at) => {
    tl.to(feature, { autoAlpha: 0, scale: 1.6, rotate: 40, duration: T * 0.9, ease: "power2.in" }, at);
  };

  // ORBIT: products circle the chair on an ellipse (front/back depth via scale + z-index)
  const orbit = { angle: 0 };
  const placeOrbit = () => {
    const box = refs.stageBox.getBoundingClientRect();
    const rx = box.width * (isMobile ? 0.4 : 0.36);
    const ry = box.height * 0.13;
    const n = orbiters.length;
    orbiters.forEach((el, i) => {
      const a = orbit.angle * Math.PI * 2 + (i / n) * Math.PI * 2;
      const sin = Math.sin(a);
      gsap.set(el, {
        x: Math.cos(a) * rx,
        y: sin * ry,
        scale: 0.78 + 0.32 * ((sin + 1) / 2),
        zIndex: sin > 0 ? 30 : 5, // lower half of the ellipse passes in front of the chair (z 10)
      });
    });
  };
  placeOrbit();
  enter.orbit = (at) => {
    tl.fromTo(
      orbiterInners,
      { autoAlpha: 0, scale: 0.2 },
      { autoAlpha: 1, scale: 1, duration: T, stagger: T * 0.15, ease: "back.out(1.6)", immediateRender: false },
      at + T * 0.4,
    );
  };
  hold.orbit = (_, ch) => {
    const [s, e] = ch.range;
    // Orbit keeps turning slightly into the next chapter while products fade out.
    tl.fromTo(orbit, { angle: 0 }, { angle: 0.7, duration: e - s + T, ease: "none", onUpdate: placeOrbit, immediateRender: false }, s);
  };
  exit.orbit = (at) => {
    tl.to(orbiterInners, { autoAlpha: 0, scale: 0.3, duration: T * 0.8, stagger: T * 0.08, ease: "power2.in" }, at);
  };

  // CTA: glowing gold ring draws itself around the chair
  enter.cta = (at) => {
    tl.fromTo(ring, { autoAlpha: 0, scale: 0.82 }, { autoAlpha: 1, scale: 1, duration: T * 1.3, ease: "power3.out", immediateRender: false }, at);
    tl.fromTo(ringDraw, { strokeDashoffset: 1 }, { strokeDashoffset: 0, duration: T * 2, stagger: T * 0.3, ease: "power2.inOut", immediateRender: false }, at);
  };
  hold.cta = (_, ch) => {
    const [s, e] = ch.range;
    tl.to(ring, { rotate: 25, duration: e - s, ease: "none" }, s);
  };

  // ── per chapter: text + stage ───────────────────────────────────────────────
  chapters.forEach((ch, i) => {
    const [s, e] = ch.range;
    const block = one(`[data-chapter="${ch.id}"]`);
    const lines = block.querySelectorAll(".line");
    const fades = block.querySelectorAll("[data-fade]");
    const isFirst = i === 0;
    const isLast = i === chapters.length - 1;

    // Text in: lines slide up out of their masks, then eyebrow/body/buttons fade up.
    if (!isFirst) {
      gsap.set(block, { autoAlpha: 0 });
      gsap.set(lines, { yPercent: 110 });
      gsap.set(fades, { autoAlpha: 0, y: 24 });
      tl.set(block, { autoAlpha: 1 }, s);
      tl.to(lines, { yPercent: 0, duration: T * 0.8, stagger: T * 0.14, ease: "power3.out" }, s + T * 0.05);
      tl.to(fades, { autoAlpha: 1, y: 0, duration: T * 0.7, stagger: T * 0.1, ease: "power2.out" }, s + T * 0.35);
    }
    // Text out: lines wipe upward through the mask.
    if (!isLast) {
      const out = e - T * 0.85;
      tl.to(lines, { yPercent: -110, duration: T * 0.6, stagger: T * 0.08, ease: "power2.in" }, out);
      tl.to(fades, { autoAlpha: 0, y: -16, duration: T * 0.5, ease: "power1.in" }, out);
      tl.set(block, { autoAlpha: 0 }, e);
    }

    hold[ch.stage]?.(s, ch);

    if (isLast) return;
    const next = chapters[i + 1];
    const b = e; // boundary between chapter i and i+1

    exit[ch.stage]?.(b - T * 0.85, ch);
    enter[next.stage]?.(b, next);

    // Chair moves between poses; when it re-appears it rises back into frame.
    const from = CHAIR_POSES[ch.stage];
    const to = CHAIR_POSES[next.stage];
    if (from.autoAlpha === 0 && to.autoAlpha === 1) {
      tl.fromTo(
        chair,
        { autoAlpha: 0, xPercent: 0, yPercent: 28, scale: to.scale * 0.85 },
        { ...to, duration: T * 1.5, ease: "power3.out", immediateRender: false },
        b - T * 0.2,
      );
    } else if (JSON.stringify(from) !== JSON.stringify(to)) {
      tl.to(chair, { ...to, duration: T * 1.2, ease: "power2.inOut" }, b - T * 0.7);
    }

    // Lighting
    tl.to(dim, { autoAlpha: DIM[next.stage], duration: T * 1.2 }, b - T * 0.6);
    tl.to(rim, { autoAlpha: RIM[next.stage], duration: T * 1.2 }, b - T * 0.6);
  });

  // Pad to exactly 1 so timeline time == scroll fraction.
  if (tl.duration() < 1) tl.set({}, {}, 1);

  return { placeOrbit };
}

/**
 * Scroll fraction at which a chapter's text is fully revealed — used by
 * the section dots and nav links to land on a "settled" frame.
 */
export function settledProgress(ch: Chapter, index: number) {
  return index === 0 ? 0 : Math.min(ch.range[1] - 0.001, ch.range[0] + T * 1.7);
}
