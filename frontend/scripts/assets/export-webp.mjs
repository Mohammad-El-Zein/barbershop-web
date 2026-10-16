#!/usr/bin/env node
/**
 * Final step of the collage asset pipeline: grade + export WebP into public/scene.
 *
 *   node scripts/assets/export-webp.mjs <workdir>
 *
 * <workdir> must contain (produced by the earlier steps, see public/scene/README.md):
 *   final/{chair,clipper,comb,scissors,bottle}.png   RGBA cut-outs (matte.py)
 *   final/backdrop.png                               treated 16:9 backdrop (backdrop.py)
 *   up/service-{haircut,beard,styling}.png           Real-ESRGAN x4 service photos
 *
 * Also copies the RGBA cut-out masters to assets-raw/cutouts/.
 */
import sharp from "sharp";
import { copyFile, mkdir } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");
const work = path.resolve(process.argv[2] ?? ".");
const out = path.join(root, "public", "scene");
const cutouts = path.join(root, "assets-raw", "cutouts");
const QUALITY = 85;

/**
 * Shared grade so studio-lit product shots sit in the dark, warm scene:
 * a touch darker, slightly desaturated, warm (less blue). `lift` raises the
 * blacks a little (used for the black comb so its teeth don't vanish on #0a0a0a).
 */
function grade(img, { brightness = 0.95, saturation = 0.9, lift = 0, alpha = true } = {}) {
  const mul = [1.03, 1.0, 0.92];
  const add = [lift, lift * 0.9, lift * 0.7];
  return img
    .modulate({ brightness, saturation })
    .linear(alpha ? [...mul, 1] : mul, alpha ? [...add, 0] : add);
}

async function save(img, name) {
  const file = path.join(out, `${name}.webp`);
  const info = await img.webp({ quality: QUALITY, alphaQuality: 90, effort: 6 }).toFile(file);
  console.log(`  ✓ ${name}.webp  ${info.width}×${info.height}  ${(info.size / 1024).toFixed(0)} KB`);
  return info;
}

await mkdir(out, { recursive: true });
await mkdir(cutouts, { recursive: true });

console.log("Objects (transparent):");
for (const [name, opts] of [
  ["chair", {}],
  ["clipper", {}],
  ["comb", { lift: 14, brightness: 1.0 }],
  ["scissors", {}],
  ["bottle", {}],
]) {
  const src = path.join(work, "final", `${name}.png`);
  await copyFile(src, path.join(cutouts, `${name}.png`));
  await save(grade(sharp(src).ensureAlpha(), opts), name);
}

// Feature object: same scissors on a square transparent canvas (the stage box is square-ish).
{
  const src = sharp(path.join(work, "final", "scissors.png"));
  const { width, height } = await src.metadata();
  const side = Math.max(width, height) + 24;
  const canvas = sharp({ create: { width: side, height: side, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } } })
    .composite([{ input: await grade(src.ensureAlpha()).png().toBuffer(), left: Math.round((side - width) / 2), top: Math.round((side - height) / 2) }]);
  await save(sharp(await canvas.png().toBuffer()), "scissors-large");
}

console.log("Photos (opaque):");
await save(grade(sharp(path.join(work, "final", "backdrop.png")).resize(2560, 1440, { fit: "cover", kernel: "lanczos3" }), { brightness: 0.95, saturation: 0.95, alpha: false }), "backdrop");
for (const name of ["service-haircut", "service-beard", "service-styling"]) {
  await save(
    grade(sharp(path.join(work, "up", `${name}.png`)).resize(1280, 800, { fit: "cover", kernel: "lanczos3" }), { brightness: 0.97, saturation: 0.95, alpha: false }),
    name,
  );
}
