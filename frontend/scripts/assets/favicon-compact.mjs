#!/usr/bin/env node
/**
 * Rewrites src/app/favicon.ico with PNG-compressed 32×32 and 16×16 entries (taken from the
 * existing BMP entries). The browser fetches the favicon early with high priority, so its size
 * competes with the first screen on slow phones: 15 KB (3 BMP icons) → ~2 KB.
 *   node scripts/assets/favicon-compact.mjs
 */
import fs from "node:fs";
import sharp from "sharp";

const FILE = "src/app/favicon.ico";
const ico = fs.readFileSync(FILE);
const count = ico.readUInt16LE(4);

/** Decodes a 32-bit BMP icon entry (bottom-up BGRA) to top-down RGBA. */
function bmpToRgba(buf, size) {
  const header = buf.readUInt32LE(0);
  const bpp = buf.readUInt16LE(14);
  if (bpp !== 32) throw new Error(`only 32-bit BMP icon entries are supported (got ${bpp})`);
  const rgba = Buffer.alloc(size * size * 4);
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const src = header + ((size - 1 - y) * size + x) * 4;
      const dst = (y * size + x) * 4;
      rgba[dst] = buf[src + 2];
      rgba[dst + 1] = buf[src + 1];
      rgba[dst + 2] = buf[src];
      rgba[dst + 3] = buf[src + 3];
    }
  }
  return rgba;
}

const entries = [];
for (let i = 0; i < count; i++) {
  const o = 6 + i * 16;
  const size = ico[o] || 256;
  const bytes = ico.readUInt32LE(o + 8);
  const offset = ico.readUInt32LE(o + 12);
  if (size !== 32 && size !== 16) continue;
  const data = ico.subarray(offset, offset + bytes);
  const isPng = data.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]));
  const png = isPng
    ? data
    : await sharp(bmpToRgba(data, size), { raw: { width: size, height: size, channels: 4 } })
        .png({ compressionLevel: 9, palette: true })
        .toBuffer();
  entries.push({ size, png });
}
entries.sort((a, b) => b.size - a.size);

const header = Buffer.alloc(6 + entries.length * 16);
header.writeUInt16LE(0, 0);
header.writeUInt16LE(1, 2);
header.writeUInt16LE(entries.length, 4);
let offset = header.length;
entries.forEach((e, i) => {
  const o = 6 + i * 16;
  header[o] = e.size;
  header[o + 1] = e.size;
  header.writeUInt16LE(1, o + 4); // planes
  header.writeUInt16LE(32, o + 6); // bit count
  header.writeUInt32LE(e.png.length, o + 8);
  header.writeUInt32LE(offset, o + 12);
  offset += e.png.length;
});
const out = Buffer.concat([header, ...entries.map((e) => e.png)]);
fs.writeFileSync(FILE, out);
console.log(`${FILE}: ${ico.length} → ${out.length} bytes (${entries.map((e) => e.size).join(", ")} px, PNG)`);
