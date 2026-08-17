import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import Avatar from "./Avatar";
import { isAdmin } from "./auth";
import "./App.css";

/**
 * Avatar-triggered account menu — same click-outside-to-close pattern as
 * NotificationBell. Only links to pages that actually exist in this app
 * (Profile, Wrapped, Achievements, Settings, Logout); the rest of
 * Letterboxd-style menus (Films, Diary, Likes, Tags, Network...) don't map
 * to anything real here, so they're not faked in just to look denser.
 *
 * Wrapped and Achievements live here rather than the primary nav (see
 * App.jsx's NavLinks) — they're things you check periodically, not places
 * you live day-to-day, and mixing the two flattened the top nav into 7 items.
 */
export default function AccountMenu({ userId, username, avatarUrl, onLogout }) {
  const [open, setopen] = useState(false);
  const containerRef = useRef(null);
  const triggerRef = useRef(null);
  const navigate = useNavigate();
  const [avatarFrame, setavatarFrame] = useState(() => localStorage.getItem("avatarFrame"));

  useEffect(() => {
    function handleoutside(e) {
      if (containerRef.current && !containerRef.current.contains(e.target)) {
        setopen(false);
      }
    }
    document.addEventListener("mousedown", handleoutside);
    return () => document.removeEventListener("mousedown", handleoutside);
  }, []);

  // Same reasoning as MovieModal's useModalA11y, scaled down for a menu
  // rather than a full dialog: a keyboard user could open this but had no
  // way to close it except Tab-ing all the way past its items, and closing
  // never returned focus to the button that opened it.
  useEffect(() => {
    if (!open) return;
    function handlekeydown(e) {
      if (e.key === "Escape") {
        setopen(false);
        triggerRef.current?.focus();
      }
    }
    document.addEventListener("keydown", handlekeydown);
    return () => document.removeEventListener("keydown", handlekeydown);
  }, [open]);

  useEffect(() => {
    function handleavatarchange() {
      setavatarFrame(localStorage.getItem("avatarFrame"));
    }
    window.addEventListener("rewatch:avatar-changed", handleavatarchange);
    return () => window.removeEventListener("rewatch:avatar-changed", handleavatarchange);
  }, []);

  function go(path) {
    setopen(false);
    navigate(path);
  }

  return (
    <div className="account-menu-wrap" ref={containerRef}>
      <button
        type="button"
        ref={triggerRef}
        className="account-menu-trigger"
        onClick={() => setopen((v) => !v)}
        aria-label="Account menu"
        aria-expanded={open}
      >
        <Avatar username={username} avatarUrl={avatarUrl} avatarFrame={avatarFrame} size={34} />
      </button>

      {open && (
        <div className="account-menu-panel" role="menu">
          <div className="account-menu-header">
            <Avatar username={username} avatarUrl={avatarUrl} avatarFrame={avatarFrame} size={40} />
            <span className="account-menu-username">{username}</span>
          </div>
          <button type="button" className="account-menu-item" role="menuitem" onClick={() => go(`/social/${userId}`)}>
            View Profile
          </button>
          <button type="button" className="account-menu-item" role="menuitem" onClick={() => go("/wrapped")}>
            Monthly Wrapped
          </button>
          <button type="button" className="account-menu-item" role="menuitem" onClick={() => go("/achievements")}>
            Achievements
          </button>
          <button type="button" className="account-menu-item" role="menuitem" onClick={() => go("/settings")}>
            Settings
          </button>
          {isAdmin() && (
            <button type="button" className="account-menu-item" role="menuitem" onClick={() => go("/admin/reports")}>
              Reports (Admin)
            </button>
          )}
          <div className="account-menu-divider" />
          <button type="button" className="account-menu-item account-menu-item--danger" role="menuitem" onClick={onLogout}>
            Sign Out
          </button>
        </div>
      )}
    </div>
  );
}
