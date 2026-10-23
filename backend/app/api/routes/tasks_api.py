from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Query, Request
from sqlalchemy.orm import Session

from ...services import projects as project_service
from ...services import tasks as task_service
from ...services import usage as usage_service
from ...services.errors import ConflictError, NotFound, ValidationError
from ...timeutils import now_iso
from ..deps import get_db, get_settings, verify_web_origin
from ..models import OrderRequest, TaskCreate, TaskUpdate

router = APIRouter(prefix="/api", tags=["tasks"])


def _counts(session: Session, settings) -> dict:
    names = {
        "inbox": "inbox",
        "today": "today",
        "upcoming": "upcoming",
        "waiting": "waiting",
        "done": "done",
    }
    return {
        key: len(task_service.list_tasks(session, view=view, tz_name=settings.timezone))
        for key, view in names.items()
    }


@router.get("/mobile/bootstrap")
def mobile_bootstrap(
    request: Request,
    session: Session = Depends(get_db),
    settings=Depends(get_settings),
):
    rows = task_service.list_tasks(session, view="board", tz_name=settings.timezone)
    return {
        "server_time": now_iso(),
        "timezone": settings.timezone,
        "projects": [
            project_service.project_payload(session, p)
            for p in project_service.list_projects(session)
        ],
        "tasks": [task_service.task_payload(session, t) for t in rows],
        "counts": _counts(session, settings),
        "usage": usage_service.usage_summary(session, settings, request.app.state.db, 7),
    }


@router.get("/tasks")
def list_tasks(
    view: str = "today",
    status: str | None = None,
    project_id: str | None = None,
    q: str | None = None,
    include_done: bool = False,
    session: Session = Depends(get_db),
    settings=Depends(get_settings),
):
    rows = task_service.list_tasks(
        session,
        view=view,
        status=status,
        project_id=project_id,
        q=q,
        include_done=include_done,
        tz_name=settings.timezone,
    )
    payload = {
        "items": [task_service.task_payload(session, t) for t in rows],
        "view": view,
        "counts": _counts(session, settings),
    }
    if view == "board":
        payload["board"] = task_service.board_payload(session, rows)
    return payload


@router.post("/tasks", dependencies=[Depends(verify_web_origin)])
def create_task(
    body: TaskCreate, session: Session = Depends(get_db), settings=Depends(get_settings)
):
    try:
        task = task_service.create_task(session, body.model_dump(), tz_name=settings.timezone)
        return {"task": task_service.task_payload(session, task, detail=True)}
    except ValidationError as exc:
        raise HTTPException(status_code=422, detail=exc.message) from exc


@router.get("/tasks/{task_id}")
def get_task(task_id: str, session: Session = Depends(get_db)):
    try:
        return {"task": task_service.detail_payload(session, task_id)}
    except NotFound as exc:
        raise HTTPException(status_code=404, detail=exc.message) from exc


@router.patch("/tasks/{task_id}", dependencies=[Depends(verify_web_origin)])
def update_task(
    task_id: str,
    body: TaskUpdate,
    session: Session = Depends(get_db),
    settings=Depends(get_settings),
):
    data = body.model_dump(exclude_unset=True)
    version = data.pop("version", None)
    tags = data.pop("tags", None)
    try:
        task = task_service.update_task(
            session,
            task_id,
            data,
            expected_version=version,
            tz_name=settings.timezone,
        )
        if tags is not None:
            task_service.set_tags(session, task.id, tags)
        return {"task": task_service.task_payload(session, task, detail=True)}
    except ConflictError as exc:
        current = task_service.task_payload(
            session, task_service.get_task(session, task_id), detail=True
        )
        raise HTTPException(
            status_code=409, detail={"message": exc.message, "task": current}
        ) from exc
    except NotFound as exc:
        raise HTTPException(status_code=404, detail=exc.message) from exc
    except ValidationError as exc:
        raise HTTPException(status_code=422, detail=exc.message) from exc


@router.post("/tasks/{task_id}/complete", dependencies=[Depends(verify_web_origin)])
def complete_task(
    task_id: str, version: int | None = Query(default=None), session: Session = Depends(get_db)
):
    try:
        task = task_service.complete_task(session, task_id, expected_version=version)
        return {"task": task_service.task_payload(session, task, detail=True)}
    except ConflictError as exc:
        raise HTTPException(status_code=409, detail=exc.message) from exc
    except NotFound as exc:
        raise HTTPException(status_code=404, detail=exc.message) from exc


@router.post("/tasks/{task_id}/reopen", dependencies=[Depends(verify_web_origin)])
def reopen_task(task_id: str, session: Session = Depends(get_db)):
    try:
        task = task_service.reopen_task(session, task_id)
        return {"task": task_service.task_payload(session, task, detail=True)}
    except (NotFound, ValidationError) as exc:
        raise HTTPException(
            status_code=404 if isinstance(exc, NotFound) else 422, detail=exc.message
        ) from exc


@router.delete("/tasks/{task_id}", dependencies=[Depends(verify_web_origin)])
def archive_task(task_id: str, session: Session = Depends(get_db)):
    try:
        task_service.archive_task(session, task_id)
        return {"ok": True}
    except NotFound as exc:
        raise HTTPException(status_code=404, detail=exc.message) from exc


@router.post("/tasks/{task_id}/order", dependencies=[Depends(verify_web_origin)])
def reorder_task(task_id: str, body: OrderRequest, session: Session = Depends(get_db)):
    try:
        task = task_service.reorder_task(session, task_id, body.sort_order)
        return {"task": task_service.task_payload(session, task)}
    except NotFound as exc:
        raise HTTPException(status_code=404, detail=exc.message) from exc


@router.post("/tasks/{task_id}/tags", dependencies=[Depends(verify_web_origin)])
def set_tags(task_id: str, tags: list[str], session: Session = Depends(get_db)):
    try:
        task_service.set_tags(session, task_id, tags)
        return {
            "task": task_service.task_payload(
                session, task_service.get_task(session, task_id), detail=True
            )
        }
    except NotFound as exc:
        raise HTTPException(status_code=404, detail=exc.message) from exc


@router.get("/search")
def search(
    q: str = Query(min_length=1), session: Session = Depends(get_db), settings=Depends(get_settings)
):
    rows = task_service.list_tasks(
        session, view="all", q=q, include_done=True, tz_name=settings.timezone
    )
    return {
        "items": [task_service.task_payload(session, t) for t in rows[:50]],
        "counts": _counts(session, settings),
    }
