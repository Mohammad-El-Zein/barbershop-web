import { gsap } from "@/lib/gsap";
import type { Chapter, Product, StagePreset, TurntableSegment } from "@/config/site.config";

/**
 * Builds the master scroll timeline for the pinned scene.
 *
 * The timeline is normalized to a duration of exactly 1, so a position on the
 * timeline == a fraction of the pinned scroll == the `range` values in the config.
 *
 * Structure:
 *  - every chapter's text block: masked line reveal in, line wipe out (Y only)
 *  - every actor (chair, products, cards, scissors, orbit, ring, lights) gets
 *    enter / hold / exit tweens depending on the stage preset of each chapter
 *
 * The chair turntable (image sequence) is driven the same way: one tween per
 * configured segment on a proxy value, so ScrollTrigger's scrub smoothing applies
 * to the rotation too, and outside the segments the chair holds its last frame.
 *
 * Everything is scrub-driven: "floating" and "orbit" motion are tweens on the
 * timeline (yoyo/repeat inside the chapter), never autoplay loops, so they
 * never fight the scroll position.
 *
 * Only transform + opacity (autoAlpha) are animated, except the ring's
 * stroke-dashoffset (one SVG circle, cheap). All fromTo tweens use
 * immediateRender:false so building the timeline never disturbs the start state.
 */

/** Length of a chapter transition, as a fraction of the total pinned scroll. */
const T = 0.045;

/**
 * How far headline lines travel (yPercent) to be fully hidden by their mask.
 * More than 100% because the mask has extra room above for umlaut dots.
 */
const LINE_TRAVEL = 125;

export type SceneRefs = {
  root: HTMLElement;
  stageBox: HTMLElement;
  chapters: Chapter[];
  products: Product[];
  isMobile: boolean;
  /** Optional chair image sequence; onFrame receives a fractional frame index. */
  turntable?: { frameCount: number; segments: TurntableSegment[]; onFrame: (frame: number) => void };
};

/**
 * Poses always include x/y = 0 next to xPercent/yPercent: GSAP stores percent and
 * pixel offsets separately, and pinning them both prevents a stale pixel offset
 * (e.g. from a re-parsed inline transform) from ever surviving.
 */
type Pose = { autoAlpha: number; xPercent: number; yPercent: number; scale: number; x: 0; y: 0 };

/** Hidden pose: the chair stays at the focal point and simply recedes (scale back + fade). */
const CHAIR_AWAY: Pose = { autoAlpha: 0, xPercent: 0, yPercent: 3, scale: 0.7, x: 0, y: 0 };

/** Where the hero object (chair) sits in each stage preset. */
const CHAIR_POSES: Record<StagePreset, Pose> = {
  hero: { autoAlpha: 1, xPercent: 0, yPercent: 0, scale: 1, x: 0, y: 0 },
  products: CHAIR_AWAY,
  services: CHAIR_AWAY,
  feature: CHAIR_AWAY,
  // smaller so the products have clear space at its sides
  orbit: { autoAlpha: 1, xPercent: 0, yPercent: 2, scale: 0.6, x: 0, y: 0 },
  cta: { autoAlpha: 1, xPercent: 0, yPercent: 2, scale: 0.84, x: 0, y: 0 },
};

/** Darkening of the backdrop per preset (lets foreground objects pop). */
const DIM: Record<StagePreset, number> = {
  hero: 0,
  products: 0.45,
  services: 0.85, // backdrop arches down to ~15% so the cards are the focus
  feature: 0.55,
  orbit: 0.3,
  cta: 0.4,
};

/** Warm gold rim light intensity per preset. */
const RIM: Record<StagePreset, number> = {
  hero: 0.75,
  products: 0.5,
  services: 0.2,
  feature: 0.9,
  orbit: 0.75,
  cta: 1,
};

/** Rotation of the orbit during its chapter, in degrees (negative = moves the front items toward the horizontal). */
const ORBIT_SWEEP = -10;

const same = (a: Pose, b: Pose) =>
  a.autoAlpha === b.autoAlpha && a.xPercent === b.xPercent && a.yPercent === b.yPercent && a.scale === b.scale;

