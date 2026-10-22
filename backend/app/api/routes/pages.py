from __future__ import annotations

from pathlib import Path

from fastapi import APIRouter, Request
from fastapi.responses import HTMLResponse
from fastapi.templating import Jinja2Templates
from sqlalchemy.orm import Session

from ...services import projects as project_service
from ...services import tasks as task_service

router = APIRouter(tags=["pages"])
TEMPLATES = Jinja2Templates(
    directory=str(Path(__file__).resolve().parents[2] / "web" / "templates")
)


def _context(session: Session, settings, view: str, project_id: str | None = None) -> dict:
    counts = {
        key: len(task_service.list_tasks(session, view=view_name, tz_name=settings.timezone))
        for key, view_name in {
            "inbox": "inbox",
            "today": "today",
            "upcoming": "upcoming",
            "waiting": "waiting",
            "done": "done",
        }.items()
    }
    return {
        "view": view,
        "project_id": project_id or "",
        "projects": [
            project_service.project_payload(session, p)
            for p in project_service.list_projects(session)
        ],
        "counts": counts,
        "settings": {
            "timezone": settings.timezone,
            "work_start": settings.work_start,
            "work_end": settings.work_end,
            "ai_enabled": settings.ai_enabled,
            "voice_enabled": settings.voice_enabled,
        },
    }


def _render(request: Request, view: str, project_id: str | None = None):
    session = request.app.state.db.sessionmaker()
    try:
        ctx = _context(session, request.app.state.settings, view, project_id)
    finally:
        session.close()
    return TEMPLATES.TemplateResponse(request, "index.html", ctx)


@router.get("/", response_class=HTMLResponse)
def today_page(request: Request):
    return _render(request, "today")


@router.get("/{view}", response_class=HTMLResponse)
def view_page(view: str, request: Request):
    if view not in {"today", "inbox", "upcoming", "anytime", "waiting", "done", "settings"}:
        return _render(request, "today")
    return _render(request, view)


@router.get("/projects/{project_id}", response_class=HTMLResponse)
def project_page(project_id: str, request: Request):
    return _render(request, "projects", project_id)
