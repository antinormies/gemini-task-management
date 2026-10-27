from __future__ import annotations

import sqlite3
from collections.abc import Iterator
from contextlib import contextmanager
from pathlib import Path

from sqlalchemy import create_engine, event, text
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker
from sqlalchemy.pool import StaticPool

from .config import Settings


class Base(DeclarativeBase):
    pass


FTS_SQL = [
    """CREATE VIRTUAL TABLE IF NOT EXISTS tasks_fts USING fts5(
        title, description, content='tasks', content_rowid='rowid',
        tokenize='unicode61 remove_diacritics 2'
    )""",
    """CREATE TRIGGER IF NOT EXISTS tasks_fts_ai AFTER INSERT ON tasks BEGIN
        INSERT INTO tasks_fts(rowid, title, description)
        VALUES (new.rowid, new.title, new.description);
    END""",
    """CREATE TRIGGER IF NOT EXISTS tasks_fts_ad AFTER DELETE ON tasks BEGIN
        INSERT INTO tasks_fts(tasks_fts, rowid, title, description)
        VALUES ('delete', old.rowid, old.title, old.description);
    END""",
    """CREATE TRIGGER IF NOT EXISTS tasks_fts_au AFTER UPDATE OF title, description ON tasks BEGIN
        INSERT INTO tasks_fts(tasks_fts, rowid, title, description)
        VALUES ('delete', old.rowid, old.title, old.description);
        INSERT INTO tasks_fts(rowid, title, description)
        VALUES (new.rowid, new.title, new.description);
    END""",
]


class Database:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.path = settings.resolved_db_path
        self.path.parent.mkdir(parents=True, exist_ok=True)
        use_memory = str(self.path) == ":memory:"
        connect_args = {"check_same_thread": False, "timeout": 5}
        kwargs = dict(
            connect_args=connect_args,
            future=True,
        )
        if use_memory:
            kwargs["poolclass"] = StaticPool
        self.engine = create_engine(f"sqlite+pysqlite:///{self.path}", **kwargs)
        event.listen(self.engine, "connect", _on_connect)
        self.sessionmaker = sessionmaker(bind=self.engine, expire_on_commit=False, autoflush=False)

    def init(self) -> None:
        from . import models  # noqa: F401

        Base.metadata.create_all(self.engine)
        with self.engine.begin() as conn:
            for statement in FTS_SQL:
                conn.execute(text(statement))

    @contextmanager
    def session(self) -> Iterator[Session]:
        session = self.sessionmaker()
        try:
            yield session
            session.commit()
        except Exception:
            session.rollback()
            raise
        finally:
            session.close()

    def rebuild_search(self) -> None:
        with self.engine.begin() as conn:
            conn.execute(text("INSERT INTO tasks_fts(tasks_fts) VALUES('rebuild')"))

    def backup(self, target: Path | None = None) -> Path:
        target = (
            target
            or self.settings.backups_dir
            / f"gtm-{__import__('datetime').datetime.now().strftime('%Y%m%d-%H%M%S')}.db"
        )
        target.parent.mkdir(parents=True, exist_ok=True)
        source = sqlite3.connect(self.path)
        dest = sqlite3.connect(target)
        try:
            source.backup(dest)
        finally:
            source.close()
            dest.close()
        return target


def _on_connect(dbapi_connection, _connection_record) -> None:
    cursor = dbapi_connection.cursor()
    cursor.execute("PRAGMA journal_mode=WAL")
    cursor.execute("PRAGMA synchronous=NORMAL")
    cursor.execute("PRAGMA foreign_keys=ON")
    cursor.execute("PRAGMA busy_timeout=5000")
    cursor.execute("PRAGMA temp_store=MEMORY")
    cursor.close()
