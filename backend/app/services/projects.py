from __future__ import annotations

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from ..models import Project, Task
from ..tenancy import account_id_for
from ..timeutils import now_iso
from .errors import NotFound, ValidationError


def project_payload(session: Session, project: Project) -> dict:
    active = (
        session.scalar(
            select(func.count(Task.id)).where(
                Task.project_id == project.id,
                Task.account_id == project.account_id,
                Task.archived_at.is_(None),
                Task.status.notin_(("done", "cancelled")),
            )
        )
        or 0
    )
    total = (
        session.scalar(
            select(func.count(Task.id)).where(
                Task.project_id == project.id,
                Task.account_id == project.account_id,
                Task.archived_at.is_(None),
            )
        )
        or 0
    )
    return {
        "id": project.id,
        "name": project.name,
        "color": project.color,
        "description": project.description,
        "archived_at": project.archived_at,
        "created_at": project.created_at,
        "updated_at": project.updated_at,
        "active_count": active,
        "task_count": total,
    }


def list_projects(session: Session, *, include_archived: bool = False) -> list[Project]:
    query = select(Project).order_by(Project.name)
    if not include_archived:
        query = query.where(Project.archived_at.is_(None))
    return list(session.scalars(query).all())


def get_project(session: Session, project_id: str) -> Project:
    project = session.get(Project, project_id)
    account_id = account_id_for(session)
    if not project or (account_id and project.account_id != account_id):
        raise NotFound("Project not found")
    return project


def create_project(session: Session, data: dict) -> Project:
    name = str(data.get("name", "")).strip()
    if not name:
        raise ValidationError("Project name is required")
    existing = session.scalar(select(Project).where(func.lower(Project.name) == name.lower()))
    if existing:
        raise ValidationError("A project with that name already exists")
    project = Project(
        name=name[:200],
        color=str(data.get("color") or "#64748b")[:20],
        description=str(data.get("description") or "")[:1000],
    )
    session.add(project)
    session.flush()
    return project


def update_project(session: Session, project_id: str, data: dict) -> Project:
    project = get_project(session, project_id)
    if "name" in data:
        name = str(data["name"]).strip()
        if not name:
            raise ValidationError("Project name is required")
        conflict = session.scalar(
            select(Project).where(
                func.lower(Project.name) == name.lower(), Project.id != project.id
            )
        )
        if conflict:
            raise ValidationError("A project with that name already exists")
        project.name = name[:200]
    if "color" in data:
        project.color = str(data["color"] or "#64748b")[:20]
    if "description" in data:
        project.description = str(data["description"] or "")[:1000]
    if data.get("archived") is True:
        project.archived_at = now_iso()
    elif data.get("archived") is False:
        project.archived_at = None
    project.updated_at = now_iso()
    session.flush()
    return project
