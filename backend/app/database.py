import os
import sqlite3
import time
import uuid
from typing import Optional

MIGRATION_FILE = os.path.join(
    os.path.dirname(__file__), "..", "migrations", "001_initial_commercial_schema.sql"
)


class CommercialDatabase:
    """
    SQLite-backed commercial persistence layer with automated SQL migration runner.
    Supports file-backed production databases and `:memory:` or temp files for isolated unit tests.
    """

    def __init__(self, db_path: Optional[str] = None) -> None:
        self.db_path = db_path or os.getenv(
            "NEXORA_DB_PATH",
            os.path.join(os.path.dirname(__file__), "..", "nexora_commercial.db"),
        )
        self._persistent_conn: Optional[sqlite3.Connection] = None
        if self.db_path == ":memory:":
            self._persistent_conn = sqlite3.connect(":memory:", check_same_thread=False)
            self._persistent_conn.row_factory = sqlite3.Row
            self._persistent_conn.execute("PRAGMA foreign_keys = ON;")
        self.run_migrations()

    def connect(self) -> sqlite3.Connection:
        if self._persistent_conn is not None:
            return self._persistent_conn
        conn = sqlite3.connect(self.db_path, check_same_thread=False)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA foreign_keys = ON;")
        return conn

    def run_migrations(self) -> None:
        with open(MIGRATION_FILE, "r", encoding="utf-8") as f:
            sql_script = f.read()

        conn = self.connect()
        try:
            conn.executescript(sql_script)
            conn.execute(
                "INSERT OR IGNORE INTO schema_migrations (version, applied_at) VALUES (?, ?)",
                ("001_initial_commercial_schema", int(time.time() * 1000)),
            )
            conn.commit()
        finally:
            if self._persistent_conn is None:
                conn.close()

    def log_event(self, level: str, category: str, message: str) -> None:
        conn = self.connect()
        try:
            conn.execute(
                "INSERT INTO audit_error_logs (id, level, category, message, created_at) VALUES (?, ?, ?, ?, ?)",
                (f"log-{uuid.uuid4().hex[:12]}", level, category, message[:500], int(time.time() * 1000)),
            )
            conn.commit()
        finally:
            if self._persistent_conn is None:
                conn.close()
