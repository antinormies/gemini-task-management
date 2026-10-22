from __future__ import annotations

from ..config import Settings
from ..db import Database


def create_backup(db: Database, settings: Settings) -> dict:
    target = db.backup()
    return {"path": str(target), "bytes": target.stat().st_size}


def rebuild_search(db: Database) -> dict:
    db.rebuild_search()
    return {"ok": True, "message": "FTS5 index rebuilt"}
