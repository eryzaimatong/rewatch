import { Link } from "react-router-dom";
import "./App.css";

/**
 * A real 404 — the previous behavior (catch-all `<Navigate to="/" replace />`)
 * silently bounced any bad URL home with no explanation, which reads as
 * broken rather than as "that page doesn't exist."
 */
export default function NotFound() {
  return (
    <div className="page-shell">
      <div className="page-panel">
        <div className="feed-state">
          <h3>Page not found</h3>
          <p>There's nothing here — the link might be broken, or the page may have moved.</p>
          <Link to="/" className="btn-primary" style={{ display: "inline-block", marginTop: "var(--sp-2)" }}>
            Back to Home Feed
          </Link>
        </div>
      </div>
    </div>
  );
}
