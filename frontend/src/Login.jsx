import { useState } from "react";
import { Link } from "react-router-dom";
import { login, register } from "./api";
import { saveSession } from "./auth";
import BrandMark from "./BrandMark";
import Footer from "./Footer";
import { IconEye, IconEyeOff } from "./Icons";
import "./App.css";

const MIN_PASSWORD_LENGTH = 6;

// Deliberately simple — not exhaustive RFC 5322 validation, just enough to
// catch "forgot the @" / "forgot the domain" before it becomes an account
// nobody can ever recover, which is the actual failure mode this exists to
// prevent (see the email field below).
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export default function Login({ onLogin }) {
  const [username, setusername] = useState("");
  const [email, setemail] = useState("");
  const [password, setpassword] = useState("");
  const [confirmpassword, setconfirmpassword] = useState("");
  const [showpassword, setshowpassword] = useState(false);
  const [isreg, setisreg] = useState(false);
  const [errmsg, seterrmsg] = useState("");
  const [okmsg, setokmsg] = useState("");
  const [loading, setloading] = useState(false);

  const passwordTooShort = isreg && password.length > 0 && password.length < MIN_PASSWORD_LENGTH;
  const passwordsMismatch = isreg && confirmpassword.length > 0 && confirmpassword !== password;
  const emailInvalid = isreg && email.length > 0 && !EMAIL_PATTERN.test(email);

  async function handlesubmit(e) {
    e.preventDefault();
    if (!username || !password) {
      seterrmsg("Enter both a username and a password.");
      return;
    }
    if (isreg && !EMAIL_PATTERN.test(email)) {
      // Without a real email on file, "Forgot password?" has nowhere to send
      // a reset link — a synthetic placeholder used to be silently substituted
      // here instead, which meant every account created through this form was
      // permanently unrecoverable the moment a password was forgotten. This
      // is now a hard requirement, not a nice-to-have.
      seterrmsg("Enter a real email — it's the only way to recover your account if you forget your password.");
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
      const res = await register(username, password, email);
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

          {isreg && (
            <div className="auth-field">
              <label htmlFor="login-email">Email</label>
              <input
                id="login-email"
                type="email"
                placeholder="you@example.com"
                value={email}
                onChange={(e) => setemail(e.target.value)}
                autoComplete="email"
              />
              <p className={`auth-field-hint${emailInvalid ? " auth-field-hint--error" : " auth-field-hint--placeholder"}`}>
                Enter a valid email.
              </p>
            </div>
          )}

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
                {showpassword ? <IconEyeOff /> : <IconEye />}
              </button>
            </div>
            {isreg && (
              <p className={`auth-field-hint${passwordTooShort ? " auth-field-hint--error" : " auth-field-hint--placeholder"}`}>
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
              <p
                className={`auth-field-hint${
                  passwordsMismatch
                    ? " auth-field-hint--error"
                    : confirmpassword.length > 0
                      ? " auth-field-hint--success"
                      : " auth-field-hint--placeholder"
                }`}
              >
                {passwordsMismatch
                  ? "Passwords don't match."
                  : confirmpassword.length > 0
                    ? "Passwords match."
                    : "Passwords don't match."}
              </p>
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
