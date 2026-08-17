import { Link } from "react-router-dom";
import "./App.css";

export default function Footer() {
  return (
    <footer className="app-footer">
      <span>© {new Date().getFullYear()} Re:Watch</span>
      <Link to="/privacy">Privacy Policy</Link>
      <Link to="/terms">Terms of Service</Link>
      {/* Required by TMDB's API terms of use whenever their data/images are
          displayed — every poster and most of the catalog metadata in this
          app comes from TMDB. */}
      <span>
        This product uses the{" "}
        <a href="https://www.themoviedb.org/" target="_blank" rel="noreferrer">
          TMDB
        </a>{" "}
        API but is not endorsed or certified by TMDB.
      </span>
    </footer>
  );
}
