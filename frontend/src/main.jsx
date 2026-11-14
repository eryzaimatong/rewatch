import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import '@fontsource-variable/inter'
import '@fontsource/jetbrains-mono/500.css'
import '@fontsource/jetbrains-mono/700.css'
import './index.css'
import App from './App.jsx'
import ErrorBoundary from './ErrorBoundary.jsx'
import { installSessionGuard } from './sessionGuard.js'
import { warmUp } from './resilience/serverStatus.js'

installSessionGuard()

// Invisible wake-up: pings /api/health (which opens a real DB connection) with
// backoff until the backend AND its database are up. Non-blocking by design,
// nothing renders differently because of it; useServerStatus() lets any screen
// react once it flips to "awake". Started before React renders so the cold
// boot overlaps with the visitor reading the login screen.
warmUp()

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
