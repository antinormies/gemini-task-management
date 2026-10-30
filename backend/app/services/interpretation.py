from __future__ import annotations

import json
import re
from datetime import datetime

from sqlalchemy import select
from sqlalchemy.orm import Session

from ..ai.clients import AIError
from ..ai.prompts import build_compact_prompt, build_prompt, build_voice_prompt
from ..ai.schemas import CompleteTaskAction, Interpretation, UpdateChanges, UpdateTaskAction
from ..ai.service import AIService
from ..config import Settings
from ..db import Database
from ..models import AIProposal, AIRun, Task
from ..tenancy import set_session_account
from ..timeutils import now_iso, now_utc
from . import projects as project_service
from . import proposals as proposal_service
from .errors import NotFound, ValidationError


def _asserted_firebase_uid(settings: Settings, account_id: str | None) -> str:
    if settings.ai_firebase_uid:
        return settings.ai_firebase_uid
    return f"gtm-{account_id}" if account_id else ""


STAGE_MOVE = re.compile(
    r"\bmove\b[^.]*?\bto\b\s+(backlog|research|sprint|review|done)\b", re.IGNORECASE
)


def _repair_stage_move(interpretation, text: str):
    """The model sometimes emits complete_task for "move <task> to <stage>".

    A stage move is an update; repair it deterministically so the user's
    explicit stage wording cannot complete the task by accident.
    """
    match = STAGE_MOVE.search(text or "")
    if not match:
        return interpretation
    stage = match.group(1).lower()
    actions = []
    changed = False
    for action in interpretation.actions:
        if isinstance(action, CompleteTaskAction):
            actions.append(
                UpdateTaskAction(
                    type="update_task",
                    task_id=action.task_id,
                    changes=UpdateChanges(status=stage),
                )
            )
            changed = True
        else:
            actions.append(action)
    if not changed:
        return interpretation
    return interpretation.model_copy(update={"actions": actions, "intent": "update_task"})


def _context(
    session: Session, view: str, selected_task_id: str | None, project_id: str | None = None
) -> dict:
    projects = project_service.list_projects(session)
    selected = None
    if selected_task_id:
        task = session.get(Task, selected_task_id)
        if task:
            selected = {
                "id": task.id,
                "title": task.title,
                "status": task.status,
                "priority": task.priority,
                "due_at": task.due_at,
                "project_id": task.project_id,
            }
    candidates = session.scalars(
        select(Task)
        .where(Task.archived_at.is_(None), Task.status.notin_(("done", "cancelled")))
        .order_by(Task.updated_at.desc())
        .limit(40)
    ).all()
    return {
        "view": view,
        "selected_task_id": selected_task_id,
        "current_project_id": project_id,
        "project_ids": [p.id for p in projects],
        "projects": [{"id": p.id, "name": p.name} for p in projects],
        "selected_task": selected,
        "tasks": [
            {
                "id": t.id,
                "title": t.title,
                "status": t.status,
                "priority": t.priority,
                "due_at": t.due_at,
                "project_id": t.project_id,
            }
            for t in candidates
        ],
        "now": now_utc().isoformat(),
        "timezone": "",
        "schema_version": 1,
    }


def create_text_run(
    session: Session,
    settings: Settings,
    *,
    text: str,
    view: str = "today",
    selected_task_id: str | None = None,
    project_id: str | None = None,
    request_id: str | None = None,
) -> AIRun:
    text = (text or "").strip()
    if not text:
        raise ValidationError("Text is required")
    if len(text) > 4000:
        raise ValidationError("Text is limited to 4,000 characters")
    context = _context(session, view, selected_task_id, project_id)
    context["timezone"] = settings.timezone
    run = AIRun(
        request_id=request_id or __import__("app.security", fromlist=["new_id"]).new_id(),
        kind="interpret",
        status="queued",
        model=settings.ai_model,
        input_text=text,
        context_json=json.dumps(context, ensure_ascii=False),
    )
    session.add(run)
    session.flush()
    return run


async def process_text_run(
    db: Database, settings: Settings, ai_service: AIService, run_id: str
) -> None:
    with db.session() as session:
        run = session.get(AIRun, run_id)
        if not run or run.status not in ("queued", "running"):
            return
        set_session_account(session, run.account_id)
        asserted_uid = _asserted_firebase_uid(settings, run.account_id)
        run.status = "running"
        run.started_at = now_iso()
        context = json.loads(run.context_json or "{}")
        if settings.ai_capability == "chat.standard":
            prompt = build_compact_prompt(
                settings,
                run.input_text,
                projects=context.get("projects", []),
                selected_task=context.get("selected_task"),
                current_project=context.get("current_project_id"),
                now=now_utc(),
                tasks=context.get("tasks", []),
            )
        else:
            prompt = build_prompt(
                settings,
                run.input_text,
                projects=context.get("projects", []),
                selected_task=context.get("selected_task"),
                current_project=context.get("current_project_id"),
                now=now_utc(),
                tasks=context.get("tasks", []),
            )
    started = datetime.now()
    try:
        result = await ai_service.interpret(prompt, firebase_uid=asserted_uid)
    except AIError as exc:
        with db.session() as session:
            run = session.get(AIRun, run_id)
            if run:
                set_session_account(session, run.account_id)
                run.status = "failed"
                run.error_code = exc.code
                run.error_message = exc.message
                run.finished_at = now_iso()
                run.duration_ms = int((datetime.now() - started).total_seconds() * 1000)
        return
    with db.session() as session:
        run = session.get(AIRun, run_id)
        if not run:
            return
        set_session_account(session, run.account_id)
        run.status = "succeeded"
        run.model = result.model
        run.finished_at = now_iso()
        run.duration_ms = result.duration_ms
        run.raw_envelope_json = json.dumps(result.raw, ensure_ascii=False)
        run.structured_output_json = json.dumps(
            result.interpretation.model_dump(mode="json"), ensure_ascii=False
        )
        run.input_tokens = result.usage.get("input_tokens")
        run.output_tokens = result.usage.get("output_tokens")
        run.total_tokens = result.usage.get("total_tokens")
        try:
            context = json.loads(run.context_json or "{}")
            context["provider"] = result.provider
            run.context_json = json.dumps(context, ensure_ascii=False)
            interpretation = _repair_stage_move(result.interpretation, run.input_text)
            proposal_service.create_proposal(session, run, interpretation, settings)
        except Exception as exc:
            run.status = "failed"
            run.error_code = "PROPOSAL_INVALID"
            run.error_message = str(exc)[:1000]


