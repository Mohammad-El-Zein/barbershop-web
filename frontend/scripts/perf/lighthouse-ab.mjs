#!/usr/bin/env node
/**
 * A/B performance measurement with ONE fixed method — use it for every before/after claim.
 *
 *   node scripts/perf/lighthouse-ab.mjs --a http://localhost:3101 --b http://localhost:3102 \
 *        [--pages lp,landing,booking] [--runs 5] [--form mobile|desktop] [--out perf-results]
 *
 * - Lighthouse 13.5.0 (devDependency, the engine Lighthouse CI runs), performance category only.
 * - Simulated throttling with the values PINNED (not left to defaults that change between
 *   versions): mobile = Moto G Power emulation, RTT 150 ms, 1.6 Mbit/s, CPU slowdown 4×;
 *   desktop = 40 ms, 10 Mbit/s, CPU 1×.
 * - A and B alternate run by run (A B, B A, A B, …) so machine drift hits both equally.
 * - Reports the median per variant (score, LCP, TBT, CLS, FCP) and every single run.
 * Run both variants as production builds on the same machine at the same time (e.g. two
 * containers of the frontend image), nothing else CPU-heavy in parallel.
 * Needs Chrome: set CHROME_PATH (e.g. Playwright's Chromium) if none is installed.
 */
import { execFileSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const args = Object.fromEntries(
  process.argv.slice(2).reduce((acc, v, i, all) => (v.startsWith("--") ? [...acc, [v.slice(2), all[i + 1]]] : acc), []),
);
const A = args.a;
const B = args.b;
if (!A || !B) {
  console.error("usage: --a <base url of variant A> --b <base url of variant B>");
  process.exit(2);
}
const RUNS = Number(args.runs ?? 5);
const FORM = args.form ?? "mobile";
const OUT = path.resolve(args.out ?? "perf-results");
const PAGES = {
  landing: "/de",
  lp: "/de/lp/herbst-fade",
  booking: "/de/booking",
};
const pages = (args.pages ?? "lp,landing").split(",");

const THROTTLING = {
  mobile: ["--form-factor=mobile", "--screenEmulation.mobile=true", "--screenEmulation.width=412",
    "--screenEmulation.height=823", "--screenEmulation.deviceScaleFactor=1.75",
    "--throttling.rttMs=150", "--throttling.throughputKbps=1638.4", "--throttling.cpuSlowdownMultiplier=4"],
  desktop: ["--preset=desktop", "--throttling.rttMs=40", "--throttling.throughputKbps=10240",
    "--throttling.cpuSlowdownMultiplier=1"],
}[FORM];

// the pinned devDependency, started with this Node binary: no shell, same on every OS
const LIGHTHOUSE_CLI = fileURLToPath(import.meta.resolve("lighthouse/cli/index.js"));
function lighthouse(url, file) {
  const argv = [LIGHTHOUSE_CLI, url, "--quiet", "--output=json", `--output-path=${file}`,
    "--only-categories=performance", "--throttling-method=simulate", "--chrome-flags=--headless=new --no-sandbox",
    ...THROTTLING];
  for (let attempt = 1; ; attempt++) {
    try {
      execFileSync(process.execPath, argv, { stdio: "pipe" });
      return JSON.parse(fs.readFileSync(file, "utf8"));
    } catch (e) {
      // Lighthouse occasionally fails to record a trace (NO_NAVSTART): measure that run again
      if (attempt >= 3) throw e;
    }
  }
}

const median = (xs) => [...xs].sort((x, y) => x - y)[Math.floor(xs.length / 2)];
fs.mkdirSync(OUT, { recursive: true });
const summary = {};
for (const page of pages) {
  const runs = { A: [], B: [] };
  for (let i = 0; i < RUNS; i++) {
    for (const v of i % 2 ? ["B", "A"] : ["A", "B"]) {
      const url = (v === "A" ? A : B) + PAGES[page];
      const j = lighthouse(url, path.join(OUT, `${page}-${FORM}-${v}-${i}.json`));
      const a = j.audits;
      runs[v].push({
        score: Math.round(j.categories.performance.score * 100),
        lcp: a["largest-contentful-paint"].numericValue,
        tbt: a["total-blocking-time"].numericValue,
        cls: a["cumulative-layout-shift"].numericValue,
        fcp: a["first-contentful-paint"].numericValue,
      });
    }
  }
  summary[page] = {};
  for (const v of ["A", "B"]) {
    const r = runs[v];
    const m = (k) => median(r.map((x) => x[k]));
    summary[page][v] = { score: m("score"), lcp: m("lcp"), tbt: m("tbt"), cls: m("cls"), fcp: m("fcp"), runs: r.map((x) => x.score) };
    console.log(`${page.padEnd(8)} ${FORM.padEnd(7)} ${v}  score ${String(m("score")).padStart(3)} (${r.map((x) => x.score).join("/")})` +
      `  LCP ${(m("lcp") / 1000).toFixed(2)} s  TBT ${Math.round(m("tbt"))} ms  CLS ${m("cls").toFixed(3)}  FCP ${(m("fcp") / 1000).toFixed(2)} s`);
  }
}
fs.writeFileSync(path.join(OUT, `summary-${FORM}.json`), JSON.stringify({ method: { lighthouse: "13.5.0", runs: RUNS, form: FORM, throttling: THROTTLING, A, B }, summary }, null, 2));