export function buildSceneTimeline(tl: gsap.core.Timeline, refs: SceneRefs) {
  const { root, stageBox, chapters, products, isMobile } = refs;
  const q = gsap.utils.selector(root);
  const one = (sel: string) => q(sel)[0] as HTMLElement;

  const chair = one('[data-obj="hero"]');
  const feature = one('[data-obj="feature"]');
  const ring = one('[data-obj="ring"]');
  const ringDraw = q("[data-ring-draw]");
  const spot = one('[data-obj="spot"]');
  const floor = one('[data-obj="floor"]');
  const dim = one('[data-obj="dim"]');
  const rim = one('[data-obj="rim"]');
  const backdrop = one('[data-obj="backdrop"]');
  const cue = one('[data-obj="scroll-cue"]');
  const cards = q("[data-card]");
  const flyers = products.map((p) => one(`[data-product="${p.id}"]`));
  const drifts = products.map((p) => one(`[data-product="${p.id}"] [data-drift]`));
  const floaters = products.map((p) => one(`[data-product="${p.id}"] [data-inner]`));
  const orbiters = products.map((p) => one(`[data-orbit="${p.id}"]`));
  const orbiterInners = products.map((p) => one(`[data-orbit="${p.id}"] [data-inner]`));

  const first = chapters[0].stage;
  const box = stageBox.getBoundingClientRect();

  // ── initial state (what the scene looks like at progress 0) ────────────────
  gsap.set(chair, CHAIR_POSES[first]);
  gsap.set(dim, { autoAlpha: DIM[first] });
  gsap.set(rim, { autoAlpha: RIM[first] });
  gsap.set([...flyers, ...cards, feature, ring, ...orbiterInners], { autoAlpha: 0 });
  gsap.set([spot, floor], { autoAlpha: 0, scale: 0.8 });
  gsap.set(ringDraw, { strokeDashoffset: 1 });

  // ── continuous background motion across the whole scene ─────────────────────
  tl.fromTo(backdrop, { scale: 1.02, xPercent: 0 }, { scale: 1.14, xPercent: -2, duration: 1, immediateRender: false }, 0);
  tl.to(cue, { autoAlpha: 0, y: 20, duration: 0.03 }, 0.005);

  // ── chair turntable: frame position (0 = first, 1 = last) per scroll segment ──
  // ScrollTrigger.refresh() renders the timeline to the end and back with callbacks
  // suppressed, so the proxy value can change without onUpdate firing. syncTurntable
  // re-pushes the current value; PinnedScene calls it after every refresh.
  let syncTurntable = () => {};
  if (refs.turntable) {
    const { frameCount, segments, onFrame } = refs.turntable;
    const turn = { pos: segments[0]?.frames[0] ?? 0 };
    const push = () => onFrame(turn.pos * (frameCount - 1));
    syncTurntable = push;
    push();
    for (const seg of segments) {
      tl.fromTo(
        turn,
        { pos: seg.frames[0] },
        { pos: seg.frames[1], duration: seg.to - seg.from, ease: "none", onUpdate: push, immediateRender: false },
        seg.from,
      );
    }
  }

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

  // PRODUCTS: one composed cluster around the chair's focal point (stage center).
  // Each product flies in from outside the stage along the line from the focal
  // point through its resting spot, with its own spin, then floats and drifts.
  const flight = products.map((p) => {
    let dx = p.x - 50;
    let dy = p.y - 50;
    let len = Math.hypot(dx, dy);
    if (len < 4) [dx, dy, len] = [-1, 0.25, Math.hypot(1, 0.25)]; // centered object → from the left
    dx /= len;
    dy /= len;
    // far enough that the object starts outside the stage box
    const dist = Math.max(box.width, box.height) * (0.85 + 0.35 * p.depth);
    const spin = (dx < 0 ? -1 : 1) * (70 + 90 * p.depth);
    return { dx, dy, dist, spin };
  });

  enter.products = (at) => {
    // Starts slightly before the boundary, overlapping the chair receding, so the
    // cluster has fully settled shortly after the chapter text is revealed.
    const start = at - T * 0.3;
    tl.to([spot, floor], { autoAlpha: 1, scale: 1, duration: T * 1.4, ease: "power2.out" }, start);
    products.forEach((p, i) => {
      const f = flight[i];
      const d = p.depth;
      tl.fromTo(
        flyers[i],
        { autoAlpha: 0, x: f.dx * f.dist, y: f.dy * f.dist, rotate: p.rotate + f.spin, scale: 0.7 },
        // closer objects (higher depth) travel further but arrive faster
        { autoAlpha: 1, x: 0, y: 0, rotate: p.rotate, scale: 1, duration: T * (1.3 - d * 0.4), ease: "power3.out", immediateRender: false },
        start + i * T * 0.1,
      );
    });
  };
  hold.products = (_, ch) => {
    const [s, e] = ch.range;
    const len = e - s;
    products.forEach((p, i) => {
      const d = p.depth;
      const f = flight[i];
      // Parallax drift: near objects move more than far ones.
      tl.fromTo(
        drifts[i],
        { xPercent: -f.dx * 4 * d, yPercent: 5 * d },
        { xPercent: f.dx * 4 * d, yPercent: -7 * d, duration: len, ease: "none", immediateRender: false },
        s,
      );
      // Slow float: up/down + a little rotation, out of phase per object (scrubbed yoyo).
      tl.fromTo(
        floaters[i],
        { yPercent: 0, rotate: 0 },
        {
          yPercent: -(3 + 3 * d),
          rotate: (i % 2 ? 1 : -1) * (3 + 3 * (1 - d)),
          duration: len / 2,
          repeat: 1,
          yoyo: true,
          ease: "sine.inOut",
          immediateRender: false,
        },
        s + (i % 2) * T * 0.3,
      );
      // Gentle sideways sway at a different rate gives a subtle orbiting feel.
      tl.fromTo(
        floaters[i],
        { xPercent: 0 },
        { xPercent: (i % 2 ? -1 : 1) * 3, duration: len / 3, repeat: 2, yoyo: true, ease: "sine.inOut", immediateRender: false },
        s,
      );
    });
  };
  exit.products = (at) => {
    tl.to([spot, floor], { autoAlpha: 0, scale: 0.85, duration: T, ease: "power1.in" }, at + T * 0.2);
    products.forEach((p, i) => {
      const f = flight[i];
      const d = p.depth;
      tl.to(
        flyers[i],
        {
          autoAlpha: 0,
          x: f.dx * f.dist * 0.8,
          y: f.dy * f.dist * 0.8 - box.height * 0.25,
          rotate: p.rotate - f.spin * 0.5,
          scale: 0.85,
          duration: T * (1.3 - 0.4 * d),
          ease: "power2.in",
        },
        at + (1 - d) * T * 0.25,
      );
    });
  };

  // SERVICES: glass cards slide up, staggered
  enter.services = (at) => {
    tl.fromTo(
      cards,
      { autoAlpha: 0, y: 0, yPercent: isMobile ? 60 : 120 },
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
      { autoAlpha: 0, x: 0, y: 0, scale: 0.35, rotate: -70, xPercent: 18 },
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

  // ORBIT: products sit in slots on an ellipse around the chair (config: orbitAngle)
  // and sweep slowly along it. Slots stay at the chair's sides, so nothing ever
  // covers the seat; the lower half is in front (z 30), the upper half behind (z 5).
  const orbit = { sweep: 0 };
  const placeOrbit = () => {
    const b = stageBox.getBoundingClientRect();
    const rx = b.width * 0.38;
    const ry = b.height * 0.28;
    orbiters.forEach((el, i) => {
      const a = ((products[i].orbitAngle + orbit.sweep) * Math.PI) / 180;
      const sin = Math.sin(a);
      gsap.set(el, {
        x: Math.cos(a) * rx,
        y: sin * ry,
        scale: 0.96 + 0.1 * sin, // front slightly larger than back
        zIndex: sin > 0 ? 30 : 5,
      });
    });
  };
  placeOrbit();
  enter.orbit = (at) => {
    tl.fromTo(
      orbiterInners,
      { autoAlpha: 0, scale: 0.3 },
      { autoAlpha: 1, scale: 1, duration: T, stagger: T * 0.15, ease: "back.out(1.4)", immediateRender: false },
      at + T * 0.4,
    );
  };
  hold.orbit = (_, ch) => {
    const [s, e] = ch.range;
    tl.fromTo(orbit, { sweep: 0 }, { sweep: ORBIT_SWEEP, duration: e - s, ease: "none", onUpdate: placeOrbit, immediateRender: false }, s);
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
      gsap.set(lines, { y: 0, yPercent: LINE_TRAVEL });
      gsap.set(fades, { autoAlpha: 0, y: 24 });
      tl.set(block, { autoAlpha: 1 }, s);
      tl.to(lines, { y: 0, yPercent: 0, duration: T * 0.8, stagger: T * 0.14, ease: "power3.out" }, s + T * 0.05);
      tl.to(fades, { autoAlpha: 1, y: 0, duration: T * 0.7, stagger: T * 0.1, ease: "power2.out" }, s + T * 0.35);
    }
    // Text out: lines wipe upward through the mask.
    if (!isLast) {
      const out = e - T * 0.85;
      tl.to(lines, { y: 0, yPercent: -LINE_TRAVEL, duration: T * 0.6, stagger: T * 0.08, ease: "power2.in" }, out);
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
        { autoAlpha: 0, x: 0, y: 0, xPercent: 0, yPercent: 28, scale: to.scale * 0.85 },
        { ...to, duration: T * 1.5, ease: "power3.out", immediateRender: false },
        b - T * 0.2,
      );
    } else if (!same(from, to)) {
      tl.to(chair, { ...to, duration: T * 1.2, ease: "power2.inOut" }, b - T * 0.7);
    }

    // Lighting
    tl.to(dim, { autoAlpha: DIM[next.stage], duration: T * 1.2 }, b - T * 0.6);
    tl.to(rim, { autoAlpha: RIM[next.stage], duration: T * 1.2 }, b - T * 0.6);
  });

  // Pad to exactly 1 so timeline time == scroll fraction.
  if (tl.duration() < 1) tl.set({}, {}, 1);

  return { placeOrbit, syncTurntable };
}

/**
 * Scroll fraction at which a chapter's text is fully revealed — used by
 * the section dots and nav links to land on a "settled" frame.
 */
export function settledProgress(ch: Chapter, index: number) {
  return index === 0 ? 0 : Math.min(ch.range[1] - 0.001, ch.range[0] + T * 1.7);
}