async def interpret_voice(
    db: Database,
    settings: Settings,
    ai_service: AIService,
    *,
    transcript: str,
    duration_ms: int,
    view: str = "speak",
    selected_task_id: str | None = None,
    project_id: str | None = None,
    device_id: str | None = None,
    account_id: str,
) -> tuple[AIRun, AIProposal, Interpretation]:
    transcript = (transcript or "").strip()
    if not transcript:
        raise ValidationError("Transcript is required")
    if len(transcript) > settings.voice_max_chars:
        raise ValidationError("Transcript is too long")
    if duration_ms < 0 or duration_ms > settings.voice_max_seconds * 1000:
        raise ValidationError("Voice duration is out of range")
    with db.session() as session:
        set_session_account(session, account_id)
        context = _context(session, view, selected_task_id, project_id)
        context["timezone"] = settings.timezone
        context["voice_input"] = "system_stt"
        run = AIRun(
            request_id=__import__("app.security", fromlist=["new_id"]).new_id(),
            kind="voice_interpret",
            status="running",
            model=settings.ai_model,
            input_text=transcript,
            context_json=json.dumps(context, ensure_ascii=False),
            speech_ms=duration_ms,
            device_id=device_id,
            started_at=now_iso(),
        )
        session.add(run)
        session.flush()
        set_session_account(session, run.account_id)
        asserted_uid = _asserted_firebase_uid(settings, account_id)
        run_id = run.id
        if settings.ai_capability == "chat.standard":
            prompt = build_compact_prompt(
                settings,
                transcript,
                projects=context.get("projects", []),
                selected_task=context.get("selected_task"),
                current_project=context.get("current_project_id"),
                now=now_utc(),
                tasks=context.get("tasks", []),
                source="voice transcript",
            )
        else:
            prompt = build_voice_prompt(
                settings,
                transcript,
                projects=context.get("projects", []),
                selected_task=context.get("selected_task"),
                current_project=context.get("current_project_id"),
                now=now_utc(),
                tasks=context.get("tasks", []),
            )
    started = datetime.now()
    try:
        result = await ai_service.interpret(prompt, firebase_uid=asserted_uid)
    except AIError as exc:
        with db.session() as session:
            run = session.get(AIRun, run_id)
            if run:
                set_session_account(session, run.account_id)
                run.status = "failed"
                run.error_code = exc.code
                run.error_message = exc.message
                run.finished_at = now_iso()
                run.duration_ms = int((datetime.now() - started).total_seconds() * 1000)
        raise
    with db.session() as session:
        run = session.get(AIRun, run_id)
        if not run:
            raise NotFound("Run disappeared")
        set_session_account(session, run.account_id)
        run.status = "succeeded"
        run.model = result.model
        run.finished_at = now_iso()
        run.duration_ms = result.duration_ms
        run.raw_envelope_json = json.dumps(result.raw, ensure_ascii=False)
        run.structured_output_json = json.dumps(
            result.interpretation.model_dump(mode="json"), ensure_ascii=False
        )
        run.input_tokens = result.usage.get("input_tokens")
        run.output_tokens = result.usage.get("output_tokens")
        run.total_tokens = result.usage.get("total_tokens")
        context = json.loads(run.context_json or "{}")
        context["provider"] = result.provider
        run.context_json = json.dumps(context, ensure_ascii=False)
        interpretation = _repair_stage_move(result.interpretation, transcript)
        interpretation = interpretation.model_copy(update={"transcript": transcript})
        proposal = proposal_service.create_proposal(session, run, interpretation, settings)
        return run, proposal, interpretation


def run_payload(session: Session, run: AIRun) -> dict:
    proposal = session.scalar(select(AIProposal).where(AIProposal.ai_run_id == run.id))
    return {
        "id": run.id,
        "kind": run.kind,
        "status": run.status,
        "model": run.model,
        "input_text": run.input_text,
        "error_code": run.error_code,
        "error_message": run.error_message,
        "duration_ms": run.duration_ms,
        "speech_ms": run.speech_ms,
        "tokens": {
            "input": run.input_tokens,
            "output": run.output_tokens,
            "total": run.total_tokens,
        },
        "created_at": run.created_at,
        "started_at": run.started_at,
        "finished_at": run.finished_at,
        "proposal": proposal_service.proposal_payload(session, proposal) if proposal else None,
    }
