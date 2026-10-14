/**
 * SITE CONFIG — the single place to rebrand / reuse this template.
 *
 * Everything the landing page renders (brand, nav, chapter copy, scroll ranges,
 * scene assets, frame sequence, footer) comes from this file. The choreography
 * of each chapter "stage" (how objects move) lives in code and is selected via
 * `chapter.stage`; the content, assets and timing live here.
 *
 * Anything marked PLACEHOLDER must be replaced before going live.
 */

// ─────────────────────────────────────────────────────────────── types ──

export type Cta = {
  label: string;
  /** "book" opens the booking flow, anything else is treated as an href / anchor. */
  action: "book" | string;
  variant: "primary" | "ghost";
};

export type SceneImage = {
  src: string;
  alt: string;
  /** Intrinsic pixel size of the source image (used for aspect ratio). */
  width: number;
  height: number;
};

export type Product = SceneImage & {
  id: string;
  /** 0..1 — higher = closer to camera: moves faster, appears larger. */
  depth: number;
  /** Resting position inside the stage, in % of stage width/height (center of the object). */
  x: number;
  y: number;
  /** Width as % of the stage box width. */
  size: number;
  /** Resting rotation in degrees. */
  rotate: number;
};

export type ServiceCard = {
  title: string;
  text: string;
  price: string;
  image: SceneImage;
  cta: Cta;
};

/**
 * Stage presets (choreography implemented in components/scene/timeline.ts):
 * hero      – hero object (chair) in the dark shop
 * products  – hero object leaves, products fly in with parallax
 * services  – glass service cards slide up, staggered
 * feature   – one big feature object (scissors) scales & rotates across the stage
 * orbit     – hero object returns, products orbit around it
 * cta       – hero object with a glowing animated gold ring
 */
export type StagePreset = "hero" | "products" | "services" | "feature" | "orbit" | "cta";

export type Chapter = {
  id: string;
  /** Optional anchor (e.g. "services") so nav links can jump to this chapter. */
  anchor?: string;
  /** Short label for the section dots. */
  label: string;
  stage: StagePreset;
  eyebrow?: string;
  /** Headline lines — each line gets its own masked reveal. */
  headline: string[];
  /** Index of the headline line rendered with the gold gradient (optional). */
  accentLine?: number;
  body?: string;
  ctas?: Cta[];
  /** [start, end] as fractions (0..1) of the pinned scroll length. Must be contiguous. */
  range: [number, number];
};

export type FooterColumn = { title: string; links: { label: string; href: string }[] };

// ────────────────────────────────────────────────────────────── config ──

