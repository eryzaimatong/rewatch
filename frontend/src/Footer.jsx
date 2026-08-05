import { Link } from "react-router-dom";
import "./App.css";

export default function Footer() {
  return (
    <footer className="app-footer">
      <span>© {new Date().getFullYear()} Re:Watch</span>
      <Link to="/privacy">Privacy Policy</Link>
      <Link to="/terms">Terms of Service</Link>
    </footer>
  );
}
