from __future__ import annotations

from collections.abc import Iterator

from fastapi import Depends, Header, HTTPException, Request
from sqlalchemy.orm import Session

from ..config import Settings
from ..models import Account, Device
from ..security import SESSION_COOKIE, origin_allowed, read_session
from ..services import auth as auth_service
from ..tenancy import account_id_for, set_session_account


def get_settings(request: Request) -> Settings:
    return request.app.state.settings


def _scope_session(request: Request, session: Session) -> None:
    """Authenticate once per request session so every ORM query is tenant-scoped."""
    settings: Settings = request.app.state.settings
    authorization = request.headers.get("authorization", "")
    if authorization:
        scheme, _, token = authorization.partition(" ")
        if scheme.lower() == "bearer" and token:
            try:
                device = auth_service.authenticate(session, settings, token)
            except Exception:
                return
            session.info["device"] = device
            set_session_account(session, device.account_id)
            return
    account_id = read_session(
        request.cookies.get(SESSION_COOKIE),
        settings.secret_key,
        settings.session_max_age_seconds,
    )
    if not account_id:
        return
    account = session.get(Account, account_id)
    if account:
        set_session_account(session, account.id)


def get_db(request: Request) -> Iterator[Session]:
    session = request.app.state.db.sessionmaker()
    _scope_session(request, session)
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


async def get_optional_device(
    request: Request,
    authorization: str | None = Header(default=None),
    session: Session = Depends(get_db),
) -> Device | None:
    cached = session.info.get("device")
    if cached is not None:
        return cached
    if not authorization:
        return None
    scheme, _, token = authorization.partition(" ")
    if scheme.lower() != "bearer" or not token:
        raise HTTPException(status_code=401, detail="Invalid authorization header")
    try:
        device = auth_service.authenticate(session, request.app.state.settings, token)
        set_session_account(session, device.account_id)
        session.info["device"] = device
        return device
    except Exception as exc:
        raise HTTPException(status_code=401, detail=str(exc)) from exc


def get_device(device: Device | None = Depends(get_optional_device)) -> Device:
    if not device:
        raise HTTPException(status_code=401, detail="Device token required")
    return device


async def get_optional_account(
    request: Request,
    authorization: str | None = Header(default=None),
    session: Session = Depends(get_db),
) -> Account | None:
    settings: Settings = request.app.state.settings
    account_id = session.info.get("account_id") or account_id_for(session)
    if account_id:
        account = session.get(Account, account_id)
        if account:
            return account
    if authorization:
        scheme, _, token = authorization.partition(" ")
        if scheme.lower() != "bearer" or not token:
            raise HTTPException(status_code=401, detail="Invalid authorization header")
        try:
            device = auth_service.authenticate(session, settings, token)
        except Exception as exc:
            raise HTTPException(status_code=401, detail=str(exc)) from exc
        account = session.get(Account, device.account_id)
        if not account:
            raise HTTPException(status_code=401, detail="Account not found")
        session.info["device"] = device
        set_session_account(session, account.id)
        return account
    account_id = read_session(
        request.cookies.get(SESSION_COOKIE),
        settings.secret_key,
        settings.session_max_age_seconds,
    )
    if not account_id:
        return None
    account = session.get(Account, account_id)
    if not account:
        return None
    set_session_account(session, account.id)
    return account


def get_account(account: Account | None = Depends(get_optional_account)) -> Account:
    if not account:
        raise HTTPException(status_code=401, detail="Sign in required")
    return account


def get_web_account(account: Account | None = Depends(get_optional_account)) -> Account | None:
    return account


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
