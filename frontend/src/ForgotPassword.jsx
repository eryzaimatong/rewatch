import { useState } from "react";
import { Link } from "react-router-dom";
import { forgotPassword } from "./api";
import BrandMark from "./BrandMark";
import Footer from "./Footer";
import "./App.css";

export default function ForgotPassword() {
  const [email, setemail] = useState("");
  const [submitted, setsubmitted] = useState(false);
  const [loading, setloading] = useState(false);

  async function handlesubmit(e) {
    e.preventDefault();
    if (!email.trim()) {
      return;
    }
    setloading(true);
    await forgotPassword(email.trim());
    setloading(false);
    // Always the same message regardless of the response — the backend never
    // signals whether the email is registered, so neither should this page.
    setsubmitted(true);
  }

  return (
    <>
    <div className="auth-shell">
      <div className="auth-card">
        <div style={{ display: "flex", justifyContent: "center", marginBottom: "10px" }}>
          <BrandMark size={36} />
        </div>
        <span className="eyebrow">RE:WATCH</span>
        <h1 className="auth-title">Reset Your Password</h1>

        {submitted ? (
          <>
            <p className="auth-subtitle">
              If that email is registered, a reset link is on its way. Check your inbox.
            </p>
            <div className="auth-toggle">
              <Link to="/" className="auth-toggle-link">Back to sign in</Link>
            </div>
          </>
        ) : (
          <>
            <p className="auth-subtitle">
              Enter your email and we'll send you a link to reset your password.
            </p>
            <form onSubmit={handlesubmit} className="auth-form">
              <div className="auth-field">
                <label htmlFor="forgot-email">Email</label>
                <input
                  id="forgot-email"
                  type="email"
                  placeholder="you@example.com"
                  value={email}
                  onChange={(e) => setemail(e.target.value)}
                  autoComplete="email"
                />
              </div>

              <button type="submit" className="btn-primary btn-block" disabled={loading}>
                {loading ? "Sending..." : "Send Reset Link"}
              </button>
            </form>
            <div className="auth-toggle">
              <Link to="/" className="auth-toggle-link">Back to sign in</Link>
            </div>
          </>
        )}
      </div>
    </div>
    <Footer />
    </>
  );
}
