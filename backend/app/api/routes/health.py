from __future__ import annotations

from fastapi import APIRouter, Depends, Request
from sqlalchemy import text
from sqlalchemy.orm import Session

from ..deps import get_ai_service, get_db, get_settings

router = APIRouter(prefix="/api", tags=["health"])


@router.get("/health")
def health(
    request: Request,
    session: Session = Depends(get_db),
    settings=Depends(get_settings),
    ai=Depends(get_ai_service),
):
    db_ok = True
    db_error = None
    try:
        session.execute(text("SELECT 1"))
    except Exception as exc:
        db_ok = False
        db_error = str(exc)
    return {
        "app": "gtm",
        "version": "0.1.0",
        "database": {"ok": db_ok, "error": db_error, "path": str(settings.resolved_db_path)},
        "ai": ai.status(),
        "voice": {
            "enabled": settings.voice_enabled,
            "provider": "gateway",
            "input": "system_stt",
            "max_seconds": settings.voice_max_seconds,
        },
    }
