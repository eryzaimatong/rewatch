import { useState } from "react";
import { login, register } from "./api";

export default function Login({ onLogin }) {
  const [username, setusername] = useState("");
  const [password, setpassword] = useState("");
  const [isreg, setisreg] = useState(false);
  const [errmsg, seterrmsg] = useState("");
  const [loading, setloading] = useState(false);

  async function handlesubmit(e) {
    e.preventDefault();
    if (!username || !password) {
      seterrmsg("fill in both fields bro");
      return;
    }

    setloading(true);
    seterrmsg("");

    if (isreg) {
      const res = await register(username, password);
      if (res) {
        alert("account created! log in now.");
        setisreg(false);
      } else {
        seterrmsg("registration failed bro. username might be taken.");
      }
    } else {
      const res = await login(username, password);
      if (res && (res.id || res.userId || res.token)) {
        const uid = res.id || res.userId || 1;
        localStorage.setItem("userId", uid);
        localStorage.setItem("username", username);
        if (onLogin) {
          onLogin(uid);
        } else {
          window.location.reload();
        }
      } else {
        seterrmsg("invalid login credentials bro");
      }
    }
    setloading(false);
  }

  return (
    <div 
      style={{
        minHeight: "80vh",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: "20px"
      }}
    >
      <div 
        style={{
          width: "100%",
          maxWidth: "440px",
          background: "linear-gradient(145deg, rgba(20, 21, 26, 0.95) 0%, rgba(30, 15, 50, 0.85) 100%)",
          border: "1px solid rgba(168, 85, 247, 0.3)",
          borderRadius: "28px",
          padding: "40px",
          boxShadow: "0 25px 60px rgba(0, 0, 0, 0.6)",
          textAlign: "center"
        }}
      >
        {/* BRAND HEADER */}
        <span style={{ fontSize: "12px", fontWeight: "800", letterSpacing: "3px", color: "#a855f7", textTransform: "uppercase" }}>
          RE:WATCH
        </span>
        <h1 style={{ fontSize: "28px", fontWeight: "800", color: "#fff", margin: "10px 0 8px 0" }}>
          {isreg ? "Create Your Story Profile" : "Welcome Back"}
        </h1>
        <p style={{ color: "#a1a1aa", fontSize: "14px", lineHeight: "1.5", margin: "0 0 28px 0" }}>
          "People don't remember the movie. They remember how it made them feel."
        </p>

        {/* ERROR BOX */}
        {errmsg && (
          <div 
            style={{
              background: "rgba(239, 68, 68, 0.15)",
              border: "1px solid rgba(239, 68, 68, 0.4)",
              color: "#fca5a5",
              padding: "12px",
              borderRadius: "12px",
              fontSize: "13px",
              marginBottom: "20px"
            }}
          >
            {errmsg}
          </div>
        )}

        {/* AUTH FORM */}
        <form onSubmit={handlesubmit} style={{ textAlign: "left" }}>
          <div style={{ marginBottom: "16px" }}>
            <label style={{ display: "block", fontSize: "13px", fontWeight: "600", color: "#e4e4e7", marginBottom: "6px" }}>
              Username
            </label>
            <input
              type="text"
              placeholder="enter your username..."
              value={username}
              onChange={(e) => setusername(e.target.value)}
              style={{
                width: "100%",
                padding: "14px 16px",
                borderRadius: "12px",
                background: "rgba(11, 12, 16, 0.8)",
                border: "1px solid rgba(255, 255, 255, 0.12)",
                color: "#fff",
                fontSize: "14px"
              }}
            />
          </div>

          <div style={{ marginBottom: "24px" }}>
            <label style={{ display: "block", fontSize: "13px", fontWeight: "600", color: "#e4e4e7", marginBottom: "6px" }}>
              Password
            </label>
            <input
              type="password"
              placeholder="••••••••"
              value={password}
              onChange={(e) => setpassword(e.target.value)}
              style={{
                width: "100%",
                padding: "14px 16px",
                borderRadius: "12px",
                background: "rgba(11, 12, 16, 0.8)",
                border: "1px solid rgba(255, 255, 255, 0.12)",
                color: "#fff",
                fontSize: "14px"
              }}
            />
          </div>

          <button
            type="submit"
            disabled={loading}
            style={{
              width: "100%",
              padding: "14px",
              borderRadius: "12px",
              background: "#a855f7",
              color: "#fff",
              fontWeight: "700",
              fontSize: "15px",
              border: "none",
              cursor: "pointer",
              boxShadow: "0 6px 20px rgba(168, 85, 247, 0.4)"
            }}
          >
            {loading ? "Please wait..." : isreg ? "Register & Enter" : "Sign In"}
          </button>
        </form>

        {/* TOGGLE REGISTRATION */}
        <div style={{ marginTop: "24px", fontSize: "13px", color: "#a1a1aa" }}>
          {isreg ? "Already have an account? " : "New to Re:Watch? "}
          <span
            onClick={() => {
              setisreg(!isreg);
              seterrmsg("");
            }}
            style={{ color: "#c084fc", fontWeight: "700", cursor: "pointer" }}
          >
            {isreg ? "Sign in here" : "Create one now"}
          </span>
        </div>
      </div>
    </div>
  );
}