from __future__ import annotations

import json
from datetime import date, datetime, time, timedelta

from pydantic import TypeAdapter
from sqlalchemy.orm import Session

from ..ai.schemas import (
    Action,
    ArchiveTaskAction,
    CompleteTaskAction,
    CreateSubtasksAction,
    CreateTaskAction,
    Interpretation,
    PlanDayAction,
    QueryTasksAction,
    SummarizeAction,
    UpdateTaskAction,
)
from ..config import Settings
from ..models import AIProposal, AIRun, Project, Task
from ..timeutils import human_short_key, iso, now_iso, now_utc, tz
from . import tasks as task_service
from .errors import ActionError, NotFound

ACTION_ADAPTER = TypeAdapter(Action)
ACTION_LIST_ADAPTER = TypeAdapter(list[Action])


def _snapshot(task: Task) -> dict:
    return {
        "title": task.title,
        "description": task.description,
        "status": task.status,
        "priority": task.priority,
        "due_at": task.due_at,
        "due_all_day": task.due_all_day,
        "scheduled_start_at": task.scheduled_start_at,
        "scheduled_end_at": task.scheduled_end_at,
        "estimate_minutes": task.estimate_minutes,
        "project_id": task.project_id,
        "archived_at": task.archived_at,
        "completed_at": task.completed_at,
        "version": task.version,
    }


def _parse_actions(proposal: AIProposal) -> list[Action]:
    try:
        raw = json.loads(proposal.actions_json or "[]")
        return ACTION_LIST_ADAPTER.validate_python(raw)
    except Exception as exc:
        raise ActionError(f"Stored actions are invalid: {exc}") from exc


def _validate_plan_day(session: Session, action: PlanDayAction, settings: Settings) -> dict:
    try:
        day = date.fromisoformat(action.date)
    except ValueError as exc:
        raise ActionError("plan_day date is invalid") from exc
    work_start = time.fromisoformat(settings.work_start)
    work_end = time.fromisoformat(settings.work_end)
    intervals: list[tuple[datetime, datetime, str]] = []
    items = []
    for item in action.items:
        task = task_service.get_task(session, item.task_id)
        start = datetime.combine(day, time.fromisoformat(item.start), tzinfo=tz(settings.timezone))
        end = datetime.combine(day, time.fromisoformat(item.end), tzinfo=tz(settings.timezone))
        if start >= end:
            raise ActionError("plan_day start must be before end")
        if start.time() < work_start or end.time() > work_end:
            raise ActionError("plan_day block is outside work hours")
        intervals.append((start, end, task.id))
        items.append(
            {"task_id": task.id, "title": task.title, "start": item.start, "end": item.end}
        )
    intervals.sort(key=lambda x: x[0])
    for left, right in zip(intervals, intervals[1:], strict=False):
        if left[1] > right[0]:
            raise ActionError("plan_day blocks overlap")
    return {"date": action.date, "items": items}


