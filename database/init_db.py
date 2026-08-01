import sqlite3

def init_db():
    conn = sqlite3.connect("rewatch.db")
    cursor = conn.cursor()

    cursor.execute("""
    CREATE TABLE IF NOT EXISTS users (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        username TEXT UNIQUE NOT NULL,
        dna TEXT DEFAULT '50,50,50,50,50,50,50,50,50,50'
    )
    """)

    cursor.execute("""
    CREATE TABLE IF NOT EXISTS movies (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        title TEXT NOT NULL,
        release_year INTEGER,
        storyprint TEXT NOT NULL,
        rating REAL DEFAULT 0.0
    )
    """)

    cursor.execute("""
    CREATE TABLE IF NOT EXISTS watch_logs (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        user_id INTEGER,
        movie_id INTEGER,
        rating REAL,
        review TEXT,
        FOREIGN KEY (user_id) REFERENCES users (id),
        FOREIGN KEY (movie_id) REFERENCES movies (id)
    )
    """)

    cursor.execute("SELECT COUNT(*) FROM movies")
    count = cursor.fetchone()[0]
    
    if count == 0:
        movies_data = [
            ("Interstellar", 2014, "68,95,82,90,40,60,98,15,78,70", 4.8),
            ("Parasite", 2019, "78,72,74,80,82,45,92,62,94,76", 4.6),
            ("Arrival", 2016, "50,85,88,85,45,30,95,10,70,80", 4.5),
            ("Mad Max: Fury Road", 2015, "95,40,30,85,60,98,40,20,85,40", 4.7)
        ]
        cursor.executemany(
            "INSERT INTO movies (title, release_year, storyprint, rating) VALUES (?, ?, ?, ?)",
            movies_data
        )

        cursor.execute("INSERT INTO users (username) VALUES (?)", ("test_user",))
        print("Sample movies and test user added!")

    conn.commit()
    conn.close()
    print("Database initialized successfully.")

if __name__ == "__main__":
    init_db()