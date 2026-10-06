import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  timeout: 30_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  retries: 0,
  reporter: "list",
  use: {
    baseURL: "http://localhost:3000",
    actionTimeout: 30_000,
    trace: "retain-on-failure",
    // Only set when the environment pre-installs Chromium at a fixed path outside Playwright's
    // own cache (e.g. this sandbox). Unset (CI, most local setups) lets Playwright find the
    // browser it installed itself via `npx playwright install chromium`.
    launchOptions: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH
      ? { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH }
      : {},
  },
});
