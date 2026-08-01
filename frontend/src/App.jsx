import { useState, useEffect } from "react";
import MovieFeed from "./MovieFeed";
import Search from "./Search";
import TasteDNA from "./TasteDNA";
import Dashboard from "./Dashboard";
import Login from "./Login";
import "./App.css";

export default function App() {
  const [currentview, setcurrentview] = useState("movies");
  const [userid, setuserid] = useState(null);

  useEffect(() => {
    checklogin();
  }, []);

  function checklogin() {
    const savedid = localStorage.getItem("userId");
    if (savedid) {
      setuserid(savedid);
    }
  }

  function handlelogout() {
    localStorage.removeItem("userId");
    localStorage.removeItem("username");
    setuserid(null);
    setcurrentview("login");
  }

  function handlelogin(id) {
    setuserid(id);
    setcurrentview("movies");
  }

  const username = localStorage.getItem("username") || "friend";

  return (
    <div>
      {/* GLOBAL NAVBAR */}
      <nav>
        <button
          type="button"
          onClick={() => setcurrentview("movies")}
          style={{
            color: currentview === "movies" ? "#fff" : "#a1a1aa",
            background: currentview === "movies" ? "rgba(255, 255, 255, 0.1)" : "transparent"
          }}
        >
          movies
        </button>

        <button
          type="button"
          onClick={() => setcurrentview("search")}
          style={{
            color: currentview === "search" ? "#fff" : "#a1a1aa",
            background: currentview === "search" ? "rgba(255, 255, 255, 0.1)" : "transparent"
          }}
        >
          search & vibe check
        </button>

        <button
          type="button"
          onClick={() => setcurrentview("tastedna")}
          style={{
            color: currentview === "tastedna" ? "#fff" : "#a1a1aa",
            background: currentview === "tastedna" ? "rgba(255, 255, 255, 0.1)" : "transparent"
          }}
        >
          taste dna
        </button>

        <button
          type="button"
          onClick={() => setcurrentview("dashboard")}
          style={{
            color: currentview === "dashboard" ? "#fff" : "#a1a1aa",
            background: currentview === "dashboard" ? "rgba(255, 255, 255, 0.1)" : "transparent"
          }}
        >
          my dashboard
        </button>

        {userid ? (
          <div style={{ marginLeft: "auto", display: "flex", alignItems: "center", gap: "10px" }}>
            <span>hi {username}</span>
            <button
              type="button"
              onClick={handlelogout}
              style={{
                background: "rgba(239, 68, 68, 0.15)",
                border: "1px solid rgba(239, 68, 68, 0.3)",
                color: "#fca5a5",
                padding: "6px 12px",
                fontSize: "12px",
                borderRadius: "8px"
              }}
            >
              logout
            </button>
          </div>
        ) : (
          <button
            type="button"
            onClick={() => setcurrentview("login")}
            style={{ marginLeft: "auto", color: "#a855f7" }}
          >
            login
          </button>
        )}
      </nav>

      {/* ACTIVE VIEW RENDERING */}
      {currentview === "login" && <Login onLogin={handlelogin} />}
      {currentview === "movies" && <MovieFeed />}
      {currentview === "search" && <Search />}
      {currentview === "tastedna" && <TasteDNA />}
      {currentview === "dashboard" && <Dashboard />}
    </div>
  );
}