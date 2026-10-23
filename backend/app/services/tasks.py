from __future__ import annotations

import json
from datetime import date, datetime, time, timedelta
from typing import Any

from sqlalchemy import func, or_, select
from sqlalchemy.orm import Session

from ..models import Project, Tag, Task, TaskEvent, TaskTag
from ..timeutils import (
    human_short_key,
    iso,
    local_day_bounds,
    local_now,
    now_iso,
    tz,
)
from .errors import ConflictError, NotFound, ValidationError

ACTIVE = ("inbox", "todo", "in_progress", "waiting")
ALL_STATUSES = ("inbox", "todo", "in_progress", "waiting", "done", "cancelled")
PRIORITIES = ("none", "low", "medium", "high", "urgent")
TRANSITIONS = {
    "inbox": {"todo", "in_progress", "waiting", "done", "cancelled"},
    "todo": {"in_progress", "waiting", "done", "cancelled"},
    "in_progress": {"waiting", "todo", "done", "cancelled"},
    "waiting": {"todo", "in_progress", "done", "cancelled"},
    "done": {"todo"},
    "cancelled": {"inbox", "todo"},
}
PRIORITY_RANK = {"urgent": 0, "high": 1, "medium": 2, "low": 3, "none": 4}


def get_task(session: Session, task_id: str, include_archived: bool = False) -> Task:
    task = session.get(Task, task_id)
    if not task or (task.archived_at and not include_archived):
        raise NotFound("Task not found")
    return task


def _tags_for(session: Session, task_id: str) -> list[dict]:
    rows = session.execute(
        select(Tag).join(TaskTag, TaskTag.tag_id == Tag.id).where(TaskTag.task_id == task_id)
    ).scalars()
    return [{"id": t.id, "name": t.name, "color": t.color} for t in rows]


def task_payload(session: Session, task: Task, *, detail: bool = False) -> dict:
    project = session.get(Project, task.project_id) if task.project_id else None
    data: dict[str, Any] = {
        "id": task.id,
        "short_key": human_short_key(task.id),
        "title": task.title,
        "description": task.description,
        "status": task.status,
        "priority": task.priority,
        "due_at": task.due_at,
        "due_all_day": bool(task.due_all_day),
        "scheduled_start_at": task.scheduled_start_at,
        "scheduled_end_at": task.scheduled_end_at,
        "estimate_minutes": task.estimate_minutes,
        "project_id": task.project_id,
        "project_name": project.name if project else None,
        "parent_id": task.parent_id,
        "sort_order": task.sort_order,
        "source": task.source,
        "version": task.version,
        "created_at": task.created_at,
        "updated_at": task.updated_at,
        "completed_at": task.completed_at,
        "archived_at": task.archived_at,
        "tags": _tags_for(session, task.id),
    }
    if detail:
        children = session.scalars(
            select(Task)
            .where(Task.parent_id == task.id, Task.archived_at.is_(None))
            .order_by(Task.sort_order, Task.created_at)
        ).all()
        data["subtasks"] = [task_payload(session, child) for child in children]
        data["events"] = [
            {
                "id": ev.id,
                "event_type": ev.event_type,
                "actor": ev.actor,
                "field": ev.field,
                "old_value": ev.old_value,
                "new_value": ev.new_value,
                "created_at": ev.created_at,
            }
            for ev in session.scalars(
                select(TaskEvent)
                .where(TaskEvent.task_id == task.id)
                .order_by(TaskEvent.id.desc())
                .limit(50)
            )
        ]
    return data


def add_event(
    session: Session,
    task_id: str,
    event_type: str,
    *,
    actor: str = "user",
    field: str | None = None,
    old_value: Any = None,
    new_value: Any = None,
    metadata: dict | None = None,
) -> None:
    def enc(value: Any) -> str | None:
        if value is None:
            return None
        return value if isinstance(value, str) else json.dumps(value, ensure_ascii=False)

    session.add(
        TaskEvent(
            task_id=task_id,
            event_type=event_type,
            actor=actor,
            field=field,
            old_value=enc(old_value),
            new_value=enc(new_value),
            metadata_json=json.dumps(metadata or {}, ensure_ascii=False),
        )
    )


