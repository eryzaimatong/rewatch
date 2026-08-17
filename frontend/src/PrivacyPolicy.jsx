import { Link } from "react-router-dom";
import "./App.css";

export default function PrivacyPolicy() {
  return (
    <div className="page-shell">
      <div className="page-panel">
        <span className="eyebrow">Legal</span>
        <h1>Privacy Policy</h1>
        <p style={{ color: "var(--text-faint)", fontSize: "0.82rem" }}>
          Effective date: August 18, 2026
        </p>

        <p>
          This page describes what Re:Watch collects and why. It's written to match
          what the app actually does today, not a generic template — if a section
          below stops being accurate as the app changes, this page needs to change
          with it.
        </p>

        <h2>What we collect</h2>
        <ul>
          <li><strong>Account data:</strong> your email address, username, and a bcrypt hash of
            your password. We never store your password in plain text and never send it
            back to you or anyone else.</li>
          <li><strong>Ratings and taste data:</strong> the titles you rate, the facets and written
            reflections you submit, and the taste-profile ("TasteDNA") vector derived from
            them. This is what powers recommendations and the evolution timeline.</li>
          <li><strong>Social data:</strong> who you follow, your public watchlists (if you mark
            them public), and reviews you choose to write.</li>
          <li><strong>Catalog data:</strong> movie/show metadata (titles, posters, genres) is
            sourced from TMDB, not something we collect about you.</li>
          <li><strong>Session data:</strong> a JSON Web Token stored in your browser's
            localStorage to keep you signed in. We don't use tracking cookies or
            third-party analytics.</li>
        </ul>

        <h2>What we don't do</h2>
        <ul>
          <li>We don't sell your data to third parties.</li>
          <li>We don't share your ratings or taste profile with anyone except as needed
            to show you the app itself (e.g. a DNA-match score with another user you've
            chosen to compare against).</li>
        </ul>

        <h2>Your controls</h2>
        <p>
          You can change your password or permanently delete your account and all
          associated data at any time from <Link to="/settings">Settings</Link>.
          Deleting your account removes your ratings, watchlist, taste profile, and
          social connections.
        </p>

        <h2>Contact</h2>
        <p>
          Questions about this policy: imatongeryzamae@gmail.com. This app operates
          under the laws of the Philippines.
        </p>
      </div>
    </div>
  );
}
