#!/usr/bin/env node
/**
 * Generates clearly labeled PLACEHOLDER scene assets into /public/scene.
 * Every image carries a "PLACEHOLDER · <file> · <size>" tag so nobody mistakes
 * them for final artwork. Replace them with real assets (see public/scene/README.md).
 *
 *   node scripts/generate-placeholders.mjs            # scene assets only
 *   node scripts/generate-placeholders.mjs --frames 150  # + placeholder frame sequence in /public/frames
 *
 * Requires `sharp` (dev dependency).
 */
import sharp from "sharp";
import { mkdir, writeFile } from "node:fs/promises";
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

/** Small pill label marking the image as a placeholder. */
function tag(w, h, text, { y = h - 40, size = Math.max(16, Math.round(w / 40)) } = {}) {
  const pw = Math.round(text.length * size * 0.6 + size * 2);
  const ph = Math.round(size * 2);
  const x = Math.round((w - pw) / 2);
  return `
    <g font-family="Arial, Helvetica, sans-serif" font-weight="700">
      <rect x="${x}" y="${y - ph}" width="${pw}" height="${ph}" rx="${ph / 2}" fill="#000" fill-opacity=".72" stroke="${GOLD}" stroke-width="2" stroke-dasharray="8 6"/>
      <text x="${w / 2}" y="${y - ph / 2 + size * 0.36}" font-size="${size}" fill="${GOLD}" text-anchor="middle" letter-spacing="2">${text}</text>
    </g>`;
}

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
    <path d="M808 420 Q838 600 820 880" fill="none" stroke="#ffe3a3" stroke-opacity=".7" stroke-width="4"/>
    ${tag(w, h, "PLACEHOLDER · chair.webp · 1200×1400 · transparent", { y: 140, size: 30 })}`);
}

function clipper() {
  const w = 800, h = 800;
  return svg(w, h, `
    <g transform="rotate(-20 400 400)">
      <rect x="300" y="200" width="200" height="440" rx="90" fill="url(#dark)" stroke="url(#gold)" stroke-width="6"/>
      <rect x="290" y="150" width="220" height="70" rx="14" fill="url(#steel)"/>
      <g stroke="#555" stroke-width="5">${Array.from({ length: 9 }, (_, i) => `<line x1="${306 + i * 24}" y1="150" x2="${306 + i * 24}" y2="128"/>`).join("")}</g>
      <rect x="300" y="420" width="200" height="34" fill="url(#gold)"/>
      <circle cx="400" cy="330" r="20" fill="${GOLD}"/>
    </g>
    ${tag(w, h, "PLACEHOLDER · clipper.webp")}`);
}

function comb() {
  const w = 800, h = 800;
  const teeth = Array.from({ length: 30 }, (_, i) => `<rect x="${172 + i * 15}" y="400" width="8" height="${i < 12 ? 120 : 90}" rx="3"/>`).join("");
  return svg(w, h, `
    <g transform="rotate(25 400 400)">
      <rect x="160" y="340" width="480" height="70" rx="18" fill="url(#dark)" stroke="url(#gold)" stroke-width="5"/>
      <g fill="#1d1d1d" stroke="${GOLD}" stroke-opacity=".5" stroke-width="1.5">${teeth}</g>
    </g>
    ${tag(w, h, "PLACEHOLDER · comb.webp")}`);
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

function scissors() {
  return svg(800, 800, `${scissorsShape()}${tag(800, 800, "PLACEHOLDER · scissors.webp")}`);
}

function scissorsLarge() {
  // Same drawing scaled 2x on a 1600 canvas.
  return svg(1600, 1600, `<g transform="scale(2)">${scissorsShape()}</g>${tag(1600, 1600, "PLACEHOLDER · scissors-large.webp · 1600×1600", { size: 34 })}`);
}

function bottle() {
  const w = 800, h = 800;
  return svg(w, h, `
    <rect x="360" y="110" width="80" height="70" rx="10" fill="url(#gold)"/>
    <rect x="375" y="175" width="50" height="60" fill="url(#dark)"/>
    <path d="M300 300 Q300 235 375 235 L425 235 Q500 235 500 300 L500 660 Q500 690 470 690 L330 690 Q300 690 300 660 Z" fill="#3b2410" fill-opacity=".9" stroke="url(#gold)" stroke-width="6"/>
    <rect x="320" y="380" width="160" height="190" rx="8" fill="#0d0d0d" stroke="${GOLD}" stroke-width="3"/>
    <text x="400" y="470" font-family="Georgia, serif" font-size="34" fill="${GOLD}" text-anchor="middle">OIL</text>
    <text x="400" y="510" font-family="Arial" font-size="16" fill="${GOLD}" text-anchor="middle" letter-spacing="4">NO. 01</text>
    <path d="M330 300 L330 640" stroke="#fff" stroke-opacity=".25" stroke-width="10" stroke-linecap="round"/>
    ${tag(w, h, "PLACEHOLDER · bottle.webp")}`);
}

function backdrop() {
  const w = 2560, h = 1440;
  const mirrors = [0, 1, 2]
    .map((i) => {
      const x = 1180 + i * 430;
      return `<rect x="${x}" y="260" width="320" height="520" rx="160" fill="url(#mirror)" stroke="url(#gold)" stroke-opacity=".55" stroke-width="6"/>`;
    })
    .join("");
  const lamps = [0, 1, 2]
    .map((i) => {
      const x = 1340 + i * 430;
      return `<line x1="${x}" y1="0" x2="${x}" y2="150" stroke="#222" stroke-width="4"/>
        <circle cx="${x}" cy="175" r="240" fill="url(#lamp)"/>
        <path d="M${x - 40} 150 L${x + 40} 150 L${x + 26} 190 L${x - 26} 190 Z" fill="${GOLD_DEEP}"/>`;
    })
    .join("");
  return svg(w, h, `
    <defs>
      <linearGradient id="wall" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#16120e"/><stop offset=".62" stop-color="#0d0b09"/><stop offset=".62" stop-color="#090807"/><stop offset="1" stop-color="#040404"/></linearGradient>
      <linearGradient id="mirror" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#2a2622"/><stop offset=".5" stop-color="#14120f"/><stop offset="1" stop-color="#0a0908"/></linearGradient>
      <radialGradient id="lamp" cx=".5" cy=".5" r=".5"><stop offset="0" stop-color="#ffcf7a" stop-opacity=".35"/><stop offset="1" stop-color="#ffcf7a" stop-opacity="0"/></radialGradient>
    </defs>
    <rect width="${w}" height="${h}" fill="url(#wall)"/>
    <rect x="1080" y="840" width="1400" height="30" fill="#1a1612"/>
    ${mirrors}${lamps}
    <text x="${w * 0.66}" y="1180" font-family="Arial" font-weight="700" font-size="56" fill="${GOLD}" fill-opacity=".22" text-anchor="middle" letter-spacing="10">PLACEHOLDER BACKDROP</text>
    ${tag(w, h, "PLACEHOLDER · backdrop.webp · 2560×1440 · opaque", { size: 30 })}`);
}

function serviceImage(name, title, glyph) {
  const w = 800, h = 600;
  return svg(w, h, `
    <defs><radialGradient id="bg" cx=".7" cy=".3" r=".9"><stop offset="0" stop-color="#3a2c18"/><stop offset="1" stop-color="#0c0b0a"/></radialGradient></defs>
    <rect width="${w}" height="${h}" fill="url(#bg)"/>
    <g transform="translate(400 260)">${glyph}</g>
    <text x="400" y="450" font-family="Arial" font-weight="700" font-size="38" fill="${GOLD}" text-anchor="middle" letter-spacing="6">${title}</text>
    ${tag(w, h, `PLACEHOLDER · ${name} · 800×600`, { size: 20 })}`);
}

const glyphs = {
  haircut: `<g transform="scale(.32) translate(-400 -400)">${scissorsShape()}</g>`,
  beard: `<path d="M-110 -90 Q-120 60 0 120 Q120 60 110 -90 Q60 10 0 10 Q-60 10 -110 -90 Z" fill="none" stroke="url(#gold)" stroke-width="10"/>`,
  styling: `<g fill="none" stroke="url(#gold)" stroke-width="10"><path d="M-90 80 Q-100 -60 0 -110 Q100 -60 90 80"/><path d="M-40 -60 Q0 -90 40 -60"/></g>`,
};

function frame(i, total) {
  const w = 1920, h = 1080;
  const t = i / total;
  const hue = Math.round(30 + 15 * Math.sin(t * Math.PI * 2));
  return svg(w, h, `
    <defs><radialGradient id="fbg" cx="${0.3 + 0.4 * t}" cy=".45" r=".8"><stop offset="0" stop-color="hsl(${hue} 45% 22%)"/><stop offset="1" stop-color="#060606"/></radialGradient></defs>
    <rect width="${w}" height="${h}" fill="url(#fbg)"/>
    <text x="${w * 0.68}" y="${h / 2 + 60}" font-family="Arial" font-weight="700" font-size="180" fill="${GOLD}" fill-opacity=".25" text-anchor="middle">${String(i).padStart(4, "0")}</text>
    ${tag(w, h, `PLACEHOLDER FRAME · frame_${String(i).padStart(4, "0")}.webp`, { size: 26 })}`);
}

// ─────────────────────────────────────────────────────────── output ──

async function write(dir, file, svgString, opts = {}) {
  const out = path.join(dir, file);
  await sharp(Buffer.from(svgString)).webp({ quality: opts.quality ?? 82, alphaQuality: 90 }).toFile(out);
  console.log("  ✓", path.relative(root, out));
}

async function main() {
  await mkdir(sceneDir, { recursive: true });
  console.log("Generating placeholder scene assets…");
  await write(sceneDir, "backdrop.webp", backdrop(), { quality: 70 });
  await write(sceneDir, "chair.webp", chair());
  await write(sceneDir, "clipper.webp", clipper());
  await write(sceneDir, "comb.webp", comb());
  await write(sceneDir, "scissors.webp", scissors());
  await write(sceneDir, "bottle.webp", bottle());
  await write(sceneDir, "scissors-large.webp", scissorsLarge());
  await write(sceneDir, "service-haircut.webp", serviceImage("service-haircut.webp", "HAIRCUT", glyphs.haircut));
  await write(sceneDir, "service-beard.webp", serviceImage("service-beard.webp", "BEARD", glyphs.beard));
  await write(sceneDir, "service-styling.webp", serviceImage("service-styling.webp", "STYLING", glyphs.styling));

  const idx = process.argv.indexOf("--frames");
  if (idx !== -1) {
    const total = Number(process.argv[idx + 1] ?? 150);
    await mkdir(framesDir, { recursive: true });
    console.log(`Generating ${total} placeholder frames…`);
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
