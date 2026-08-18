import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getNotifications, getUnreadNotificationCount, markAllNotificationsRead } from "./api";
import "./App.css";

function timeago(iso) {
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return "";
  const mins = Math.max(0, Math.round((Date.now() - then) / 60000));
  if (mins < 1) return "just now";
  if (mins < 60) return `${mins}m ago`;
  const hours = Math.round(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  return `${Math.round(hours / 24)}d ago`;
}

/**
 * @param ownUserId the recipient's own id — needed for REVIEW_LIKED/
 *   REVIEW_COMMENTED, which otherwise have nowhere obvious to send someone:
 *   `relatedUserId` on those two is the liker/commenter, not the review
 *   itself, and Notification carries no titleId/ratingId to deep-link to
 *   the specific review. Routing to the recipient's own profile (where
 *   their reviews are listed) was a silent dead end before this — clicking
 *   either of these notification types navigated nowhere at all.
 */
function targetFor(n, ownUserId) {
  if (n.type === "NEW_FOLLOWER" || n.type === "DNA_MATCH") {
    return n.relatedUserId ? `/social/${n.relatedUserId}` : null;
  }
  if (n.type === "TASTE_MILESTONE") {
    return "/profile";
  }
  if (n.type === "ACHIEVEMENT_UNLOCKED") {
    return "/achievements";
  }
  if (n.type === "REVIEW_LIKED" || n.type === "REVIEW_COMMENTED") {
    return ownUserId ? `/social/${ownUserId}` : null;
  }
  return null;
}

/**
 * Fetch-on-mount, no polling — this app has no live-update infrastructure
 * anywhere yet (every screen is one-shot fetch-on-mount), so this doesn't
 * introduce any either. Mark-all-read fires when the panel opens, which is
 * simpler and correct enough for a first pass over per-item read tracking.
 */
export default function NotificationBell({ userId }) {
  const [open, setopen] = useState(false);
  const [unread, setunread] = useState(0);
  const [items, setitems] = useState([]);
  const [loaded, setloaded] = useState(false);
  const containerRef = useRef(null);
  const triggerRef = useRef(null);
  const navigate = useNavigate();

  useEffect(() => {
    if (!userId) return;
    getUnreadNotificationCount(userId).then(setunread);
  }, [userId]);

  useEffect(() => {
    function handleoutside(e) {
      if (containerRef.current && !containerRef.current.contains(e.target)) {
        setopen(false);
      }
    }
    document.addEventListener("mousedown", handleoutside);
    return () => document.removeEventListener("mousedown", handleoutside);
  }, []);

  // Same reasoning as AccountMenu: a keyboard user opening this panel had no
  // way to close it short of Tab-ing past every notification, and closing
  // never returned focus to the bell button that opened it.
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

  async function togglepanel() {
    const next = !open;
    setopen(next);
    if (next && userId) {
      const list = await getNotifications(userId);
      setitems(list);
      setloaded(true);
      if (unread > 0) {
        setunread(0);
        markAllNotificationsRead(userId);
      }
    }
  }

  function handleclick(n) {
    setopen(false);
    const target = targetFor(n, userId);
    if (target) {
      navigate(target);
    }
  }

  return (
    <div className="notification-bell-wrap" ref={containerRef}>
      <button
        type="button"
        ref={triggerRef}
        className="notification-bell"
        onClick={togglepanel}
        aria-label={unread > 0 ? `Notifications (${unread} unread)` : "Notifications"}
      >
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path
            d="M12 3a5 5 0 0 0-5 5v2.6c0 .8-.28 1.58-.8 2.2L5 14.5c-.7.85-.1 2.5 1.2 2.5h11.6c1.3 0 1.9-1.65 1.2-2.5l-1.2-1.7a3.6 3.6 0 0 1-.8-2.2V8a5 5 0 0 0-5-5Z"
            stroke="currentColor"
            strokeWidth="1.6"
            strokeLinejoin="round"
          />
          <path d="M9.5 19a2.5 2.5 0 0 0 5 0" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
        </svg>
        {unread > 0 && <span className="notification-badge">{unread > 9 ? "9+" : unread}</span>}
      </button>

      {open && (
        <div className="notification-panel" role="menu">
          <p className="notification-panel-title">Notifications</p>
          {!loaded && <p className="notification-panel-empty">Loading...</p>}
          {loaded && items.length === 0 && (
            <p className="notification-panel-empty">You're all caught up.</p>
          )}
          {items.map((n) => (
            <button
              type="button"
              key={n.id}
              className="notification-item"
              onClick={() => handleclick(n)}
            >
              <span className="notification-item-message">{n.message}</span>
              <span className="notification-item-time">{timeago(n.createdAt)}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