def _validate_project(session: Session, project_id: str | None) -> None:
    if project_id is None:
        return
    project = session.get(Project, project_id)
    if not project or project.archived_at:
        raise ValidationError("Project does not exist or is archived")


def _depth(session: Session, task: Task) -> int:
    depth = 1
    current = task
    while current.parent_id:
        parent = session.get(Task, current.parent_id)
        if not parent:
            break
        depth += 1
        current = parent
        if depth > 10:
            break
    return depth


def _normalize_due(value: str | None, all_day: bool, tz_name: str) -> str | None:
    if not value:
        return None
    if all_day:
        zone = tz(tz_name)
        text = value.strip()
        try:
            if "T" in text:
                parsed = datetime.fromisoformat(text.replace("Z", "+00:00"))
                day = parsed.astimezone(zone).date()
            else:
                day = date.fromisoformat(text)
        except ValueError as exc:
            raise ValidationError(f"Invalid due date: {value}") from exc
        return (
            datetime.combine(day, time.min, tzinfo=zone)
            .astimezone(__import__("datetime").timezone.utc)
            .isoformat(timespec="milliseconds")
            .replace("+00:00", "Z")
        )
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as exc:
        raise ValidationError(f"Invalid due datetime: {value}") from exc
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=tz(tz_name))
    return (
        parsed.astimezone(__import__("datetime").timezone.utc)
        .isoformat(timespec="milliseconds")
        .replace("+00:00", "Z")
    )


def _upsert_tags(session: Session, task: Task, names: list[str]) -> None:
    session.execute(TaskTag.__table__.delete().where(TaskTag.task_id == task.id))
    for raw in names[:10]:
        name = str(raw).strip()
        if not name:
            continue
        tag = session.scalar(select(Tag).where(func.lower(Tag.name) == name.lower()))
        if not tag:
            tag = Tag(name=name[:50])
            session.add(tag)
            session.flush()
        session.add(TaskTag(task_id=task.id, tag_id=tag.id))


def create_task(
    session: Session,
    data: dict,
    *,
    actor: str = "user",
    source: str = "manual",
    ai_run_id: str | None = None,
    tz_name: str = "Asia/Jakarta",
) -> Task:
    title = str(data.get("title", "")).strip()
    if not title:
        raise ValidationError("Title is required")
    if len(title) > 300:
        raise ValidationError("Title is too long")
    status = data.get("status") or "inbox"
    if status not in ALL_STATUSES:
        raise ValidationError("Invalid status")
    priority = data.get("priority") or "none"
    if priority not in PRIORITIES:
        raise ValidationError("Invalid priority")
    project_id = data.get("project_id") or None
    _validate_project(session, project_id)
    parent_id = data.get("parent_id") or None
    if parent_id:
        parent = get_task(session, parent_id)
        if _depth(session, parent) >= 3:
            raise ValidationError("Maximum subtask depth is 3")
        if not project_id:
            project_id = parent.project_id
    due_all_day = bool(data.get("due_all_day"))
    due_at = _normalize_due(data.get("due_at"), due_all_day, tz_name)
    estimate = data.get("estimate_minutes")
    if estimate is not None:
        estimate = int(estimate)
        if estimate < 0 or estimate > 1440:
            raise ValidationError("Estimate must be 0-1440 minutes")
    max_order = (
        session.scalar(select(func.max(Task.sort_order)).where(Task.status == status)) or 0.0
    )
    task = Task(
        title=title,
        description=str(data.get("description") or "")[:5000],
        status=status,
        priority=priority,
        due_at=due_at,
        due_all_day=1 if due_all_day else 0,
        estimate_minutes=estimate,
        project_id=project_id,
        parent_id=parent_id,
        sort_order=float(max_order) + 1.0,
        source=source,
        ai_run_id=ai_run_id,
    )
    session.add(task)
    session.flush()
    add_event(session, task.id, "created", actor=actor, new_value={"title": title})
    _upsert_tags(session, task, list(data.get("tags") or []))
    session.flush()
    return task


