from __future__ import annotations

from datetime import timedelta
from pathlib import Path

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from ..config import Settings
from ..db import Database
from ..models import AIProposal, AIRun, Device, Project, Task
from ..timeutils import iso, now_utc


def _dir_size(path: Path) -> int:
    if not path.exists():
        return 0
    total = 0
    for item in path.rglob("*"):
        if item.is_file():
            try:
                total += item.stat().st_size
            except OSError:
                pass
    return total


def usage_summary(session: Session, settings: Settings, db: Database, days: int = 7) -> dict:
    days = max(1, min(days, 90))
    cutoff = iso(now_utc() - timedelta(days=days))
    runs = list(session.scalars(select(AIRun).where(AIRun.created_at >= cutoff)).all())
    total_tokens = sum((r.total_tokens or 0) for r in runs)
    total_speech = sum((r.speech_ms or 0) for r in runs)
    total_duration = sum((r.duration_ms or 0) for r in runs)
    by_day: dict[str, dict] = {}
    for run in runs:
        day = run.created_at[:10]
        row = by_day.setdefault(day, {"date": day, "runs": 0, "tokens": 0, "speech_ms": 0})
        row["runs"] += 1
        row["tokens"] += run.total_tokens or 0
        row["speech_ms"] += run.speech_ms or 0
    # A rough displayed estimate; actual billing is provider-specific.
    estimated_cost = round((total_tokens / 1_000_000) * 0.75, 4)
    db_size = db.path.stat().st_size if db.path.exists() else 0
    return {
        "days": days,
        "runs": len(runs),
        "voice_runs": sum(1 for r in runs if r.kind == "voice_interpret"),
        "tokens": total_tokens,
        "speech_ms": total_speech,
        "latency_ms": int(total_duration / len(runs)) if runs else 0,
        "estimated_cost_usd": estimated_cost,
        "by_day": sorted(by_day.values(), key=lambda x: x["date"]),
        "storage": {
            "db_bytes": db_size,
            "backups_bytes": _dir_size(settings.backups_dir),
            "logs_bytes": _dir_size(settings.logs_dir),
            "workspace_bytes": _dir_size(settings.workspace_dir),
        },
        "counts": {
            "tasks": session.scalar(select(func.count(Task.id)).where(Task.archived_at.is_(None)))
            or 0,
            "projects": session.scalar(
                select(func.count(Project.id)).where(Project.archived_at.is_(None))
            )
            or 0,
            "devices": session.scalar(
                select(func.count(Device.id)).where(Device.revoked_at.is_(None))
            )
            or 0,
            "proposals": session.scalar(select(func.count(AIProposal.id))) or 0,
        },
    }
