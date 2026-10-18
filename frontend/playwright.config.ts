import { defineConfig } from "@playwright/test";

/**
 * End-to-end tests against the RUNNING stack:
 *   backend + Postgres + Mailpit: docker compose -f infra/docker-compose.yml up -d
 *     (with raised per-IP rate limits, see e2e/README.md)
 *   frontend: npm run build && npm run start   (http://localhost:3000)
 *
 * Every spec runs in four variants: desktop 1440×900 and phone 390×844,
 * each as DE / dark and EN / light. Screenshots of every step land in
 * ../screenshots/phase3/<project>/.
 */
export default defineConfig({
  testDir: "./e2e",
  timeout: 180_000,
  expect: { timeout: 20_000 },
  fullyParallel: false,
  workers: 1, // one shop, one calendar: tests must not race each other for slots
  retries: 0,
  reporter: [["list"]],
  globalSetup: "./e2e/global-setup.ts",
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:3000",
    trace: "retain-on-failure",
    timezoneId: "Europe/Berlin",
    // "only necessary" consent stored (written by global-setup); consent.spec.ts overrides it
    storageState: "./e2e/.storage.json",
  },
  projects: [
    { name: "desktop-de-dark", use: { viewport: { width: 1440, height: 900 }, colorScheme: "dark", locale: "de-DE" } },
    { name: "desktop-en-light", use: { viewport: { width: 1440, height: 900 }, colorScheme: "light", locale: "en-GB" } },
    {
      name: "mobile-de-dark",
      use: { viewport: { width: 390, height: 844 }, colorScheme: "dark", locale: "de-DE", isMobile: true, hasTouch: true },
    },
    {
      name: "mobile-en-light",
      use: { viewport: { width: 390, height: 844 }, colorScheme: "light", locale: "en-GB", isMobile: true, hasTouch: true },
    },
  ],
});