def validate_actions(
    session: Session, interpretation: Interpretation, settings: Settings
) -> tuple[list[dict], bool]:
    preview: list[dict] = []
    create_refs = {a.client_ref for a in interpretation.actions if isinstance(a, CreateTaskAction)}
    seen_refs: set[str] = set()
    destructive = False
    for action in interpretation.actions:
        if isinstance(action, CreateTaskAction):
            if action.client_ref in seen_refs:
                raise ActionError(f"Duplicate client_ref {action.client_ref}")
            seen_refs.add(action.client_ref)
            if action.project_id:
                project = session.get(Project, action.project_id)
                if not project or project.archived_at:
                    raise ActionError("create_task project does not exist")
            if action.parent_ref and action.parent_ref not in create_refs:
                task_service.get_task(session, action.parent_ref)
            preview.append(
                {
                    "op": "create",
                    "client_ref": action.client_ref,
                    "title": action.title,
                    "fields": action.model_dump(mode="json"),
                }
            )
        elif isinstance(action, UpdateTaskAction):
            task = task_service.get_task(session, action.task_id)
            changes = action.changes.model_dump(exclude_unset=True, mode="json")
            if not changes:
                raise ActionError("update_task has no changes")
            if changes.get("project_id"):
                project = session.get(Project, changes["project_id"])
                if not project or project.archived_at:
                    raise ActionError("update_task project does not exist")
            preview.append(
                {
                    "op": "update",
                    "task_id": task.id,
                    "title": task.title,
                    "changes": {
                        key: {"from": getattr(task, key, None), "to": value}
                        for key, value in changes.items()
                    },
                }
            )
        elif isinstance(action, CompleteTaskAction):
            task = task_service.get_task(session, action.task_id)
            preview.append({"op": "complete", "task_id": task.id, "title": task.title})
        elif isinstance(action, ArchiveTaskAction):
            task = task_service.get_task(session, action.task_id)
            destructive = True
            preview.append({"op": "archive", "task_id": task.id, "title": task.title})
        elif isinstance(action, QueryTasksAction):
            f = action.filter
            rows = task_service.list_tasks(
                session,
                view="all",
                project_id=f.project_id,
                q=f.search,
                include_done=True,
                tz_name=settings.timezone,
            )
            if f.status:
                rows = [t for t in rows if t.status in f.status]
            if f.due_before:
                rows = [t for t in rows if t.due_at and t.due_at < f.due_before]
            if f.due_after:
                rows = [t for t in rows if t.due_at and t.due_at > f.due_after]
            rows = rows[: action.limit]
            preview.append(
                {
                    "op": "query",
                    "count": len(rows),
                    "tasks": [
                        {
                            "id": t.id,
                            "short_key": human_short_key(t.id),
                            "title": t.title,
                            "status": t.status,
                        }
                        for t in rows[:20]
                    ],
                }
            )
        elif isinstance(action, PlanDayAction):
            preview.append({"op": "plan_day", **_validate_plan_day(session, action, settings)})
        elif isinstance(action, CreateSubtasksAction):
            parent = task_service.get_task(session, action.parent_id)
            preview.append(
                {
                    "op": "create_subtasks",
                    "parent_id": parent.id,
                    "parent_title": parent.title,
                    "count": len(action.subtasks),
                    "titles": [s.title for s in action.subtasks],
                }
            )
        elif isinstance(action, SummarizeAction):
            preview.append({"op": "summarize", "text": action.text})
    requires = bool(
        interpretation.requires_confirmation or destructive or len(interpretation.actions) > 3
    )
    return preview, requires


def create_proposal(
    session: Session,
    run: AIRun,
    interpretation: Interpretation,
    settings: Settings,
) -> AIProposal:
    preview, requires = validate_actions(session, interpretation, settings)
    proposal = AIProposal(
        ai_run_id=run.id,
        status="pending",
        intent=interpretation.intent,
        assistant_message=interpretation.assistant_message,
        actions_json=json.dumps(interpretation.action_dicts(), ensure_ascii=False),
        preview_json=json.dumps(preview, ensure_ascii=False),
        requires_confirmation=1 if requires else 0,
        confidence=interpretation.confidence,
        expires_at=iso(now_utc() + timedelta(hours=24)),
    )
    session.add(proposal)
    session.flush()
    return proposal


def _plan_time(day: str, hhmm: str, tz_name: str) -> str:
    d = date.fromisoformat(day)
    t = time.fromisoformat(hhmm)
    return iso(datetime.combine(d, t, tzinfo=tz(tz_name)))


