#!/usr/bin/env node
/**
 * Generates PLACEHOLDER scene artwork into /public/scene.
 *
 * The images are deliberately clean (no text, no labels baked in) so the page
 * looks finished during demos. To see which file is which, set
 * `debug.showPlaceholderLabels = true` in src/config/site.config.ts — the labels
 * are then drawn as a DOM overlay in development, never inside the images.
 * The list of final assets you must supply is in public/scene/README.md.
 *
 *   node scripts/generate-placeholders.mjs               # only files that do not exist yet
 *   node scripts/generate-placeholders.mjs --force       # overwrite existing files (destroys real assets!)
 *   node scripts/generate-placeholders.mjs --frames 150  # + test frame sequence in /public/frames
 *
 * Existing files are never overwritten without --force, so real assets in
 * public/scene (see scripts/assets/) are safe.
 *
 * Product cut-outs are trimmed to their silhouette (like real cut-out photos),
 * so `size` in the config maps to the visible object width.
 *
 * Requires `sharp` (dev dependency).
 */
import sharp from "sharp";
import { access, mkdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const sceneDir = path.join(root, "public", "scene");
const framesDir = path.join(root, "public", "frames");

const GOLD = "#c9a45c";
const GOLD_DEEP = "#8a6d2f";

// Shared gradient definitions used by all object drawings.
const defs = `
  <defs>
    <linearGradient id="gold" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#f1d796"/><stop offset=".45" stop-color="${GOLD}"/><stop offset="1" stop-color="${GOLD_DEEP}"/>
    </linearGradient>
    <linearGradient id="leather" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#3a2a1d"/><stop offset=".6" stop-color="#1b130d"/><stop offset="1" stop-color="#0d0906"/>
    </linearGradient>
    <linearGradient id="chrome" x1="0" y1="0" x2="1" y2="0">
      <stop offset="0" stop-color="#2a2a2a"/><stop offset=".35" stop-color="#9a9a9a"/><stop offset=".55" stop-color="#e8e8e8"/><stop offset="1" stop-color="#3a3a3a"/>
    </linearGradient>
    <linearGradient id="steel" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#f4f4f4"/><stop offset=".5" stop-color="#9da3a8"/><stop offset="1" stop-color="#4b5056"/>
    </linearGradient>
    <linearGradient id="dark" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#2b2b2b"/><stop offset="1" stop-color="#0f0f0f"/>
    </linearGradient>
    <radialGradient id="shadow" cx=".5" cy=".5" r=".5">
      <stop offset="0" stop-color="#000" stop-opacity=".75"/><stop offset="1" stop-color="#000" stop-opacity="0"/>
    </radialGradient>
  </defs>`;

const svg = (w, h, body) =>
  `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">${defs}${body}</svg>`;

// ───────────────────────────────────────────────────────── drawings ──

function chair() {
  const w = 1200, h = 1400;
  return svg(w, h, `
    <ellipse cx="600" cy="1290" rx="430" ry="70" fill="url(#shadow)"/>
    <!-- base + pole -->
    <ellipse cx="600" cy="1262" rx="290" ry="46" fill="url(#dark)" stroke="url(#gold)" stroke-width="5"/>
    <rect x="566" y="1000" width="68" height="262" rx="12" fill="url(#chrome)"/>
    <rect x="520" y="1000" width="160" height="40" rx="14" fill="url(#chrome)"/>
    <!-- footrest -->
    <path d="M430 1010 L380 1150 L820 1150 L770 1010" fill="none" stroke="url(#chrome)" stroke-width="18" stroke-linejoin="round"/>
    <rect x="360" y="1136" width="480" height="34" rx="10" fill="url(#chrome)"/>
    <!-- backrest -->
    <path d="M380 880 Q362 600 392 420 Q402 372 452 368 L748 368 Q798 372 808 420 Q838 600 820 880 Z" fill="url(#leather)" stroke="url(#gold)" stroke-width="6"/>
    <g stroke="#000" stroke-opacity=".45" stroke-width="5" fill="none">
      <path d="M470 430 L470 840"/><path d="M600 420 L600 850"/><path d="M730 430 L730 840"/>
    </g>
    <g fill="${GOLD}" fill-opacity=".5">
      <circle cx="535" cy="520" r="7"/><circle cx="665" cy="520" r="7"/><circle cx="535" cy="680" r="7"/><circle cx="665" cy="680" r="7"/>
    </g>
    <!-- headrest -->
    <rect x="585" y="300" width="30" height="80" fill="url(#chrome)"/>
    <rect x="470" y="230" width="260" height="96" rx="44" fill="url(#leather)" stroke="url(#gold)" stroke-width="5"/>
    <!-- seat -->
    <rect x="300" y="850" width="600" height="160" rx="50" fill="url(#leather)" stroke="url(#gold)" stroke-width="6"/>
    <!-- armrests -->
    <rect x="230" y="740" width="200" height="56" rx="28" fill="url(#leather)" stroke="url(#gold)" stroke-width="5"/>
    <rect x="770" y="740" width="200" height="56" rx="28" fill="url(#leather)" stroke="url(#gold)" stroke-width="5"/>
    <rect x="300" y="796" width="26" height="80" fill="url(#chrome)"/><rect x="874" y="796" width="26" height="80" fill="url(#chrome)"/>
    <!-- rim light -->
    <path d="M808 420 Q838 600 820 880" fill="none" stroke="#ffe3a3" stroke-opacity=".7" stroke-width="4"/>`);
}

function clipper() {
  return svg(800, 800, `
    <g transform="rotate(-20 400 400)">
      <rect x="300" y="200" width="200" height="440" rx="90" fill="url(#dark)" stroke="url(#gold)" stroke-width="6"/>
      <rect x="290" y="150" width="220" height="70" rx="14" fill="url(#steel)"/>
      <g stroke="#555" stroke-width="5">${Array.from({ length: 9 }, (_, i) => `<line x1="${306 + i * 24}" y1="150" x2="${306 + i * 24}" y2="128"/>`).join("")}</g>
      <rect x="300" y="420" width="200" height="34" fill="url(#gold)"/>
      <circle cx="400" cy="330" r="20" fill="${GOLD}"/>
      <path d="M330 260 L330 600" stroke="#fff" stroke-opacity=".12" stroke-width="12" stroke-linecap="round"/>
    </g>`);
}

function comb() {
  const teeth = Array.from({ length: 30 }, (_, i) => `<rect x="${172 + i * 15}" y="400" width="8" height="${i < 12 ? 120 : 90}" rx="3"/>`).join("");
  return svg(800, 800, `
    <g transform="rotate(25 400 400)">
      <rect x="160" y="340" width="480" height="70" rx="18" fill="url(#dark)" stroke="url(#gold)" stroke-width="5"/>
      <g fill="#1d1d1d" stroke="${GOLD}" stroke-opacity=".5" stroke-width="1.5">${teeth}</g>
    </g>`);
}

function scissorsShape() {
  return `
    <g transform="rotate(-35 400 400)">
      <path d="M400 410 L170 120 Q160 104 178 110 L420 380 Z" fill="url(#steel)"/>
      <path d="M400 410 L630 120 Q640 104 622 110 L380 380 Z" fill="url(#steel)"/>
      <circle cx="400" cy="400" r="16" fill="url(#gold)"/>
      <path d="M392 420 L300 560" stroke="url(#gold)" stroke-width="26" stroke-linecap="round"/>
      <path d="M408 420 L500 560" stroke="url(#gold)" stroke-width="26" stroke-linecap="round"/>
      <ellipse cx="280" cy="620" rx="72" ry="58" fill="none" stroke="url(#gold)" stroke-width="22"/>
      <ellipse cx="520" cy="620" rx="72" ry="58" fill="none" stroke="url(#gold)" stroke-width="22"/>
    </g>`;
}

const scissors = () => svg(800, 800, scissorsShape());
// Same drawing at 2x for the big "The Craft" feature object.
const scissorsLarge = () => svg(1600, 1600, `<g transform="scale(2)">${scissorsShape()}</g>`);

function bottle() {
  // Label uses an emblem instead of text so nothing reads as baked-in copy.
  return svg(800, 800, `
    <rect x="360" y="110" width="80" height="70" rx="10" fill="url(#gold)"/>
    <rect x="375" y="175" width="50" height="60" fill="url(#dark)"/>
    <path d="M300 300 Q300 235 375 235 L425 235 Q500 235 500 300 L500 660 Q500 690 470 690 L330 690 Q300 690 300 660 Z" fill="#3b2410" fill-opacity=".92" stroke="url(#gold)" stroke-width="6"/>
    <rect x="320" y="380" width="160" height="190" rx="8" fill="#0d0d0d" stroke="${GOLD}" stroke-width="3"/>
    <g transform="translate(400 470)" fill="none" stroke="${GOLD}" stroke-width="3">
      <circle r="38"/><path d="M-16 18 L16 -18 M16 18 L-16 -18"/><circle cx="-20" cy="22" r="7"/><circle cx="20" cy="22" r="7"/>
    </g>
    <path d="M330 300 L330 640" stroke="#fff" stroke-opacity=".25" stroke-width="10" stroke-linecap="round"/>`);
}

function backdrop() {
  // Lamps hang well below the top edge so they never sit under the nav bar.
  const w = 2560, h = 1440;
  const mirrors = [0, 1, 2]
    .map((i) => `<rect x="${1180 + i * 430}" y="420" width="320" height="480" rx="160" fill="url(#mirror)" stroke="url(#gold)" stroke-opacity=".55" stroke-width="6"/>`)
    .join("");
  const lamps = [0, 1, 2]
    .map((i) => {
      const x = 1340 + i * 430;
      return `<line x1="${x}" y1="0" x2="${x}" y2="300" stroke="#1a1a1a" stroke-width="4"/>
        <circle cx="${x}" cy="330" r="240" fill="url(#lamp)"/>
        <path d="M${x - 40} 300 L${x + 40} 300 L${x + 26} 340 L${x - 26} 340 Z" fill="${GOLD_DEEP}"/>`;
    })
    .join("");
  return svg(w, h, `
    <defs>
      <linearGradient id="wall" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#16120e"/><stop offset=".68" stop-color="#0d0b09"/><stop offset=".68" stop-color="#090807"/><stop offset="1" stop-color="#040404"/></linearGradient>
      <linearGradient id="mirror" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#2a2622"/><stop offset=".5" stop-color="#14120f"/><stop offset="1" stop-color="#0a0908"/></linearGradient>
      <radialGradient id="lamp" cx=".5" cy=".5" r=".5"><stop offset="0" stop-color="#ffcf7a" stop-opacity=".35"/><stop offset="1" stop-color="#ffcf7a" stop-opacity="0"/></radialGradient>
    </defs>
    <rect width="${w}" height="${h}" fill="url(#wall)"/>
    <rect x="1080" y="960" width="1400" height="30" fill="#1a1612"/>
    ${mirrors}${lamps}`);
}

function serviceImage(glyph) {
  const w = 800, h = 600;
  return svg(w, h, `
    <defs><radialGradient id="bg" cx=".7" cy=".3" r=".9"><stop offset="0" stop-color="#3a2c18"/><stop offset="1" stop-color="#0c0b0a"/></radialGradient></defs>
    <rect width="${w}" height="${h}" fill="url(#bg)"/>
    <g transform="translate(400 300)">${glyph}</g>`);
}

const glyphs = {
  haircut: `<g transform="scale(.36) translate(-400 -400)">${scissorsShape()}</g>`,
  beard: `<path d="M-110 -90 Q-120 60 0 120 Q120 60 110 -90 Q60 10 0 10 Q-60 10 -110 -90 Z" fill="none" stroke="url(#gold)" stroke-width="10"/>`,
  styling: `<g fill="none" stroke="url(#gold)" stroke-width="10"><path d="M-90 80 Q-100 -60 0 -110 Q100 -60 90 80"/><path d="M-40 -60 Q0 -90 40 -60"/></g>`,
};

/** Test frame for the optional canvas sequence: a slowly moving warm light, no text. */
function frame(i, total) {
  const w = 1920, h = 1080;
  const t = i / total;
  const hue = Math.round(30 + 15 * Math.sin(t * Math.PI * 2));
  return svg(w, h, `
    <defs><radialGradient id="fbg" cx="${0.3 + 0.4 * t}" cy=".45" r=".8"><stop offset="0" stop-color="hsl(${hue} 45% 22%)"/><stop offset="1" stop-color="#060606"/></radialGradient></defs>
    <rect width="${w}" height="${h}" fill="url(#fbg)"/>`);
}

// ─────────────────────────────────────────────────────────── output ──

const FORCE = process.argv.includes("--force");
const exists = (f) => access(f).then(() => true, () => false);

async function write(file, svgString, { quality = 82, trim = false } = {}) {
  const out = path.join(sceneDir, file);
  if (!FORCE && (await exists(out))) {
    console.log(`  – ${path.relative(root, out)} exists, skipped (use --force to overwrite)`);
    return;
  }
  let img = sharp(Buffer.from(svgString));
  if (trim) {
    // Crop to the silhouette (+ a few px), then cap the long side at 800px.
    const trimmed = await img.trim({ threshold: 1 }).png().toBuffer();
    img = sharp(trimmed).extend({ top: 6, bottom: 6, left: 6, right: 6, background: { r: 0, g: 0, b: 0, alpha: 0 } })
      .resize(800, 800, { fit: "inside", withoutEnlargement: true });
  }
  const info = await img.webp({ quality, alphaQuality: 90 }).toFile(out);
  console.log(`  ✓ ${path.relative(root, out)}  ${info.width}×${info.height}`);
}

async function main() {
  await mkdir(sceneDir, { recursive: true });
  console.log(`Generating placeholder scene assets (no text baked in)${FORCE ? ", overwriting" : ""}…`);
  await write("backdrop.webp", backdrop(), { quality: 70 });
  await write("chair.webp", chair());
  await write("clipper.webp", clipper(), { trim: true });
  await write("comb.webp", comb(), { trim: true });
  await write("scissors.webp", scissors(), { trim: true });
  await write("bottle.webp", bottle(), { trim: true });
  await write("scissors-large.webp", scissorsLarge());
  await write("service-haircut.webp", serviceImage(glyphs.haircut));
  await write("service-beard.webp", serviceImage(glyphs.beard));
  await write("service-styling.webp", serviceImage(glyphs.styling));

  const idx = process.argv.indexOf("--frames");
  if (idx !== -1) {
    const total = Number(process.argv[idx + 1] ?? 150);
    await mkdir(framesDir, { recursive: true });
    console.log(`Generating ${total} test frames…`);
    for (let i = 1; i <= total; i++) {
      const buf = await sharp(Buffer.from(frame(i, total))).webp({ quality: 60 }).toBuffer();
      await writeFile(path.join(framesDir, `frame_${String(i).padStart(4, "0")}.webp`), buf);
    }
    console.log("  ✓ public/frames/frame_0001.webp …", `frame_${String(total).padStart(4, "0")}.webp`);
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
