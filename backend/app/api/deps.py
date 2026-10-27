from __future__ import annotations

from collections.abc import Iterator

from fastapi import Depends, Header, HTTPException, Request
from sqlalchemy.orm import Session

from ..config import Settings
from ..models import Device
from ..security import origin_allowed
from ..services import auth as auth_service


def get_settings(request: Request) -> Settings:
    return request.app.state.settings


def get_db(request: Request) -> Iterator[Session]:
    session = request.app.state.db.sessionmaker()
    try:
        yield session
        session.commit()
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()


def get_ai_service(request: Request):
    return request.app.state.ai_service


def get_optional_device(
    request: Request,
    authorization: str | None = Header(default=None),
    session: Session = Depends(get_db),
) -> Device | None:
    if not authorization:
        return None
    scheme, _, token = authorization.partition(" ")
    if scheme.lower() != "bearer" or not token:
        raise HTTPException(status_code=401, detail="Invalid authorization header")
    try:
        return auth_service.authenticate(session, request.app.state.settings, token)
    except Exception as exc:
        raise HTTPException(status_code=401, detail=str(exc)) from exc


def get_device(device: Device | None = Depends(get_optional_device)) -> Device:
    if not device:
        raise HTTPException(status_code=401, detail="Device token required")
    return device


def verify_web_origin(
    request: Request,
    origin: str | None = Header(default=None),
    x_gtm_request: str | None = Header(default=None),
    hx_request: str | None = Header(default=None),
) -> None:
    settings: Settings = request.app.state.settings
    if origin and not origin_allowed(origin, settings.public_origin):
        raise HTTPException(status_code=403, detail="Origin not allowed")
    if request.method in ("POST", "PATCH", "PUT", "DELETE") and not (x_gtm_request or hx_request):
        # Local web UI sets X-GTM-Request. Native clients use bearer auth instead.
        authorization = request.headers.get("authorization", "")
        if not authorization.lower().startswith("bearer "):
            raise HTTPException(status_code=403, detail="Missing X-GTM-Request header")
