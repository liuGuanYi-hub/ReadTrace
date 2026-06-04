from pathlib import Path
import sqlite3

DB_PATH = Path(__file__).resolve().parents[1] / "readtrace.db"


def get_connection() -> sqlite3.Connection:
    connection = sqlite3.connect(DB_PATH)
    connection.row_factory = sqlite3.Row
    return connection


def init_db() -> None:
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    with get_connection() as connection:
        connection.execute(
            """
            CREATE TABLE IF NOT EXISTS books (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                author TEXT,
                cover_url TEXT,
                category TEXT,
                status TEXT NOT NULL DEFAULT 'wishlist',
                rating REAL,
                tags TEXT,
                short_comment TEXT,
                review TEXT,
                start_date TEXT,
                finish_date TEXT,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                is_deleted INTEGER NOT NULL DEFAULT 0,
                deleted_at TEXT
            );
            """
        )
        connection.execute(
            """
            CREATE INDEX IF NOT EXISTS idx_books_status_deleted
            ON books(status, is_deleted);
            """
        )
        connection.execute(
            """
            CREATE INDEX IF NOT EXISTS idx_books_updated_at
            ON books(updated_at);
            """
        )

