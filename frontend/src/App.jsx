import { useState, useEffect, lazy, Suspense } from "react";
import { BrowserRouter, Routes, Route, NavLink, useNavigate, useLocation } from "react-router-dom";
import Login from "./Login";
import Footer from "./Footer";
import BrandMark from "./BrandMark";
import NotificationBell from "./NotificationBell";
import AccountMenu from "./AccountMenu";
import ErrorBoundary from "./ErrorBoundary";
import RouteLoading from "./RouteLoading";
import CommandPalette from "./CommandPalette";
import { IconSearch } from "./Icons";
import { getToken, clearSession, applyAccentColor, isAdmin } from "./auth";
import { applyAccessibilityPrefs } from "./accessibility";
import "./App.css";

// Code-split: everything past the login screen. Login is the one route every
// signed-out visitor needs on first paint, so it stays a static import; every
// other screen (including Onboarding, reached one hop later) is fetched on
// first visit instead of shipping in the initial bundle every session pays
// for regardless of which pages actually get used.
const MovieFeed = lazy(() => import("./MovieFeed"));
const Onboarding = lazy(() => import("./Onboarding"));
const TasteProfile = lazy(() => import("./TasteProfile"));
const Wrapped = lazy(() => import("./Wrapped"));
const Achievements = lazy(() => import("./Achievements"));
const Dashboard = lazy(() => import("./Dashboard"));
const Community = lazy(() => import("./Community"));
const SocialProfile = lazy(() => import("./SocialProfile"));
const ForgotPassword = lazy(() => import("./ForgotPassword"));
const CompareTaste = lazy(() => import("./CompareTaste"));
const ResetPassword = lazy(() => import("./ResetPassword"));
const Settings = lazy(() => import("./Settings"));
const NotFound = lazy(() => import("./NotFound"));
const PrivacyPolicy = lazy(() => import("./PrivacyPolicy"));
const TermsOfService = lazy(() => import("./TermsOfService"));
const AdminReports = lazy(() => import("./AdminReports"));

// Runs once, synchronously, before the first paint — so a page reload with
// an existing session shows the right accent immediately instead of a flash
// of default purple. saveSession() handles every subsequent change.
applyAccentColor(localStorage.getItem("accentColor"));
applyAccessibilityPrefs();

// The four core destinations someone reaches for every session. Everything
// periodic or administrative (Wrapped, Achievements, Settings) lives in
// AccountMenu's dropdown instead — a flat list mixing "where I live" with
// "things I check once a month" is what made the old 7-item nav feel bloated,
// especially on the mobile bottom bar, where 7 cramped text labels were
// barely legible.
function NavLinks({ linkClassName }) {
  return (
    <>
      <NavLink to="/" end className={linkClassName}>
        Home Feed
      </NavLink>
      <NavLink to="/watchlist" className={linkClassName}>
        My Watchlist
      </NavLink>
      <NavLink to="/community" className={linkClassName}>
        Community
      </NavLink>
      <NavLink to="/profile" className={linkClassName}>
        TasteDNA Profile
      </NavLink>
    </>
  );
}

