import { useState } from "react";
import { BrowserRouter, Routes, Route, Navigate, NavLink, useNavigate } from "react-router-dom";
import MovieFeed from "./MovieFeed";
import Onboarding from "./Onboarding";
import TasteProfile from "./TasteProfile";
import Wrapped from "./Wrapped";
import Dashboard from "./Dashboard";
import Community from "./Community";
import SocialProfile from "./SocialProfile";
import Login from "./Login";
import BrandMark from "./BrandMark";
import { getToken, clearSession } from "./auth";
import "./App.css";

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
      <NavLink to="/wrapped" className={linkClassName}>
        Wrapped
      </NavLink>
    </>
  );
}

function AppShell({ onLogout }) {
  const navLinkClass = ({ isActive }) => `app-nav-link${isActive ? " active" : ""}`;
  const mobileNavLinkClass = ({ isActive }) => `mobile-nav-link${isActive ? " active" : ""}`;

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

        <button type="button" className="logout-button" onClick={onLogout}>
          Logout
        </button>
      </header>

      <div id="main-content" tabIndex={-1} style={{ paddingTop: "20px", outline: "none" }}>
        <Routes>
          <Route path="/" element={<MovieFeed />} />
          <Route path="/watchlist" element={<Dashboard />} />
          <Route path="/community" element={<Community />} />
          <Route path="/social/:userId" element={<SocialProfile />} />
          <Route path="/profile" element={<TasteProfile />} />
          <Route path="/wrapped" element={<Wrapped />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </div>

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

  if (!logged) {
    return <Login onLogin={handlelogin} />;
  }

  return <AuthenticatedApp onLogout={dologout} />;
}

export default function App() {
  return (
    <BrowserRouter>
      <Root />
    </BrowserRouter>
  );
}
