/**
 * SITE CONFIG — the single place to rebrand / reuse this template.
 *
 * Structure, assets, colours and timing live here. All visible TEXT lives in
 * messages/<locale>.json; this file only references message keys (e.g.
 * "chapters.hero"), so a new client = new config values + new message files.
 *
 * The choreography of each chapter "stage" (how objects move) lives in code and
 * is selected via `chapter.stage`.
 *
 * Anything marked PLACEHOLDER must be replaced before going live.
 */
import turntableManifest from "../../public/scene/chair-turntable/manifest.json";

// ─────────────────────────────────────────────────────────────── types ──

export type Cta = {
  /** Message key of the label, e.g. "cta.book". */
  labelKey: string;
  /** "book" opens the booking flow, anything else is treated as an href / anchor. */
  action: "book" | string;
  variant: "primary" | "ghost";
};

export type SceneImage = {
  src: string;
  /** Optional lighter variant for phones (≤ 1280 px wide), used via srcset. */
  mobileSrc?: string;
  alt: string;
  /** Intrinsic pixel size of the source image (used for aspect ratio). */
  width: number;
  height: number;
};

export type Product = SceneImage & {
  id: string;
  /**
   * 0..1 — distance to the camera. Higher = closer: drawn on top, moves faster,
   * stronger parallax. Below 0.4 the object is also slightly blurred (depth of field).
   */
  depth: number;
  /** Resting position of the object's center inside the stage box, in % of its width/height. */
  x: number;
  y: number;
  /** Visible width as % of the stage box width (images are trimmed cut-outs). */
  size: number;
  /** Extra resting rotation in degrees. */
  rotate: number;
  /**
   * Slot on the ellipse around the chair in the "orbit" chapter, in degrees
   * (0 = right, 90 = front/below, 180 = left, 270 = behind/above). Keep slots
   * within ±40° of 0 or 180 so products stay at the sides of the chair.
   */
  orbitAngle: number;
};

export type ServiceCard = {
  /** Message namespace with title / text / price, e.g. "services.haircut". */
  messageKey: string;
  /** Backend service id: the card's "Book" button preselects this service in the booking flow. */
  serviceId: string;
  image: SceneImage;
  cta: Cta;
};

/**
 * Stage presets (choreography implemented in components/scene/timeline.ts):
 * hero      – hero object (chair) in the dark shop
 * products  – hero object fades back, products fly in as one composed cluster
 * services  – glass service cards slide up, staggered
 * feature   – one big feature object (scissors) scales & rotates across the stage
 * orbit     – hero object returns, products circle at its sides
 * cta       – hero object with a glowing animated gold ring
 */
export type StagePreset = "hero" | "products" | "services" | "feature" | "orbit" | "cta";

export type Chapter = {
  id: string;
  /** Message namespace with label / eyebrow / headline[] / body, e.g. "chapters.hero". */
  messageKey: string;
  /** Optional anchor (e.g. "services") so nav links can jump to this chapter. */
  anchor?: string;
  stage: StagePreset;
  /** Index of the headline line rendered with the gold gradient (optional). */
  accentLine?: number;
  ctas?: Cta[];
  /** [start, end] as fractions (0..1) of the pinned scroll length. Must be contiguous. */
  range: [number, number];
};

/**
 * A stretch of the pinned scroll during which the chair turntable plays.
 * `from`/`to` are scroll fractions (same scale as chapter ranges);
 * `frames` are positions in the sequence (0 = first frame, 1 = last frame),
 * so the ranges stay valid when a new video yields a different frame count.
 * Outside all segments the chair holds the last reached frame.
 */
export type TurntableSegment = { from: number; to: number; frames: [number, number] };

export type Palette = {
  bg: string;
  /** Secondary surface (footer, cards on page backgrounds). */
  surface: string;
  text: string;
  muted: string;
  /** Decorative gold (lines, glows, button fill). */
  gold: string;
  goldDeep: string;
  /** Gold for TEXT and outlines — must reach WCAG AA (4.5:1) on bg and surface. */
  goldText: string;
  /** Hairlines / borders. */
  line: string;
};

export type NavItem = { labelKey: string; href: string };
export type FooterColumn = { titleKey: string; links: NavItem[] };

// ────────────────────────────────────────────────────────────── config ──

