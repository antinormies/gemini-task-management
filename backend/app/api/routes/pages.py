from __future__ import annotations

from pathlib import Path

from fastapi import APIRouter, Depends, Request
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.templating import Jinja2Templates
from sqlalchemy.orm import Session

from ...models import Account
from ...security import SESSION_COOKIE
from ...services import projects as project_service
from ...services import tasks as task_service
from ...tenancy import set_session_account
from ..deps import get_web_account

router = APIRouter(tags=["pages"])
TEMPLATES = Jinja2Templates(
    directory=str(Path(__file__).resolve().parents[2] / "web" / "templates")
)


def _context(
    session: Session,
    settings,
    account: Account,
    view: str,
    project_id: str | None = None,
) -> dict:
    counts = {
        key: len(
            task_service.list_tasks(
                session,
                view=view_name,
                project_id=project_id or None,
                tz_name=settings.timezone,
            )
        )
        for key, view_name in {
            "backlog": "backlog",
            "research": "research",
            "sprint": "sprint",
            "review": "review",
            "done": "done",
            "all": "all",
        }.items()
    }
    return {
        "view": view,
        "project_id": project_id or "",
        "account": {
            "name": account.name or account.email,
            "email": account.email,
            "picture": account.picture,
        },
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


def _render(request: Request, account: Account, view: str, project_id: str | None = None):
    session = request.app.state.db.sessionmaker()
    try:
        set_session_account(session, account.id)
        ctx = _context(session, request.app.state.settings, account, view, project_id)
    finally:
        session.close()
    return TEMPLATES.TemplateResponse(request, "index.html", ctx)


LEGACY_VIEWS = {
    "today": "all",
    "inbox": "backlog",
    "upcoming": "all",
    "anytime": "all",
    "waiting": "review",
}


@router.get("/login", response_class=HTMLResponse)
def login_page(
    request: Request,
    account: Account | None = Depends(get_web_account),
):
    if account:
        return RedirectResponse("/", status_code=303)
    return TEMPLATES.TemplateResponse(
        request,
        "login.html",
        {"google_client_id": request.app.state.settings.google_client_id},
    )


@router.post("/logout")
def logout_page():
    response = RedirectResponse("/login", status_code=303)
    response.delete_cookie(SESSION_COOKIE, path="/")
    return response


@router.get("/", response_class=HTMLResponse)
def home_page(
    request: Request,
    project: str | None = None,
    account: Account | None = Depends(get_web_account),
):
    if not account:
        return RedirectResponse("/login", status_code=303)
    return _render(request, account, "all", project)


@router.get("/{view}", response_class=HTMLResponse)
def view_page(
    view: str,
    request: Request,
    project: str | None = None,
    account: Account | None = Depends(get_web_account),
):
    if not account:
        return RedirectResponse("/login", status_code=303)
    if view not in {
        "all",
        "backlog",
        "research",
        "sprint",
        "review",
        "done",
        "settings",
        "today",
        "inbox",
        "upcoming",
        "anytime",
        "waiting",
    }:
        return _render(request, account, "all", project)
    return _render(request, account, LEGACY_VIEWS.get(view, view), project)


@router.get("/projects/{project_id}", response_class=HTMLResponse)
def project_page(
    project_id: str,
    request: Request,
    account: Account | None = Depends(get_web_account),
):
    if not account:
        return RedirectResponse("/login", status_code=303)
    return _render(request, account, "projects", project_id)