def update_task(
    session: Session,
    task_id: str,
    changes: dict,
    *,
    expected_version: int | None = None,
    actor: str = "user",
    tz_name: str = "Asia/Jakarta",
) -> Task:
    task = get_task(session, task_id)
    if expected_version is not None and expected_version != task.version:
        raise ConflictError("This task changed; review again.")
    allowed = {
        "title",
        "description",
        "status",
        "priority",
        "due_at",
        "due_all_day",
        "project_id",
        "estimate_minutes",
        "sort_order",
    }
    changed: list[tuple[str, Any, Any]] = []
    old_status = task.status
    for field, value in changes.items():
        if field not in allowed:
            continue
        if field == "title":
            value = str(value).strip()
            if not value:
                raise ValidationError("Title cannot be empty")
            value = value[:300]
        if field == "description":
            value = str(value)[:5000]
        if field == "status":
            if value not in ALL_STATUSES:
                raise ValidationError("Invalid status")
            if value != task.status and value not in TRANSITIONS.get(task.status, set()):
                raise ValidationError(f"Cannot move from {task.status} to {value}")
        if field == "priority" and value not in PRIORITIES:
            raise ValidationError("Invalid priority")
        if field == "project_id" and value:
            _validate_project(session, value)
        if field == "due_at" or field == "due_all_day":
            continue
        old = getattr(task, field)
        if old != value:
            setattr(task, field, value)
            changed.append((field, old, value))
    if "due_at" in changes or "due_all_day" in changes:
        all_day = bool(changes.get("due_all_day", task.due_all_day))
        due_value = changes.get("due_at", task.due_at)
        normalized = _normalize_due(due_value, all_day, tz_name)
        if normalized != task.due_at or int(all_day) != task.due_all_day:
            changed.append(("due_at", task.due_at, normalized))
            task.due_at = normalized
            task.due_all_day = 1 if all_day else 0
    if task.status != old_status:
        task.completed_at = now_iso() if task.status == "done" else None
    if "estimate_minutes" in changes:
        estimate = changes["estimate_minutes"]
        if estimate is not None:
            estimate = int(estimate)
            if estimate < 0 or estimate > 1440:
                raise ValidationError("Estimate must be 0-1440 minutes")
        if estimate != task.estimate_minutes:
            changed.append(("estimate_minutes", task.estimate_minutes, estimate))
            task.estimate_minutes = estimate
    if not changed:
        return task
    task.version += 1
    task.updated_at = now_iso()
    for field, old, new in changed:
        add_event(
            session, task.id, "updated", actor=actor, field=field, old_value=old, new_value=new
        )
    session.flush()
    return task


def set_tags(session: Session, task_id: str, names: list[str]) -> None:
    task = get_task(session, task_id)
    _upsert_tags(session, task, names)
    task.version += 1
    task.updated_at = now_iso()
    session.flush()


def complete_task(
    session: Session, task_id: str, *, actor: str = "user", expected_version: int | None = None
) -> Task:
    task = get_task(session, task_id)
    if expected_version is not None and expected_version != task.version:
        raise ConflictError("This task changed; review again.")
    if task.status == "done":
        return task
    old = task.status
    task.status = "done"
    task.completed_at = now_iso()
    task.updated_at = now_iso()
    task.version += 1
    add_event(
        session, task.id, "completed", actor=actor, field="status", old_value=old, new_value="done"
    )
    session.flush()
    return task


def reopen_task(session: Session, task_id: str, *, actor: str = "user") -> Task:
    task = get_task(session, task_id)
    if task.status not in ("done", "cancelled"):
        raise ValidationError("Only done or cancelled tasks can be reopened")
    old = task.status
    task.status = "todo"
    task.completed_at = None
    task.archived_at = None
    task.updated_at = now_iso()
    task.version += 1
    add_event(
        session, task.id, "reopened", actor=actor, field="status", old_value=old, new_value="todo"
    )
    session.flush()
    return task


