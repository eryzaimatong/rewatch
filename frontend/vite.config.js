import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  test: {
    // Most units under test (onboardingUtils, api helpers) are plain JS with
    // no DOM. auth.js needs localStorage/document, which is why this isn't
    // the faster default 'node' environment across the board — one
    // environment for the whole suite is simpler than per-file overrides
    // for a test suite this size. happy-dom, not jsdom: jsdom's environment
    // setup alone measured 40-55s locally for a two-file suite, suspicious
    // enough on its own to be the actual cause of a CI failure rather than
    // just "CI is slower" — happy-dom is the standard, much lighter
    // alternative Vitest itself recommends for exactly this.
    environment: 'happy-dom',
  },
})
