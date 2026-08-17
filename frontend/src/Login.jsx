import { useState } from "react";
import { Link } from "react-router-dom";
import { login, register } from "./api";
import { saveSession } from "./auth";
import BrandMark from "./BrandMark";
import Footer from "./Footer";
import "./App.css";

const MIN_PASSWORD_LENGTH = 6;

export default function Login({ onLogin }) {
  const [username, setusername] = useState("");
  const [password, setpassword] = useState("");
  const [confirmpassword, setconfirmpassword] = useState("");
  const [showpassword, setshowpassword] = useState(false);
  const [isreg, setisreg] = useState(false);
  const [errmsg, seterrmsg] = useState("");
  const [okmsg, setokmsg] = useState("");
  const [loading, setloading] = useState(false);

  const passwordTooShort = isreg && password.length > 0 && password.length < MIN_PASSWORD_LENGTH;
  const passwordsMismatch = isreg && confirmpassword.length > 0 && confirmpassword !== password;

  async function handlesubmit(e) {
    e.preventDefault();
    if (!username || !password) {
      seterrmsg("Enter both a username and a password.");
      return;
    }
    if (isreg && password.length < MIN_PASSWORD_LENGTH) {
      seterrmsg(`Password must be at least ${MIN_PASSWORD_LENGTH} characters.`);
      return;
    }
    if (isreg && confirmpassword !== password) {
      seterrmsg("Passwords don't match.");
      return;
    }

    setloading(true);
    seterrmsg("");
    setokmsg("");

    if (isreg) {
      const res = await register(username, password);
      if (res && res.token) {
        // Registration now returns a full session, same as login — no reason to
        // make the user re-enter their password immediately after choosing it.
        const uid = res.userId || 1;
        saveSession({ userId: uid, username: res.username || username, token: res.token, onboarded: res.onboarded, accentColor: res.accentColor, avatarUrl: res.avatarUrl, avatarFrame: res.avatarFrame, role: res.role });
        if (onLogin) {
          onLogin(uid);
        } else {
          window.location.reload();
        }
      } else {
        seterrmsg(res?.message || "Registration failed. That username might already be taken.");
      }
    } else {
      const res = await login(username, password);
      if (res && res.token) {
        const uid = res.userId || 1;
        saveSession({ userId: uid, username: res.username || username, token: res.token, onboarded: res.onboarded, accentColor: res.accentColor, avatarUrl: res.avatarUrl, avatarFrame: res.avatarFrame, role: res.role });
        if (onLogin) {
          onLogin(uid);
        } else {
          window.location.reload();
        }
      } else {
        seterrmsg(res?.message || "Invalid username or password.");
      }
    }
    setloading(false);
  }

  return (
    <>
    <div className="auth-shell">
      <div className="auth-card">
        <div style={{ display: "flex", justifyContent: "center", marginBottom: "10px" }}>
          <BrandMark size={36} />
        </div>
        <span className="eyebrow">RE:WATCH</span>
        <h1 className="auth-title">
          {isreg ? "Create Your Story Profile" : "Welcome Back"}
        </h1>
        <p className="auth-subtitle">
          Stories chosen for how you feel.
        </p>

        {errmsg && <div className="status-message status-message--error">{errmsg}</div>}
        {okmsg && <div className="status-message status-message--success">{okmsg}</div>}

        <form onSubmit={handlesubmit} className="auth-form">
          <div className="auth-field">
            <label htmlFor="login-username">Username</label>
            <input
              id="login-username"
              type="text"
              placeholder="Enter your username"
              value={username}
              onChange={(e) => setusername(e.target.value)}
              autoComplete="username"
            />
          </div>

          <div className="auth-field">
            <label htmlFor="login-password">Password</label>
            <div className="auth-password-wrap">
              <input
                id="login-password"
                type={showpassword ? "text" : "password"}
                className="auth-password-input"
                placeholder="Enter your password"
                value={password}
                onChange={(e) => setpassword(e.target.value)}
                autoComplete={isreg ? "new-password" : "current-password"}
              />
              <button
                type="button"
                className="auth-eye-toggle"
                onClick={() => setshowpassword((v) => !v)}
                aria-label={showpassword ? "Hide password" : "Show password"}
                tabIndex={-1}
              >
                {showpassword ? "🙈" : "👁"}
              </button>
            </div>
            {passwordTooShort && (
              <p className="auth-field-hint auth-field-hint--error">
                At least {MIN_PASSWORD_LENGTH} characters.
              </p>
            )}
          </div>

          {isreg && (
            <div className="auth-field">
              <label htmlFor="login-confirm-password">Confirm password</label>
              <div className="auth-password-wrap">
                <input
                  id="login-confirm-password"
                  type={showpassword ? "text" : "password"}
                  className="auth-password-input"
                  placeholder="Re-enter your password"
                  value={confirmpassword}
                  onChange={(e) => setconfirmpassword(e.target.value)}
                  autoComplete="new-password"
                />
              </div>
              {passwordsMismatch && (
                <p className="auth-field-hint auth-field-hint--error">Passwords don't match.</p>
              )}
              {isreg && confirmpassword.length > 0 && !passwordsMismatch && (
                <p className="auth-field-hint auth-field-hint--success">Passwords match.</p>
              )}
            </div>
          )}

          <button type="submit" className="btn-primary btn-block" disabled={loading}>
            {loading ? "Please wait..." : isreg ? "Register & Enter" : "Sign In"}
          </button>
        </form>

        {!isreg && (
          <div className="auth-toggle">
            <Link to="/forgot-password" className="auth-toggle-link">Forgot password?</Link>
          </div>
        )}

        <div className="auth-toggle">
          {isreg ? "Already have an account? " : "New to Re:Watch? "}
          <span
            className="auth-toggle-link"
            onClick={() => {
              setisreg(!isreg);
              seterrmsg("");
              setokmsg("");
            }}
          >
            {isreg ? "Sign in here" : "Create one now"}
          </span>
        </div>
      </div>
    </div>
    <Footer />
    </>
  );
}