def archive_task(
    session: Session, task_id: str, *, actor: str = "user", reason: str | None = None
) -> list[Task]:
    task = get_task(session, task_id)
    ids = [task.id]
    children = session.scalars(
        select(Task).where(Task.parent_id == task.id, Task.archived_at.is_(None))
    ).all()
    for child in children:
        ids.extend([t.id for t in archive_task(session, child.id, actor=actor, reason=reason)])
    task.archived_at = now_iso()
    task.updated_at = now_iso()
    task.version += 1
    add_event(
        session, task.id, "archived", actor=actor, metadata={"reason": reason} if reason else None
    )
    session.flush()
    return session.scalars(select(Task).where(Task.id.in_(ids))).all()


def reorder_task(session: Session, task_id: str, sort_order: float, *, actor: str = "user") -> Task:
    task = get_task(session, task_id)
    old = task.sort_order
    task.sort_order = float(sort_order)
    task.updated_at = now_iso()
    task.version += 1
    add_event(
        session,
        task.id,
        "reordered",
        actor=actor,
        field="sort_order",
        old_value=old,
        new_value=task.sort_order,
    )
    session.flush()
    return task


def list_tasks(
    session: Session,
    *,
    view: str = "all",
    status: str | None = None,
    project_id: str | None = None,
    q: str | None = None,
    include_done: bool = False,
    tz_name: str = "Asia/Jakarta",
) -> list[Task]:
    now = local_now(tz_name)
    day_start, day_end = local_day_bounds(now.date(), tz_name)
    query = select(Task).where(Task.archived_at.is_(None))
    if view == "today":
        query = query.where(
            Task.status.notin_(("done", "cancelled")),
            or_(Task.due_at < iso(day_end), Task.scheduled_start_at < iso(day_end)),
        )
    elif view == "inbox":
        query = query.where(Task.status == "inbox")
    elif view == "upcoming":
        end = day_start + timedelta(days=7)
        query = query.where(
            Task.status.notin_(("done", "cancelled")),
            Task.due_at >= iso(day_start),
            Task.due_at < iso(end),
        )
    elif view == "anytime":
        query = query.where(Task.status.in_(("todo", "in_progress")), Task.due_at.is_(None))
    elif view == "waiting":
        query = query.where(Task.status == "waiting")
    elif view == "done":
        cutoff = now - timedelta(days=30)
        query = query.where(Task.status == "done", Task.completed_at >= iso(cutoff))
    elif view == "board" or view == "all" or view == "projects":
        query = query.where(Task.status != "cancelled")
    if status:
        query = query.where(Task.status == status)
    if project_id:
        query = query.where(Task.project_id == project_id)
    if q:
        like = f"%{q.strip()}%"
        query = query.where(or_(Task.title.ilike(like), Task.description.ilike(like)))
    if not include_done and view not in ("done", "board", "all", "projects"):
        query = query.where(Task.status.notin_(("done", "cancelled")))
    rows = list(session.scalars(query).all())
    rows.sort(
        key=lambda t: (
            0 if t.status in ("inbox", "todo", "in_progress") else 1,
            PRIORITY_RANK.get(t.priority, 4),
            t.due_at or "9999",
            t.sort_order,
            t.created_at,
        )
    )
    return rows


def board_payload(session: Session, tasks: list[Task]) -> dict:
    columns = [
        ("todo", "To do"),
        ("in_progress", "In progress"),
        ("waiting", "Waiting"),
        ("done", "Done"),
    ]
    result = []
    for key, label in columns:
        if key == "todo":
            items = [t for t in tasks if t.status in ("inbox", "todo")]
        else:
            items = [t for t in tasks if t.status == key]
        result.append(
            {"key": key, "label": label, "tasks": [task_payload(session, t) for t in items]}
        )
    return {"columns": result}


def detail_payload(session: Session, task_id: str) -> dict:
    return task_payload(session, get_task(session, task_id), detail=True)