function AppShell({ onLogout }) {
  const navLinkClass = ({ isActive }) => `app-nav-link${isActive ? " active" : ""}`;
  const mobileNavLinkClass = ({ isActive }) => `mobile-nav-link${isActive ? " active" : ""}`;
  // Keys the CSS entrance animation below (.route-transition, App.css) —
  // every distinct pathname remounts the wrapper div fresh, so switching
  // tabs reads as a real screen change instead of an abrupt content swap.
  // Plain CSS @keyframes rather than framer-motion here on purpose: App.jsx
  // is the one eager (non-lazy) entry point, and framer-motion is otherwise
  // only ever pulled in by lazy-loaded page chunks — importing it here would
  // put the whole library back on the critical path for every first paint
  // (verified: +122KB to the eager bundle) just for a route fade. A CSS
  // animation is also automatically covered by the app's existing global
  // prefers-reduced-motion rule, unlike a framer-motion animation, which is
  // JS/WAAPI-driven and that rule doesn't reach.
  const location = useLocation();
  const userid = localStorage.getItem("userId");
  const username = localStorage.getItem("username") || "You";
  // localStorage writes elsewhere (Settings.jsx's upload/remove) don't trigger
  // a re-render here on their own — this component only reads the value once
  // at mount otherwise, so the header would keep showing the old avatar (or
  // the initials fallback) until a full page reload. Settings.jsx dispatches
  // this event right after a successful save/remove so the header updates
  // immediately, same tab, no reload.
  const [avatarUrl, setavatarUrl] = useState(() => localStorage.getItem("avatarUrl"));
  useEffect(() => {
    function handleavatarchange() {
      setavatarUrl(localStorage.getItem("avatarUrl"));
    }
    window.addEventListener("rewatch:avatar-changed", handleavatarchange);
    return () => window.removeEventListener("rewatch:avatar-changed", handleavatarchange);
  }, []);

  return (
    <div>
      {/* Visually hidden until focused — the first Tab stop for a keyboard
          user, letting them jump past the header/nav straight to the page
          content instead of tabbing through three nav links every time. */}
      <a href="#main-content" className="skip-link">Skip to main content</a>

      <header className="app-header">
        <NavLink to="/" className="brand-mark">
          <BrandMark />
          RE:WATCH
          <span className="brand-tagline header-tagline">Stories chosen for how you feel.</span>
        </NavLink>

        <nav className="app-nav" aria-label="Primary">
          <NavLinks linkClassName={navLinkClass} />
        </nav>

        <div className="app-header-actions" style={{ display: "flex", alignItems: "center", gap: "var(--sp-2)" }}>
          {/* Was display:none below 860px with no other way to reach title
              search on mobile at all — not hard to find, not there. Same
              button, same handler; below 860px it shows a real "Search"
              label instead of the Ctrl+K hint, which is meaningless on a
              touchscreen with no physical keyboard. */}
          <button
            type="button"
            className="command-palette-trigger"
            onClick={() => window.dispatchEvent(new CustomEvent("rewatch:open-command-palette"))}
            aria-label="Search titles"
            title="Search titles"
          >
            <IconSearch />
            <span className="command-palette-trigger-label">Search</span>
            <kbd className="command-palette-trigger-kbd">{navigator.platform?.includes("Mac") ? "⌘" : "Ctrl"}K</kbd>
          </button>
          <NotificationBell userId={userid} />
          <AccountMenu userId={userid} username={username} avatarUrl={avatarUrl} onLogout={onLogout} />
        </div>
      </header>

      <div id="main-content" tabIndex={-1} style={{ paddingTop: "20px", outline: "none" }}>
        <div key={location.pathname} className="route-transition">
          <Routes>
            <Route path="/" element={<MovieFeed />} />
            <Route path="/watchlist" element={<Dashboard />} />
            <Route path="/community" element={<Community />} />
            <Route path="/social/:userId" element={<SocialProfile />} />
            <Route path="/profile" element={<TasteProfile />} />
            <Route path="/wrapped" element={<Wrapped />} />
            <Route path="/achievements" element={<Achievements />} />
            <Route path="/settings" element={<Settings />} />
            {/* Client-side gate is UX only (hides the link, avoids a dead-end page for
                a non-admin who guesses the URL) — the real check is the server's
                hasRole("ADMIN") on every /api/admin/** request this page makes. */}
            <Route path="/admin/reports" element={isAdmin() ? <AdminReports /> : <NotFound />} />
            <Route path="*" element={<NotFound />} />
          </Routes>
        </div>
      </div>

      <Footer />

      {/* Bottom tab bar — only shown below the mobile breakpoint (see App.css).
          The top nav stays in the DOM for larger viewports; both share the
          same NavLink active-state logic via NavLinks above. */}
      <nav className="mobile-bottom-nav" aria-label="Primary">
        <NavLinks linkClassName={mobileNavLinkClass} />
      </nav>

      <CommandPalette />
    </div>
  );
}

function AuthenticatedApp({ onLogout }) {
  const [onboarded, setonboarded] = useState(
    localStorage.getItem("onboarded") ? true : false
  );

  function finishonboard() {
    localStorage.setItem("onboarded", "yes");
    setonboarded(true);
  }

  if (!onboarded) {
    const userid = Number(localStorage.getItem("userId")) || 1;
    return <Onboarding userid={userid} onFinish={finishonboard} />;
  }

  return <AppShell onLogout={onLogout} />;
}

function Root() {
  const [logged, setlogged] = useState(!!getToken());
  const navigate = useNavigate();

  function handlelogin() {
    setlogged(true);
    navigate("/");
  }

  function dologout() {
    clearSession();
    setlogged(false);
  }

  return (
    <ErrorBoundary>
      <Suspense fallback={<RouteLoading />}>
        <Routes>
          {/* Reachable regardless of login state — a still-logged-in user clicking
              an old reset-password email link should also land here, not get
              shunted back into the app. */}
          <Route path="/forgot-password" element={<ForgotPassword />} />
          {/* Public on purpose — the whole point of the compatibility check is a
              visitor with no account can open a shared link and use it cold. See
              CompatibilityController's own doc comment for the backend half. */}
          <Route path="/compare/:username" element={<CompareTaste />} />
          <Route path="/reset-password" element={<ResetPassword />} />
          <Route path="/privacy" element={<PrivacyPolicy />} />
          <Route path="/terms" element={<TermsOfService />} />
          <Route
            path="/*"
            element={logged ? <AuthenticatedApp onLogout={dologout} /> : <Login onLogin={handlelogin} />}
          />
        </Routes>
      </Suspense>
    </ErrorBoundary>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <Root />
    </BrowserRouter>
  );
}
