import type { NextConfig } from "next";
import createNextIntlPlugin from "next-intl/plugin";

// Wires src/i18n/request.ts (per-request locale + messages) into the app.
const withNextIntl = createNextIntlPlugin("./src/i18n/request.ts");

const isDev = process.env.NODE_ENV !== "production";
const env = (name: string) => (process.env[name] ?? "").trim();
const originOf = (url: string) => {
  try {
    return new URL(url).origin;
  } catch {
    return "";
  }
};

/**
 * Content-Security-Policy, built from the same NEXT_PUBLIC_* values that switch the
 * integrations on (all inlined at build time): a third-party host is only allowed when
 * its id / key is configured.
 *
 * script-src keeps 'unsafe-inline': Next.js streams inline bootstrap / RSC scripts and the
 * layout has two tiny inline scripts (theme, consent banner). The alternative, per-request
 * nonces, forces dynamic rendering of every page and gives up the prerendered shells
 * (performance). Third-party script hosts are still pinned, objects, frames, <base> and form
 * targets are locked down, and the app renders no user HTML (React escapes everything).
 */
function contentSecurityPolicy(): string {
  const api = originOf(env("NEXT_PUBLIC_API_URL") || "http://localhost:8080");
  const ga = !!(env("NEXT_PUBLIC_GA4_ID") || env("NEXT_PUBLIC_GOOGLE_ADS_ID"));
  const ads = !!env("NEXT_PUBLIC_GOOGLE_ADS_ID");
  const meta = !!env("NEXT_PUBLIC_META_PIXEL_ID");
  const turnstile = !!env("NEXT_PUBLIC_TURNSTILE_SITE_KEY");

  const script = ["'self'", "'unsafe-inline'"];
  const connect = ["'self'", api];
  const img = ["'self'", "data:", "blob:"];
  const frame: string[] = [];
  if (isDev) {
    script.push("'unsafe-eval'"); // React dev tooling / HMR
    connect.push("ws:", "wss:");
  }
  if (ga) {
    script.push("https://www.googletagmanager.com");
    connect.push("https://*.google-analytics.com", "https://*.analytics.google.com", "https://www.googletagmanager.com");
    img.push("https://*.google-analytics.com", "https://www.googletagmanager.com");
  }
  if (ads) {
    script.push("https://www.googleadservices.com", "https://googleads.g.doubleclick.net");
    connect.push("https://www.google.com", "https://googleads.g.doubleclick.net", "https://www.googleadservices.com");
    img.push("https://www.google.com", "https://googleads.g.doubleclick.net", "https://www.googleadservices.com");
    frame.push("https://td.doubleclick.net", "https://www.googletagmanager.com");
  }
  if (meta) {
    script.push("https://connect.facebook.net");
    connect.push("https://www.facebook.com", "https://connect.facebook.net");
    img.push("https://www.facebook.com");
  }
  if (turnstile) {
    script.push("https://challenges.cloudflare.com");
    connect.push("https://challenges.cloudflare.com");
    frame.push("https://challenges.cloudflare.com");
  }

  const directives = [
    "default-src 'self'",
    `script-src ${script.join(" ")}`,
    "style-src 'self' 'unsafe-inline'",
    `img-src ${img.join(" ")}`,
    "font-src 'self' data:",
    `connect-src ${[...new Set(connect.filter(Boolean))].join(" ")}`,
    `frame-src ${frame.length ? frame.join(" ") : "'none'"}`,
    "worker-src 'self' blob:",
    "manifest-src 'self'",
    "media-src 'self'",
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "frame-ancestors 'none'",
  ];
  if (!isDev && env("NEXT_PUBLIC_SITE_URL").startsWith("https://")) directives.push("upgrade-insecure-requests");
  return directives.join("; ");
}

const PRIVATE_PATHS = ["admin", "account", "barber", "pay", "booking/manage"].flatMap((p) => [
  `/:locale(de|en)/${p}`,
  `/:locale(de|en)/${p}/:path*`,
]);

function securityHeaders() {
  const headers = [
    { key: "Content-Security-Policy", value: contentSecurityPolicy() },
    { key: "X-Content-Type-Options", value: "nosniff" },
    { key: "X-Frame-Options", value: "DENY" },
    { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
    {
      key: "Permissions-Policy",
      value: "camera=(), microphone=(), geolocation=(), payment=(), usb=(), interest-cohort=(), browsing-topics=()",
    },
  ];
  // HSTS only for an https site (browsers ignore it over http, but localhost must never get it)
  if (!isDev && env("NEXT_PUBLIC_SITE_URL").startsWith("https://")) {
    headers.push({ key: "Strict-Transport-Security", value: "max-age=63072000; includeSubDomains" });
  }
  return headers;
}

const nextConfig: NextConfig = {
  cacheComponents: true,
  experimental: {
    // complete, server-rendered 404 for unmatched URLs (app/global-not-found.tsx)
    globalNotFound: true,
  },
  partialPrefetching: true,
  // self-contained server (.next/standalone) for the production Docker image, which sets
  // NEXT_OUTPUT=standalone; local `npm run build && npm start` keeps the normal output
  output: process.env.NEXT_OUTPUT === "standalone" ? "standalone" : undefined,
  // the OG / Twitter image routes read their font from src/assets at runtime (fs, not an import)
  outputFileTracingIncludes: {
    "/**": ["./src/assets/fonts/**/*"],
  },
  poweredByHeader: false,
  images: {
    // AVIF first (~30-50 % smaller than WebP), WebP for browsers without AVIF
    formats: ["image/avif", "image/webp"],
    // optimized images are cached for 30 days (the sources are versioned by deploy)
    minimumCacheTTL: 2592000,
  },
  async headers() {
    return [
      { source: "/:path*", headers: securityHeaders() },
      // scene images and other static files in public/: not content-hashed, so 30 days +
      // revalidation instead of "immutable" (_next/static is immutable already)
      {
        source: "/scene/:path*",
        headers: [{ key: "Cache-Control", value: "public, max-age=2592000, stale-while-revalidate=86400" }],
      },
      // Cross-window isolation where sessions and email-link tokens live. Not on the public
      // marketing pages: in Lighthouse it cost ~200 ms TBT on the mobile landing page
      // (process swap), with nothing to protect there.
      ...PRIVATE_PATHS.map((source) => ({
        source,
        headers: [{ key: "Cross-Origin-Opener-Policy", value: "same-origin" }],
      })),
    ];
  },
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

export default withNextIntl(nextConfig);
