/**
 * npm run check:messages — every text key must exist in DE and EN (landing + app files).
 */
import { readFileSync } from "node:fs";

const load = (f) => JSON.parse(readFileSync(new URL(`../messages/${f}`, import.meta.url), "utf8"));
const keys = (o, p = "") =>
  Object.entries(o).flatMap(([k, v]) =>
    v && typeof v === "object" && !Array.isArray(v) ? keys(v, `${p}${k}.`) : [`${p}${k}`],
  );

let failed = false;
for (const [a, b] of [["de.json", "en.json"], ["app.de.json", "app.en.json"]]) {
  const ka = new Set(keys(load(a)));
  const kb = new Set(keys(load(b)));
  const onlyA = [...ka].filter((k) => !kb.has(k));
  const onlyB = [...kb].filter((k) => !ka.has(k));
  if (onlyA.length || onlyB.length) {
    failed = true;
    if (onlyA.length) console.error(`only in ${a}:`, onlyA);
    if (onlyB.length) console.error(`only in ${b}:`, onlyB);
  } else console.log(`${a} / ${b}: ${ka.size} keys, identical`);
}
process.exit(failed ? 1 : 0);