export const siteConfig = {
  brand: {
    name: "THE BARBER NEWS",
    /** Short form shown on small screens. */
    shortName: "TBN",
    tagline: "Barbershop · Downtown",
    description:
      "Premium barbershop for precision haircuts, beard sculpting and styling. Book your appointment online.",
    locale: "en",
  },

  theme: {
    background: "#0a0a0a",
    gold: "#c9a45c",
    goldDeep: "#8a6d2f",
    text: "#f2efe9",
  },

  nav: [
    { label: "Home", href: "#home" },
    { label: "Services", href: "#services" },
    { label: "Downtown", href: "#downtown" },
    { label: "About", href: "#about" },
  ],

  bookCta: { label: "Book Appointment", action: "book", variant: "primary" } satisfies Cta,

  /** Pinned scene settings. */
  scene: {
    /** Total pinned scroll length in vh (the spec asks for ~600vh). */
    scrollLengthVh: 600,
    /** ScrollTrigger scrub smoothing in seconds (true = no smoothing). */
    scrub: 1,

    backdrop: {
      src: "/scene/backdrop.webp",
      alt: "Dark barbershop interior",
      width: 2560,
      height: 1440,
    } satisfies SceneImage,

    heroObject: {
      src: "/scene/chair.webp",
      alt: "Classic barber chair",
      width: 1200,
      height: 1400,
    } satisfies SceneImage,

    featureObject: {
      src: "/scene/scissors-large.webp",
      alt: "Barber scissors",
      width: 1600,
      height: 1600,
    } satisfies SceneImage,

    products: [
      { id: "clipper", src: "/scene/clipper.webp", alt: "Hair clipper", width: 800, height: 800, depth: 0.9, x: 30, y: 30, size: 26, rotate: -14 },
      { id: "comb", src: "/scene/comb.webp", alt: "Comb", width: 800, height: 800, depth: 0.45, x: 74, y: 22, size: 22, rotate: 22 },
      { id: "scissors", src: "/scene/scissors.webp", alt: "Scissors", width: 800, height: 800, depth: 0.7, x: 70, y: 70, size: 24, rotate: 32 },
      { id: "bottle", src: "/scene/bottle.webp", alt: "Grooming oil bottle", width: 800, height: 800, depth: 0.3, x: 36, y: 74, size: 19, rotate: -6 },
    ] satisfies Product[],

    /**
     * Optional technique A: scroll-scrubbed image sequence drawn on <canvas>
     * behind the layered objects. Enable once you have frames in /public/frames
     * (see scripts/extract-frames.*). Frame i is `${path}${pad(i)}.${ext}`, 1-based.
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
      anchor: "home",
      label: "Home",
      stage: "hero",
      eyebrow: "Barbershop · Est. 2024",
      headline: ["Precision.", "Style.", "Confidence."],
      accentLine: 1,
      body: "Classic craft meets modern style. Sharp cuts, clean fades and a chair that is always worth the wait.",
      ctas: [
        { label: "Book Appointment", action: "book", variant: "primary" },
        { label: "Explore Services", action: "#services", variant: "ghost" },
      ],
      range: [0, 0.14],
    },
    {
      id: "signature",
      label: "Experience",
      stage: "products",
      eyebrow: "Signature Experience",
      headline: ["Every detail", "is deliberate."],
      accentLine: 1,
      body: "Hot towels, straight razors and premium products, chosen by barbers who obsess over the finish.",
      range: [0.14, 0.32],
    },
    {
      id: "services",
      anchor: "services",
      label: "Services",
      stage: "services",
      eyebrow: "Services",
      headline: ["Crafted", "for you."],
      accentLine: 1,
      body: "From a sharp everyday cut to the full grooming ritual.",
      range: [0.32, 0.5],
    },
    {
      id: "craft",
      anchor: "about",
      label: "The Craft",
      stage: "feature",
      eyebrow: "About us",
      headline: ["The", "Craft."],
      accentLine: 1,
      body: "Trained hands, honest advice and techniques passed down through generations of barbers.",
      range: [0.5, 0.66],
    },
    {
      id: "premium",
      label: "Premium",
      stage: "orbit",
      eyebrow: "Premium Grooming Experience",
      headline: ["More than", "a haircut."],
      accentLine: 1,
      body: "Consultation, wash, cut, beard, hot towel and styling. Sit back, we take care of the rest.",
      range: [0.66, 0.84],
    },
    {
      id: "cta",
      label: "Book",
      stage: "cta",
      eyebrow: "Your chair is waiting",
      headline: ["Your next look", "starts here."],
      accentLine: 1,
      body: "Pick a service, choose your barber and book a time that suits you in under a minute.",
      ctas: [{ label: "Book Appointment", action: "book", variant: "primary" }],
      range: [0.84, 1],
    },
  ] satisfies Chapter[],

  services: [
    {
      title: "Haircut",
      text: "Consultation, precision cut and finish. Fades, scissor cuts, classics.",
      price: "from €35",
      image: { src: "/scene/service-haircut.webp", alt: "Haircut", width: 800, height: 600 },
      cta: { label: "Book", action: "book", variant: "ghost" },
    },
    {
      title: "Beard Sculpting",
      text: "Shape, line-up and hot towel treatment with straight-razor finish.",
      price: "from €25",
      image: { src: "/scene/service-beard.webp", alt: "Beard sculpting", width: 800, height: 600 },
      cta: { label: "Book", action: "book", variant: "ghost" },
    },
    {
      title: "Premium Styling",
      text: "Cut, beard, wash and styling. The complete grooming ritual.",
      price: "from €60",
      image: { src: "/scene/service-styling.webp", alt: "Premium styling", width: 800, height: 600 },
      cta: { label: "Book", action: "book", variant: "ghost" },
    },
  ] satisfies ServiceCard[],

  /** PLACEHOLDER contact data. Replace with the client's real details. */
  contact: {
    anchor: "downtown",
    addressLines: ["Musterstraße 1 (placeholder)", "10115 Berlin"],
    phone: "+49 30 0000000",
    email: "hello@example.com",
    hours: ["Mon–Fri 09:00–20:00", "Sat 09:00–18:00", "Sun closed"],
  },

  footer: {
    columns: [
      {
        title: "Explore",
        links: [
          { label: "Home", href: "#home" },
          { label: "Services", href: "#services" },
          { label: "About", href: "#about" },
          { label: "Downtown", href: "#downtown" },
        ],
      },
      {
        title: "Services",
        links: [
          { label: "Haircut", href: "#services" },
          { label: "Beard Sculpting", href: "#services" },
          { label: "Premium Styling", href: "#services" },
        ],
      },
    ] satisfies FooterColumn[],
    /** PLACEHOLDER social URLs. */
    socials: [
      { label: "Instagram", href: "https://instagram.com/", icon: "instagram" },
      { label: "Facebook", href: "https://facebook.com/", icon: "facebook" },
      { label: "TikTok", href: "https://tiktok.com/", icon: "tiktok" },
    ] as const,
    /** Legal pages are built in phase 4. */
    legal: [
      { label: "Impressum", href: "/impressum" },
      { label: "Datenschutz", href: "/datenschutz" },
    ],
    newsletter: {
      title: "Stay sharp",
      text: "Offers and new barbers, at most once a month.",
    },
  },
};

export type SiteConfig = typeof siteConfig;