def apply_proposal(
    session: Session, proposal_id: str, settings: Settings
) -> tuple[AIProposal, list[Task]]:
    proposal = session.get(AIProposal, proposal_id)
    if not proposal:
        raise NotFound("Proposal not found")
    if proposal.status != "pending":
        raise ActionError("Proposal is no longer pending")
    if proposal.expires_at and proposal.expires_at < now_iso():
        proposal.status = "expired"
        proposal.resolved_at = now_iso()
        session.flush()
        raise ActionError("Proposal has expired")
    actions = _parse_actions(proposal)
    run = session.get(AIRun, proposal.ai_run_id)
    created: list[str] = []
    snapshots: dict[str, dict] = {}
    refs: dict[str, str] = {}
    affected: list[Task] = []

    def snap(task: Task) -> None:
        snapshots.setdefault(task.id, _snapshot(task))

    for action in actions:
        if isinstance(action, CreateTaskAction):
            parent_id = refs.get(action.parent_ref) if action.parent_ref else None
            task = task_service.create_task(
                session,
                {
                    "title": action.title,
                    "description": action.description,
                    "status": action.status,
                    "priority": action.priority,
                    "due_at": action.due_at,
                    "due_all_day": action.due_all_day,
                    "project_id": action.project_id,
                    "parent_id": parent_id,
                    "estimate_minutes": action.estimate_minutes,
                    "tags": action.tags,
                },
                actor="ai",
                source="ai",
                ai_run_id=run.id if run else None,
                tz_name=settings.timezone,
            )
            refs[action.client_ref] = task.id
            created.append(task.id)
            affected.append(task)
        elif isinstance(action, UpdateTaskAction):
            task = task_service.get_task(session, action.task_id)
            snap(task)
            changes = action.changes.model_dump(exclude_unset=True)
            updated = task_service.update_task(
                session, task.id, changes, actor="ai", tz_name=settings.timezone
            )
            affected.append(updated)
        elif isinstance(action, CompleteTaskAction):
            task = task_service.get_task(session, action.task_id)
            snap(task)
            affected.append(task_service.complete_task(session, task.id, actor="ai"))
        elif isinstance(action, ArchiveTaskAction):
            task = task_service.get_task(session, action.task_id)
            snap(task)
            affected.extend(
                task_service.archive_task(session, task.id, actor="ai", reason=action.reason)
            )
        elif isinstance(action, CreateSubtasksAction):
            parent = task_service.get_task(session, action.parent_id)
            snap(parent)
            for spec in action.subtasks:
                child = task_service.create_task(
                    session,
                    {
                        "title": spec.title,
                        "description": spec.description,
                        "status": "todo",
                        "priority": parent.priority,
                        "project_id": parent.project_id,
                        "parent_id": parent.id,
                        "estimate_minutes": spec.estimate_minutes,
                    },
                    actor="ai",
                    source="ai",
                    ai_run_id=run.id if run else None,
                    tz_name=settings.timezone,
                )
                created.append(child.id)
                affected.append(child)
        elif isinstance(action, PlanDayAction):
            for item in action.items:
                task = task_service.get_task(session, item.task_id)
                snap(task)
                task.scheduled_start_at = _plan_time(action.date, item.start, settings.timezone)
                task.scheduled_end_at = _plan_time(action.date, item.end, settings.timezone)
                task.updated_at = now_iso()
                task.version += 1
                task_service.add_event(
                    session,
                    task.id,
                    "updated",
                    actor="ai",
                    field="scheduled_start_at",
                    new_value=task.scheduled_start_at,
                )
                affected.append(task)
        elif isinstance(action, (QueryTasksAction, SummarizeAction)):
            continue

    proposal.status = "applied"
    proposal.resolved_at = now_iso()
    proposal.undo_json = json.dumps(
        {"created": created, "snapshots": snapshots}, ensure_ascii=False
    )
    for task in {t.id: t for t in affected}.values():
        task_service.add_event(
            session,
            task.id,
            "proposal_applied",
            actor="ai",
            metadata={"proposal_id": proposal.id, "intent": proposal.intent},
        )
    session.flush()
    return proposal, affected


def undo_proposal(session: Session, proposal_id: str) -> tuple[AIProposal, list[Task]]:
    proposal = session.get(AIProposal, proposal_id)
    if not proposal:
        raise NotFound("Proposal not found")
    if proposal.status != "applied":
        raise ActionError("Only applied proposals can be undone")
    try:
        data = json.loads(proposal.undo_json or "{}")
    except json.JSONDecodeError:
        data = {}
    restored: list[Task] = []
    for task_id, snap in (data.get("snapshots") or {}).items():
        task = session.get(Task, task_id)
        if not task:
            continue
        for field, value in snap.items():
            if field != "version" and hasattr(task, field):
                setattr(task, field, value)
        task.version += 1
        task.updated_at = now_iso()
        task_service.add_event(
            session, task.id, "proposal_undone", actor="ai", metadata={"proposal_id": proposal.id}
        )
        restored.append(task)
    for task_id in data.get("created") or []:
        task = session.get(Task, task_id)
        if task and not task.archived_at:
            task.archived_at = now_iso()
            task.version += 1
            restored.append(task)
    proposal.status = "undone"
    proposal.resolved_at = now_iso()
    session.flush()
    return proposal, restored


def proposal_payload(session: Session, proposal: AIProposal) -> dict:
    run = session.get(AIRun, proposal.ai_run_id)
    provider = None
    if run:
        try:
            provider = json.loads(run.context_json or "{}").get("provider")
        except json.JSONDecodeError:
            provider = None
    return {
        "id": proposal.id,
        "run_id": proposal.ai_run_id,
        "status": proposal.status,
        "intent": proposal.intent,
        "assistant_message": proposal.assistant_message,
        "actions": json.loads(proposal.actions_json or "[]"),
        "preview": json.loads(proposal.preview_json or "[]"),
        "requires_confirmation": bool(proposal.requires_confirmation),
        "confidence": proposal.confidence,
        "created_at": proposal.created_at,
        "expires_at": proposal.expires_at,
        "resolved_at": proposal.resolved_at,
        "model": run.model if run else None,
        "provider": provider,
    }
