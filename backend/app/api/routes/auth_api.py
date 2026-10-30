from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Response
from fastapi.responses import JSONResponse
from sqlalchemy.orm import Session

from ...models import Account, Device
from ...security import SESSION_COOKIE, make_session
from ...services import auth as auth_service
from ...services import google_auth
from ...services.errors import NotFound, ValidationError
from ..deps import (
    get_account,
    get_db,
    get_device,
    get_optional_device,
    get_settings,
    verify_web_origin,
)
from ..models import GoogleLoginRequest

router = APIRouter(prefix="/api/auth", tags=["auth"])


@router.post("/google")
def google_login(
    body: GoogleLoginRequest,
    session: Session = Depends(get_db),
    settings=Depends(get_settings),
):
    raw_token = body.token()
    if not raw_token:
        raise HTTPException(status_code=422, detail="Google credential is required")
    try:
        info = google_auth.verify_google_id_token(raw_token, settings)
        account = auth_service.login_with_google(session, settings, info)
        token = None
        if body.device_name:
            token, _device = auth_service.create_device(
                session, settings, account, body.device_name
            )
    except ValidationError as exc:
        status = 503 if "not configured" in exc.message else 401
        raise HTTPException(status_code=status, detail=exc.message) from exc

    response = JSONResponse({"account": auth_service.account_payload(account), "token": token})
    if not body.device_name:
        response.set_cookie(
            SESSION_COOKIE,
            make_session(account.id, settings.secret_key),
            max_age=settings.session_max_age_seconds,
            httponly=True,
            samesite="lax",
            secure=settings.public_origin.startswith("https"),
            path="/",
        )
    return response


@router.get("/me")
def me(account: Account = Depends(get_account)):
    return {"account": auth_service.account_payload(account)}


@router.post("/logout")
def logout(
    response: Response,
    device: Device | None = Depends(get_optional_device),
    session: Session = Depends(get_db),
):
    if device:
        auth_service.revoke_device(session, device.id)
    response.delete_cookie(SESSION_COOKIE, path="/")
    return {"ok": True}


@router.post("/revoke")
def revoke(
    device: Device = Depends(get_device),
    session: Session = Depends(get_db),
):
    auth_service.revoke_device(session, device.id)
    return {"ok": True}


@router.get("/devices", dependencies=[Depends(verify_web_origin)])
def devices(
    account: Account = Depends(get_account),
    session: Session = Depends(get_db),
):
    return {"items": [auth_service.device_payload(d) for d in auth_service.list_devices(session)]}


@router.post("/devices/{device_id}/revoke", dependencies=[Depends(verify_web_origin)])
def revoke_device(
    device_id: str,
    account: Account = Depends(get_account),
    session: Session = Depends(get_db),
):
    try:
        return {
            "device": auth_service.device_payload(auth_service.revoke_device(session, device_id))
        }
    except NotFound as exc:
        raise HTTPException(status_code=404, detail=exc.message) from exc
