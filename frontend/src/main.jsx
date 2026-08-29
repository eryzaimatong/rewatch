import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.jsx'
import ErrorBoundary from './ErrorBoundary.jsx'
import { installSessionGuard } from './sessionGuard.js'
import { BASE } from './api.js'

installSessionGuard()

// Fire-and-forget warm-up: on a free tier, the backend may be asleep and the
// first real request (whatever it turns out to be, on whichever route) pays
// the full cold-boot cost (135-166s+ measured, see DEPLOYMENT.md). Firing
// this at module scope — before React even renders — means the wake-up
// overlaps with the page loading and the visitor reading it, instead of
// only starting once they've clicked something. Result is deliberately
// ignored: nothing in the UI depends on this succeeding, it exists purely
// to get a head start on whichever backend call happens next.
fetch(`${BASE}/api/health`).catch(() => {})

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <ErrorBoundary>
      <App />
    </ErrorBoundary>
  </StrictMode>,
)

// Registered after load so it never competes with the initial render for
// bandwidth/main-thread time — see public/sw.js for the caching strategy.
if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => {})
  })
}
