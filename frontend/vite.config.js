import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  test: {
    // Most units under test (onboardingUtils, api helpers) are plain JS with
    // no DOM. auth.js needs localStorage/document, which is why this is
    // jsdom rather than the faster default 'node' environment across the
    // board — one environment for the whole suite is simpler than per-file
    // overrides for a test suite this size.
    environment: 'jsdom',
    // Vitest's default 'forks' pool hung indefinitely on this Windows/Git
    // Bash setup ("Timeout waiting for worker to respond") — not a test
    // problem, a subprocess-spawning issue specific to this environment.
    // 'threads' works reliably here; CI (Ubuntu) would likely be fine
    // either way, but there's no reason to run something different locally
    // than what's actually verified to work.
    pool: 'threads',
  },
})
