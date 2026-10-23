from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from ...services import projects as project_service
from ...services.errors import NotFound, ValidationError
from ..deps import get_db, verify_web_origin
from ..models import ProjectCreate, ProjectUpdate

router = APIRouter(prefix="/api/projects", tags=["projects"])


def _payload(session, project):
    return project_service.project_payload(session, project)


@router.get("")
def list_projects(include_archived: bool = False, session: Session = Depends(get_db)):
    return {
        "items": [
            _payload(session, p)
            for p in project_service.list_projects(session, include_archived=include_archived)
        ]
    }


@router.post("", dependencies=[Depends(verify_web_origin)])
def create_project(body: ProjectCreate, session: Session = Depends(get_db)):
    try:
        project = project_service.create_project(session, body.model_dump())
        return {"project": _payload(session, project)}
    except ValidationError as exc:
        raise HTTPException(status_code=422, detail=exc.message) from exc


@router.patch("/{project_id}", dependencies=[Depends(verify_web_origin)])
def update_project(project_id: str, body: ProjectUpdate, session: Session = Depends(get_db)):
    try:
        project = project_service.update_project(
            session, project_id, body.model_dump(exclude_unset=True)
        )
        return {"project": _payload(session, project)}
    except NotFound as exc:
        raise HTTPException(status_code=404, detail=exc.message) from exc
    except ValidationError as exc:
        raise HTTPException(status_code=422, detail=exc.message) from exc
