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
    launchOptions: {
      executablePath: "/opt/pw-browsers/chromium",
    },
  },
});
