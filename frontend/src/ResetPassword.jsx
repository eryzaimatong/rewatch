import { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { resetPassword } from "./api";
import BrandMark from "./BrandMark";
import Footer from "./Footer";
import "./App.css";

export default function ResetPassword() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token");
  const navigate = useNavigate();

  const [newpassword, setnewpassword] = useState("");
  const [confirm, setconfirm] = useState("");
  const [loading, setloading] = useState(false);
  const [errmsg, seterrmsg] = useState("");
  const [done, setdone] = useState(false);

  async function handlesubmit(e) {
    e.preventDefault();
    seterrmsg("");

    if (!token) {
      seterrmsg("This reset link is missing its token — check the link you clicked.");
      return;
    }
    if (newpassword.length < 6) {
      seterrmsg("Password must be at least 6 characters.");
      return;
    }
    if (newpassword !== confirm) {
      seterrmsg("Passwords don't match.");
      return;
    }

    setloading(true);
    const res = await resetPassword(token, newpassword);
    setloading(false);

    if (res?.status === "success") {
      setdone(true);
      setTimeout(() => navigate("/"), 2000);
    } else {
      seterrmsg(res?.message || "Could not reset your password. The link may have expired.");
    }
  }

  return (
    <>
    <div className="auth-shell">
      <div className="auth-card">
        <div style={{ display: "flex", justifyContent: "center", marginBottom: "10px" }}>
          <BrandMark size={36} />
        </div>
        <span className="eyebrow">RE:WATCH</span>
        <h1 className="auth-title">Choose a New Password</h1>

        {done ? (
          <p className="status-message status-message--success">
            Password reset. Redirecting you to sign in...
          </p>
        ) : (
          <>
            {errmsg && <div className="status-message status-message--error">{errmsg}</div>}

            <form onSubmit={handlesubmit} className="auth-form">
              <div className="auth-field">
                <label htmlFor="reset-new-password">New password</label>
                <input
                  id="reset-new-password"
                  type="password"
                  placeholder="••••••••"
                  value={newpassword}
                  onChange={(e) => setnewpassword(e.target.value)}
                  autoComplete="new-password"
                />
              </div>

              <div className="auth-field">
                <label htmlFor="reset-confirm-password">Confirm new password</label>
                <input
                  id="reset-confirm-password"
                  type="password"
                  placeholder="••••••••"
                  value={confirm}
                  onChange={(e) => setconfirm(e.target.value)}
                  autoComplete="new-password"
                />
              </div>

              <button type="submit" className="btn-primary btn-block" disabled={loading}>
                {loading ? "Resetting..." : "Reset Password"}
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
