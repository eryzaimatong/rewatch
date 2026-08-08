import { useState, useEffect } from "react";
import { BrowserRouter, Routes, Route, NavLink, useNavigate } from "react-router-dom";
import MovieFeed from "./MovieFeed";
import Onboarding from "./Onboarding";
import TasteProfile from "./TasteProfile";
import Wrapped from "./Wrapped";
import Achievements from "./Achievements";
import Dashboard from "./Dashboard";
import Community from "./Community";
import SocialProfile from "./SocialProfile";
import Login from "./Login";
import ForgotPassword from "./ForgotPassword";
import ResetPassword from "./ResetPassword";
import Settings from "./Settings";
import NotFound from "./NotFound";
import PrivacyPolicy from "./PrivacyPolicy";
import TermsOfService from "./TermsOfService";
import Footer from "./Footer";
import BrandMark from "./BrandMark";
import NotificationBell from "./NotificationBell";
import AccountMenu from "./AccountMenu";
import { getToken, clearSession, applyAccentColor } from "./auth";
import { applyAccessibilityPrefs } from "./accessibility";
import "./App.css";

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

        <div style={{ display: "flex", alignItems: "center", gap: "var(--sp-2)" }}>
          <NotificationBell userId={userid} />
          <AccountMenu userId={userid} username={username} avatarUrl={avatarUrl} onLogout={onLogout} />
        </div>
      </header>

      <div id="main-content" tabIndex={-1} style={{ paddingTop: "20px", outline: "none" }}>
        <Routes>
          <Route path="/" element={<MovieFeed />} />
          <Route path="/watchlist" element={<Dashboard />} />
          <Route path="/community" element={<Community />} />
          <Route path="/social/:userId" element={<SocialProfile />} />
          <Route path="/profile" element={<TasteProfile />} />
          <Route path="/wrapped" element={<Wrapped />} />
          <Route path="/achievements" element={<Achievements />} />
          <Route path="/settings" element={<Settings />} />
          <Route path="*" element={<NotFound />} />
        </Routes>
      </div>

      <Footer />

      {/* Bottom tab bar — only shown below the mobile breakpoint (see App.css).
          The top nav stays in the DOM for larger viewports; both share the
          same NavLink active-state logic via NavLinks above. */}
      <nav className="mobile-bottom-nav" aria-label="Primary">
        <NavLinks linkClassName={mobileNavLinkClass} />
      </nav>
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
    <Routes>
      {/* Reachable regardless of login state — a still-logged-in user clicking
          an old reset-password email link should also land here, not get
          shunted back into the app. */}
      <Route path="/forgot-password" element={<ForgotPassword />} />
      <Route path="/reset-password" element={<ResetPassword />} />
      <Route path="/privacy" element={<PrivacyPolicy />} />
      <Route path="/terms" element={<TermsOfService />} />
      <Route
        path="/*"
        element={logged ? <AuthenticatedApp onLogout={dologout} /> : <Login onLogin={handlelogin} />}
      />
    </Routes>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <Root />
    </BrowserRouter>
  );
}
