import { defineConfig, devices } from "@playwright/test";

// End-to-end tests run against the production build, with the backend replaced
// by in-test mocks (see e2e/fixtures.js), so they are hermetic: no database, no
// network, no flakiness from a sleeping server.
export default defineConfig({
  testDir: "./e2e",
  timeout: 45_000,
  expect: { timeout: 10_000 },
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["github"], ["html", { open: "never" }]] : "list",
  use: {
    baseURL: "http://localhost:4173",
    trace: "off",
    screenshot: "only-on-failure"
  },
  projects: [
    { name: "desktop", use: { ...devices["Desktop Chrome"], viewport: { width: 1280, height: 860 } } },
    // Mobile only needs the layout checks; the share flow differs on a phone (native share sheet).
    { name: "mobile", use: { ...devices["Pixel 7"] }, testMatch: /rendering\.spec\.js/ }
  ],
  webServer: {
    command: "npm run build && npm run preview -- --port 4173 --strictPort",
    url: "http://localhost:4173",
    reuseExistingServer: !process.env.CI,
    timeout: 180_000
  }
});
