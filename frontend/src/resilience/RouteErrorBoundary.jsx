import { Component } from "react";
import { Link } from "react-router-dom";
import Ree from "../Ree";
import { captureError } from "../telemetry";

const MAX_INLINE_RETRIES = 2;

/**
 * Per-route boundary: a crash in one tab (Community, Profile…) must never take
 * the whole shell down, and must never become a "Try again" treadmill. After
 * MAX_INLINE_RETRIES the retry button disappears and the only forward path is
 * the primary mood-discovery flow, so there is always somewhere to go.
 *
 * Pass `resetKey={location.pathname}` so navigating away and back starts clean.
 */
export default class RouteErrorBoundary extends Component {
  state = { error: null, retries: 0 };

  static getDerivedStateFromError(error) {
    return { error };
  }

  componentDidCatch(error, info) {
    console.error(`[${this.props.area ?? "route"}] render error:`, error, info?.componentStack);
    captureError(error, { area: this.props.area ?? "route" });
  }

  componentDidUpdate(prev) {
    if (prev.resetKey !== this.props.resetKey && this.state.error) {
      // Navigating away from a crashed route clears it (a reset, not a retry loop).
      this.setState({ error: null, retries: 0 });
    }
  }

  retry = () => this.setState((s) => ({ error: null, retries: s.retries + 1 }));

  render() {
    if (!this.state.error) return this.props.children;
    const {
      area = "This page",
      title = `${area} is taking a breather`,
      message = "Something didn't load the way we planned. Your ratings and TasteDNA are safe.",
      ctaTo = "/",
      ctaLabel = "Pick a film by mood"
    } = this.props;
    const canRetry = this.state.retries < MAX_INLINE_RETRIES;

    return (
      <div className="page-shell">
        <div className="route-fallback" role="alert">
          <Ree pose="curious" size={168} />
          <h2>{title}</h2>
          <p>{message}</p>
          <div className="route-fallback-actions">
            <Link to={ctaTo} className="cx-cta">{ctaLabel}</Link>
            {canRetry && (
              <button type="button" className="pill" onClick={this.retry}>Try again</button>
            )}
          </div>
        </div>
      </div>
    );
  }
}
