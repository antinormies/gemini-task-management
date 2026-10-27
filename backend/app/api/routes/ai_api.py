from __future__ import annotations

import asyncio

from fastapi import APIRouter, Depends, HTTPException, Request
from sqlalchemy import select
from sqlalchemy.orm import Session

from ...ai.clients import AIError
from ...models import AIProposal, AIRun, Device, Tag
from ...services import interpretation as interpretation_service
from ...services import maintenance
from ...services import projects as project_service
from ...services import proposals as proposal_service
from ...services import tasks as task_service
from ...services import usage as usage_service
from ...services.errors import ActionError, NotFound, ValidationError
from ...timeutils import now_iso
from ..deps import get_db, get_optional_device, get_settings, verify_web_origin
from ..models import ImportRequest, TextInterpretRequest, VoiceRequest

router = APIRouter(prefix="/api", tags=["ai"])


@router.post("/ai/interpret")
async def interpret_text(
    body: TextInterpretRequest,
    request: Request,
    session: Session = Depends(get_db),
    settings=Depends(get_settings),
):
    try:
        run = interpretation_service.create_text_run(
            session,
            settings,
            text=body.text,
            view=body.view,
            selected_task_id=body.selected_task_id,
            project_id=body.project_id,
        )
    except ValidationError as exc:
        raise HTTPException(status_code=422, detail=exc.message) from exc
    session.commit()
    task = asyncio.create_task(
        interpretation_service.process_text_run(
            request.app.state.db, settings, request.app.state.ai_service, run.id
        )
    )
    request.app.state.background_tasks.add(task)
    task.add_done_callback(request.app.state.background_tasks.discard)
    return {"run_id": run.id, "status": "queued"}


@router.get("/ai/runs/{run_id}")
def get_run(run_id: str, session: Session = Depends(get_db)):
    run = session.get(AIRun, run_id)
    if not run:
        raise HTTPException(status_code=404, detail="Run not found")
    return interpretation_service.run_payload(session, run)


@router.post("/ai/proposals/{proposal_id}/apply", dependencies=[Depends(verify_web_origin)])
def apply_proposal(
    proposal_id: str, session: Session = Depends(get_db), settings=Depends(get_settings)
):
    try:
        proposal, affected = proposal_service.apply_proposal(session, proposal_id, settings)
        return {
            "proposal": proposal_service.proposal_payload(session, proposal),
            "task_ids": [t.id for t in {task.id: task for task in affected}.values()],
        }
    except NotFound as exc:
        raise HTTPException(status_code=404, detail=exc.message) from exc
    except ActionError as exc:
        raise HTTPException(status_code=409, detail=exc.message) from exc
    except ValidationError as exc:
        raise HTTPException(status_code=422, detail=exc.message) from exc


@router.post("/ai/proposals/{proposal_id}/reject", dependencies=[Depends(verify_web_origin)])
def reject_proposal(proposal_id: str, session: Session = Depends(get_db)):
    proposal = session.get(AIProposal, proposal_id)
    if not proposal:
        raise HTTPException(status_code=404, detail="Proposal not found")
    if proposal.status != "pending":
        raise HTTPException(status_code=409, detail="Proposal is not pending")
    proposal.status = "rejected"
    proposal.resolved_at = now_iso()
    session.flush()
    return {"proposal": proposal_service.proposal_payload(session, proposal)}


@router.post("/ai/proposals/{proposal_id}/undo", dependencies=[Depends(verify_web_origin)])
def undo_proposal(proposal_id: str, session: Session = Depends(get_db)):
    try:
        proposal, restored = proposal_service.undo_proposal(session, proposal_id)
        return {
            "proposal": proposal_service.proposal_payload(session, proposal),
            "restored": [t.id for t in restored],
        }
    except NotFound as exc:
        raise HTTPException(status_code=404, detail=exc.message) from exc
    except ActionError as exc:
        raise HTTPException(status_code=409, detail=exc.message) from exc


@router.post("/voice/interpret")
async def interpret_voice(
    body: VoiceRequest,
    request: Request,
    device: Device | None = Depends(get_optional_device),
    settings=Depends(get_settings),
):
    try:
        run, proposal, interpretation = await interpretation_service.interpret_voice(
            request.app.state.db,
            settings,
            request.app.state.ai_service,
            transcript=body.transcript,
            duration_ms=body.duration_ms,
            view=body.view,
            selected_task_id=body.selected_task_id,
            project_id=body.project_id,
            device_id=device.id if device else None,
        )
        with request.app.state.db.session() as session:
            return {
                "run": interpretation_service.run_payload(session, session.get(AIRun, run.id)),
                "proposal": proposal_service.proposal_payload(
                    session, session.get(AIProposal, proposal.id)
                ),
                "transcript": interpretation.transcript,
                "assistant_message": interpretation.assistant_message,
            }
    except AIError as exc:
        raise HTTPException(
            status_code=502, detail={"code": exc.code, "message": exc.message}
        ) from exc
    except ValidationError as exc:
        raise HTTPException(status_code=422, detail=exc.message) from exc


@router.get("/usage")
def usage(
    request: Request,
    days: int = 7,
    session: Session = Depends(get_db),
    settings=Depends(get_settings),
):
    return usage_service.usage_summary(session, settings, request.app.state.db, days)


@router.post("/maintenance/backup", dependencies=[Depends(verify_web_origin)])
def backup(request: Request):
    return maintenance.create_backup(request.app.state.db, request.app.state.settings)


@router.post("/maintenance/rebuild-search", dependencies=[Depends(verify_web_origin)])
def rebuild_search(request: Request):
    return maintenance.rebuild_search(request.app.state.db)


@router.get("/export")
def export_data(session: Session = Depends(get_db), settings=Depends(get_settings)):
    projects = [
        project_service.project_payload(session, p)
        for p in project_service.list_projects(session, include_archived=True)
    ]
    tasks = [
        task_service.task_payload(session, t)
        for t in task_service.list_tasks(
            session, view="all", include_done=True, tz_name=settings.timezone
        )
    ]
    tags = [
        {"id": t.id, "name": t.name, "color": t.color} for t in session.scalars(select(Tag)).all()
    ]
    return {
        "version": 1,
        "timezone": settings.timezone,
        "projects": projects,
        "tasks": tasks,
        "tags": tags,
    }


@router.post("/import", dependencies=[Depends(verify_web_origin)])
def import_data(
    body: ImportRequest, session: Session = Depends(get_db), settings=Depends(get_settings)
):
    data = body.data
    created = 0
    for p in data.get("projects", []):
        try:
            project_service.create_project(session, p)
        except Exception:
            pass
    for t in data.get("tasks", []):
        if not t.get("title"):
            continue
        try:
            task_service.create_task(session, {**t, "id": None}, tz_name=settings.timezone)
            created += 1
        except Exception:
            continue
    return {"ok": True, "created": created}