export const siteConfig = {
  brand: {
    name: "THE BARBER NEWS",
    /** Short form shown on small screens. */
    shortName: "TBN",
  },

  /** PLACEHOLDER: production origin, used for canonical / hreflang URLs. */
  siteUrl: process.env.NEXT_PUBLIC_SITE_URL ?? "http://localhost:3000",

  /**
   * Colour tokens for both themes (injected as CSS variables by the layout).
   * The pinned cinematic scene always uses `dark` (dark photography).
   * Contrast (checked): light text 15.4:1, muted 6.0:1, goldText 5.6:1 on bg / 5.1:1 on surface.
   */
  theme: {
    dark: {
      bg: "#0a0a0a",
      surface: "#070707",
      text: "#f2efe9",
      muted: "rgb(242 239 233 / 0.62)",
      gold: "#c9a45c",
      goldDeep: "#8a6d2f",
      goldText: "#c9a45c",
      line: "rgb(242 239 233 / 0.12)",
    },
    light: {
      bg: "#f6f1e7",
      surface: "#efe6d6",
      text: "#1d1a17",
      muted: "rgb(29 26 23 / 0.70)",
      gold: "#c9a45c",
      goldDeep: "#8a6d2f",
      goldText: "#7a5a1c",
      line: "rgb(29 26 23 / 0.14)",
    },
  } satisfies Record<"dark" | "light", Palette>,

  /** Development helpers. Never rendered in production builds. */
  debug: {
    /**
     * Show small file-name badges (e.g. "chair.webp") on every scene asset as a
     * DOM overlay. Only rendered when NODE_ENV !== "production".
     */
    showPlaceholderLabels: false,
  },

  nav: [
    { labelKey: "nav.home", href: "#home" },
    { labelKey: "nav.services", href: "#services" },
    { labelKey: "nav.downtown", href: "#downtown" },
    { labelKey: "nav.about", href: "#about" },
  ] satisfies NavItem[],

  bookCta: { labelKey: "cta.book", action: "book", variant: "primary" } satisfies Cta,

  /** Pinned scene settings. */
  scene: {
    /** Total pinned scroll length in vh (the spec asks for ~600vh). */
    scrollLengthVh: 600,
    /** ScrollTrigger scrub smoothing in seconds (true = no smoothing). */
    scrub: 1,
    /**
     * Light variant (phones, touch devices; lib/sceneMode): a much shorter track, in svh (the
     * address bar showing / hiding never changes it), and the scene follows the finger
     * (scrub without smoothing).
     */
    lite: { scrollLengthSvh: 220 },

    backdrop: {
      src: "/scene/backdrop.webp",
      /** Phone variant (scripts/assets/mobile-variants.mjs), picked via srcset. */
      mobileSrc: "/scene/backdrop-1280.webp",
      alt: "",
      width: 2560,
      height: 1440,
    } satisfies SceneImage,

    /**
     * The chair. With the turntable enabled this is frame 0 of the sequence
     * (written by scripts/assets/turntable.py --poster), used before the canvas
     * draws, as fallback, and for prefers-reduced-motion.
     */
    heroObject: {
      src: "/scene/chair.webp",
      /** Phone variant (scripts/assets/mobile-variants.mjs), picked via srcset. */
      mobileSrc: "/scene/chair-640.webp",
      alt: "Barber chair",
      width: turntableManifest.sets[0].width,
      height: turntableManifest.sets[0].height,
    } satisfies SceneImage,

    /**
     * Scroll-driven chair rotation (image sequence on <canvas>), generated by
     * scripts/assets/turntable.py. Swap the input video and re-run the script —
     * no code changes needed.
     */
    turntable: {
      enabled: true,
      base: "/scene/chair-turntable",
      manifest: turntableManifest,
      /** Frames loaded behind the loading screen; the rest stream in on first interaction / when idle. */
      eagerFrames: 8,
      /** Same on phones (< 768 px), which also get the lighter 400 px set with every 2nd frame. */
      eagerFramesSmall: 4,
      segments: [
        // chapter 1 → 2: the chair turns away while the products fly in
        { from: 0.035, to: 0.155, frames: [0, 1] },
        // chapter 5 → 6: the chair turns back to face you and settles in the gold ring
        { from: 0.79, to: 0.9, frames: [1, 0] },
      ] satisfies TurntableSegment[],
    },

    featureObject: {
      src: "/scene/scissors-large.webp",
      alt: "Barber scissors",
      width: 901,
      height: 901,
    } satisfies SceneImage,

    /**
     * Grooming products. In the "products" chapter they form one composed cluster
     * around the chair's focal point (center of the stage box): large clipper
     * middle-left, scissors on the diagonal, comb behind the centre (silhouetted
     * against the gold spotlight), bottle small in front. Width/height = the
     * trimmed cut-out's pixel size.
     */
    products: [
      { id: "clipper", src: "/scene/clipper.webp", alt: "Hair clipper", width: 278, height: 704, depth: 0.8, x: 36, y: 52, size: 24, rotate: -14, orbitAngle: 150 },
      { id: "scissors", src: "/scene/scissors.webp", alt: "Scissors", width: 372, height: 877, depth: 0.6, x: 61, y: 45, size: 22, rotate: 18, orbitAngle: 30 },
      { id: "comb", src: "/scene/comb.webp", alt: "Comb", width: 650, height: 839, depth: 0.35, x: 50, y: 30, size: 28, rotate: -6, orbitAngle: 215 },
      { id: "bottle", src: "/scene/bottle.webp", alt: "Grooming oil bottle", width: 320, height: 801, depth: 0.95, x: 73, y: 71, size: 13, rotate: 0, orbitAngle: 325 },
    ] satisfies Product[],

    /**
     * Optional technique A: scroll-scrubbed full-frame image sequence drawn on
     * <canvas> behind the layered objects (see scripts/extract-frames.*).
     * Frame i is `${path}${pad(i)}.${ext}`, 1-based.
     */
    frames: {
      enabled: false,
      path: "/frames/frame_",
      count: 150,
      pad: 4,
      ext: "webp",
      /** Frames loaded before the page is revealed; the rest load lazily. */
      eagerCount: 20,
    },
  },

  chapters: [
    {
      id: "hero",
      messageKey: "chapters.hero",
      anchor: "home",
      stage: "hero",
      accentLine: 1,
      ctas: [
        { labelKey: "cta.book", action: "book", variant: "primary" },
        { labelKey: "cta.explore", action: "#services", variant: "ghost" },
      ],
      range: [0, 0.14],
    },
    { id: "signature", messageKey: "chapters.signature", stage: "products", accentLine: 1, range: [0.14, 0.32] },
    { id: "services", messageKey: "chapters.services", anchor: "services", stage: "services", accentLine: 1, range: [0.32, 0.5] },
    { id: "craft", messageKey: "chapters.craft", anchor: "about", stage: "feature", accentLine: 1, range: [0.5, 0.66] },
    { id: "premium", messageKey: "chapters.premium", stage: "orbit", accentLine: 1, range: [0.66, 0.84] },
    {
      id: "cta",
      messageKey: "chapters.cta",
      stage: "cta",
      accentLine: 1,
      ctas: [{ labelKey: "cta.book", action: "book", variant: "primary" }],
      range: [0.84, 1],
    },
  ] satisfies Chapter[],

  services: [
    {
      messageKey: "services.haircut",
      serviceId: "7b3f1c2e-0000-4000-8000-000000000101", // PLACEHOLDER: demo shop ids
      image: { src: "/scene/service-haircut.webp", alt: "", width: 1280, height: 800 },
      cta: { labelKey: "cta.bookShort", action: "book", variant: "ghost" },
    },
    {
      messageKey: "services.beard",
      serviceId: "7b3f1c2e-0000-4000-8000-000000000102",
      image: { src: "/scene/service-beard.webp", alt: "", width: 1280, height: 800 },
      cta: { labelKey: "cta.bookShort", action: "book", variant: "ghost" },
    },
    {
      messageKey: "services.styling",
      serviceId: "7b3f1c2e-0000-4000-8000-000000000103",
      image: { src: "/scene/service-styling.webp", alt: "", width: 1280, height: 800 },
      cta: { labelKey: "cta.bookShort", action: "book", variant: "ghost" },
    },
  ] satisfies ServiceCard[],

  /**
   * PLACEHOLDER contact data. Replace with the client's real details. Opening hours are in the messages.
   * The structured address is the fallback for the schema.org data when the backend's shop profile
   * (admin → settings → shop profile) can't be loaded; keep both in sync.
   */
  contact: {
    anchor: "downtown",
    addressLines: ["Musterstraße 1 (placeholder)", "10115 Berlin"],
    address: { street: "Musterstraße 1", postalCode: "10115", city: "Berlin", country: "DE" },
    phone: "+49 30 0000000",
    email: "hello@example.com",
    priceRange: "€€",
  },

  footer: {
    columns: [
      {
        titleKey: "footer.explore",
        links: [
          { labelKey: "nav.home", href: "#home" },
          { labelKey: "nav.services", href: "#services" },
          { labelKey: "nav.about", href: "#about" },
          { labelKey: "nav.downtown", href: "#downtown" },
        ],
      },
      {
        titleKey: "footer.services",
        links: [
          { labelKey: "services.haircut.title", href: "#services" },
          { labelKey: "services.beard.title", href: "#services" },
          { labelKey: "services.styling.title", href: "#services" },
        ],
      },
    ] satisfies FooterColumn[],
    /** PLACEHOLDER social URLs. */
    socials: [
      { label: "Instagram", href: "https://instagram.com/", icon: "instagram" },
      { label: "Facebook", href: "https://facebook.com/", icon: "facebook" },
      { label: "TikTok", href: "https://tiktok.com/", icon: "tiktok" },
    ] as const,
    /** Legal pages (src/content/legal). Paths are prefixed with the locale at render time. */
    legal: [
      { labelKey: "footer.legal.impressum", href: "/impressum" },
      { labelKey: "footer.legal.privacy", href: "/datenschutz" },
      { labelKey: "footer.legal.terms", href: "/agb" },
    ] satisfies NavItem[],
  },
};

export type SiteConfig = typeof siteConfig;
