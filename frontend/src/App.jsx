import { useState, useEffect, lazy, Suspense } from "react";
import { BrowserRouter, Routes, Route, NavLink, Navigate, useNavigate, useLocation } from "react-router-dom";
import Login from "./Login";
import GuestHome from "./GuestHome";
import { NightStart, NightRoom } from "./Night";
import Footer from "./Footer";
import BrandMark, { Logo } from "./BrandMark";
import NotificationBell from "./NotificationBell";
import AccountMenu from "./AccountMenu";
import ErrorBoundary from "./ErrorBoundary";
import RouteLoading from "./RouteLoading";
import RouteErrorBoundary from "./resilience/RouteErrorBoundary";
import CommandPalette from "./CommandPalette";
import { IconSearch } from "./Icons";
import { IconHome, IconBookmark, IconUsers, IconDna } from "./RailIcons";
import { getToken, clearSession, applyAccentColor, isAdmin } from "./auth";
import { applyAccessibilityPrefs } from "./accessibility";
import "./App.css";
import "./brand.css";

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

// Left icon rail (desktop) — icon-only with a hover label, like the tablet
// concept in the design board. The mobile bottom bar reuses the same items.
const RAIL_ITEMS = [
  { to: "/", end: true, label: "Home Feed", short: "Home", Icon: IconHome },
  { to: "/watchlist", label: "My Watchlist", short: "Watchlist", Icon: IconBookmark },
  { to: "/community", label: "Community", short: "Community", Icon: IconUsers },
  { to: "/profile", label: "TasteDNA Profile", short: "TasteDNA", Icon: IconDna }
];

function openPalette() {
  window.dispatchEvent(new CustomEvent("rewatch:open-command-palette"));
}

function Rail() {
  return (
    <aside className="rail">
      <NavLink to="/" className="rail-logo" aria-label="Re:Watch home">
        <BrandMark size={30} />
      </NavLink>
      <nav className="rail-nav" aria-label="Primary">
        <button type="button" className="rail-link" data-label="Search" onClick={openPalette} aria-label="Search titles">
          <IconSearch />
        </button>
        {RAIL_ITEMS.map(({ to, end, label, Icon }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            data-label={label}
            aria-label={label}
            className={({ isActive }) => `rail-link${isActive ? " active" : ""}`}
          >
            <Icon />
          </NavLink>
        ))}
      </nav>
    </aside>
  );
}

function AppShell({ onLogout }) {
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
  // Lets CSS drop #root's page-width cap while the rail shell is mounted
  // (login/legal pages keep the narrow centered layout).
  useEffect(() => {
    document.body.classList.add("has-shell");
    return () => document.body.classList.remove("has-shell");
  }, []);
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
    <div className="shell">
      {/* Visually hidden until focused — the first Tab stop for a keyboard
          user, letting them jump past the nav straight to the page content. */}
      <a href="#main-content" className="skip-link">Skip to main content</a>

      <Rail />

      <div className="shell-main">
        <header className="topbar">
          <NavLink to="/" className="topbar-brand" aria-label="Re:Watch home">
            <Logo size={26} />
          </NavLink>
          <span className="topbar-tagline">Stories chosen for how you feel.</span>

          <div className="topbar-actions">
            <button
              type="button"
              className="command-palette-trigger"
              onClick={openPalette}
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

      <main id="main-content" tabIndex={-1} style={{ outline: "none" }}>
        <div key={location.pathname} className="route-transition">
          <Routes>
            <Route path="/" element={<MovieFeed />} />
            <Route path="/watchlist" element={<Dashboard />} />
            <Route path="/community" element={<RouteErrorBoundary area="Community" resetKey={location.pathname} title="The community is taking a breather" message="We couldn’t load this corner just now. Your ratings and TasteDNA are safe — pick a film by mood while it recovers."><Community /></RouteErrorBoundary>} />
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
      </main>

        <Footer />
      </div>

      {/* Bottom tab bar — only shown below the mobile breakpoint (see App.css).
          The top nav stays in the DOM for larger viewports; both share the
          same RAIL_ITEMS (icon + short label) above. */}
      <nav className="mobile-bottom-nav" aria-label="Primary">
        {RAIL_ITEMS.map(({ to, end, label, short, Icon }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            aria-label={label}
            className={({ isActive }) => `mobile-nav-link${isActive ? " active" : ""}`}
          >
            <Icon />
            <span>{short}</span>
          </NavLink>
        ))}
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
    return (
      <main id="main-content">
        <Onboarding userid={userid} onFinish={finishonboard} />
      </main>
    );
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
          <Route path="/forgot-password" element={<main id="main-content"><ForgotPassword /></main>} />
          {/* Public on purpose — the whole point of the compatibility check is a
              visitor with no account can open a shared link and use it cold. See
              CompatibilityController's own doc comment for the backend half. */}
          <Route path="/compare/:username" element={<main id="main-content"><CompareTaste /></main>} />
          {/* Public: a friend opens a shared Movie Night link with no account. */}
          <Route path="/night" element={<NightStart />} />
          <Route path="/night/:code" element={<NightRoom />} />
          <Route path="/reset-password" element={<main id="main-content"><ResetPassword /></main>} />
          <Route path="/privacy" element={<main id="main-content"><PrivacyPolicy /></main>} />
          <Route path="/terms" element={<main id="main-content"><TermsOfService /></main>} />
          {/* Signed-out visitors land on the try-before-you-sign-up home;
              the form itself lives at /login. */}
          <Route path="/" element={logged ? <AuthenticatedApp onLogout={dologout} /> : <GuestHome />} />
          <Route
            path="/login"
            element={logged ? <Navigate to="/" replace /> : <main id="main-content"><Login onLogin={handlelogin} /></main>}
          />
          <Route
            path="/*"
            element={logged ? <AuthenticatedApp onLogout={dologout} /> : <main id="main-content"><Login onLogin={handlelogin} /></main>}
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
