// Generates the lighter phone variants of the landing-page scene assets.
//   node scripts/assets/mobile-variants.mjs
// Re-run after replacing a source image (backdrop, chair poster, turntable frames).
//
// - backdrop-1280.webp       backdrop for phones / small screens (srcset)
// - chair-640.webp           chair poster (= turntable frame 0) for phones (srcset)
// - chair-turntable/400/     phone turntable set: every 2nd frame, 400 px tall
//                            (manifest set with "step": 2 — the canvas shows the
//                            nearest loaded frame, the lerp hides the gaps)
import { readdirSync, readFileSync, statSync, writeFileSync, mkdirSync } from "node:fs";
import { join } from "node:path";
import sharp from "sharp";

const scene = join(process.cwd(), "public", "scene");
const tt = join(scene, "chair-turntable");

async function out(file, pipeline) {
  await pipeline.toFile(file);
  return statSync(file).size;
}

const sizes = {};
sizes.backdrop = await out(join(scene, "backdrop-1280.webp"), sharp(join(scene, "backdrop.webp")).resize({ width: 1280 }).webp({ quality: 62, effort: 6 }));
sizes.chair = await out(join(scene, "chair-640.webp"), sharp(join(scene, "chair.webp")).resize({ width: 640 }).webp({ quality: 72, effort: 6, alphaQuality: 80 }));

// phone turntable set from the largest set
const manifestPath = join(tt, "manifest.json");
const manifest = JSON.parse(readFileSync(manifestPath, "utf8"));
const largest = [...manifest.sets].sort((a, b) => b.height - a.height)[0];
const STEP = 2;
const HEIGHT = 400;
const dir = join(tt, String(HEIGHT));
mkdirSync(dir, { recursive: true });
let bytes = 0;
let width = 0;
const files = readdirSync(join(tt, largest.dir)).filter((f) => f.endsWith(".webp")).sort();
for (const f of files) {
  const index = Number(f.replace(".webp", ""));
  if (index % STEP !== 0 && index !== manifest.frames - 1) continue; // keep every 2nd + the last frame
  const info = await sharp(join(tt, largest.dir, f))
    .resize({ height: HEIGHT })
    .webp({ quality: 68, effort: 6, alphaQuality: 75 })
    .toFile(join(dir, f));
  bytes += info.size;
  width = info.width;
}
manifest.sets = manifest.sets.filter((s) => s.dir !== String(HEIGHT));
manifest.sets.push({ height: HEIGHT, width, dir: String(HEIGHT), step: STEP, bytes });
writeFileSync(manifestPath, JSON.stringify(manifest, null, 2) + "\n");

console.log(
  `backdrop-1280 ${Math.round(sizes.backdrop / 1024)} KB · chair-640 ${Math.round(sizes.chair / 1024)} KB · ` +
    `turntable/${HEIGHT}: ${Math.ceil(manifest.frames / STEP)} frames, ${Math.round(bytes / 1024)} KB`,
);
