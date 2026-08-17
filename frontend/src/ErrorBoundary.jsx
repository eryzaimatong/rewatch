import { Component } from "react";
import ErrorState from "./ErrorState";
import "./App.css";

/**
 * Top-level safety net. Without this, any unhandled render error anywhere in
 * the tree (a malformed API response, a null a component didn't guard
 * against) unmounts the whole app and leaves a blank white page with no way
 * back short of the user guessing to hit reload themselves.
 */
export default class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError() {
    return { hasError: true };
  }

  componentDidCatch(error, info) {
    console.error("Unhandled render error:", error, info);
  }

  render() {
    if (!this.state.hasError) {
      return this.props.children;
    }
    return (
      <div className="page-shell">
        <div className="page-panel">
          <ErrorState
            title="Something went wrong."
            message="This page hit an error it couldn't recover from. Reloading usually fixes it."
            onRetry={() => window.location.reload()}
          />
        </div>
      </div>
    );
  }
}
