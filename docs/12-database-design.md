# 12 - Database Design

## SQLite Schema (`init_db.py`)

### `users` Table
* `id` (INTEGER PRIMARY KEY AUTOINCREMENT)
* `username` (TEXT UNIQUE NOT NULL)
* `dna` (TEXT - comma-separated 10-point vector)

### `movies` Table
* `id` (INTEGER PRIMARY KEY AUTOINCREMENT)
* `title` (TEXT NOT NULL)
* `release_year` (INTEGER)
* `storyprint` (TEXT - comma-separated 10-point vector)
* `rating` (REAL)

### `watch_logs` Table
* `id` (INTEGER PRIMARY KEY AUTOINCREMENT)
* `user_id` (INTEGER FK)
* `movie_id` (INTEGER FK)
* `rating` (REAL)