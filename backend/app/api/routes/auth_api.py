from __future__ import annotations

import hmac

from fastapi import APIRouter, Depends, HTTPException, Request
from sqlalchemy.orm import Session

from ...models import Device
from ...services import auth as auth_service
from ...services.errors import NotFound, ValidationError
from ..deps import get_db, get_device, get_settings, verify_web_origin
from ..models import AutoPairRequest, PairRequest

router = APIRouter(prefix="/api/auth", tags=["auth"])


def _direct_loopback(request: Request) -> bool:
    host = request.client.host if request.client else ""
    if host not in ("127.0.0.1", "::1"):
        return False
    proxy_headers = ("x-forwarded-for", "tailscale-user-login", "cf-connecting-ip", "x-real-ip")
    return not any(request.headers.get(header) for header in proxy_headers)


def _auto_pair_allowed(request: Request, settings) -> bool:
    if _direct_loopback(request):
        return True
    configured = settings.auto_pair_key.strip()
    provided = request.headers.get("x-auto-pair-key", "")
    return bool(configured) and hmac.compare_digest(provided, configured)


@router.post("/auto-pair")
def auto_pair(
    body: AutoPairRequest,
    request: Request,
    session: Session = Depends(get_db),
    settings=Depends(get_settings),
):
    """Provision a device token for the single-user app.

    Loopback requests (adb reverse) work without a key. A production app can
    present X-Auto-Pair-Key matching GTM_AUTO_PAIR_KEY to provision through the
    public host; without either, remote clients cannot self-provision.
    """
    if not _auto_pair_allowed(request, settings):
        raise HTTPException(
            status_code=403,
            detail="Automatic provisioning requires loopback or a valid X-Auto-Pair-Key",
        )
    try:
        token, device = auth_service.auto_pair_device(session, settings, body.device_name)
        return {"token": token, "device": auth_service.device_payload(device)}
    except ValidationError as exc:
        raise HTTPException(status_code=503, detail=exc.message) from exc


@router.post("/pairing-code", dependencies=[Depends(verify_web_origin)])
def create_pairing_code(session: Session = Depends(get_db), settings=Depends(get_settings)):
    try:
        code, expires = auth_service.create_pairing_code(session, settings)
        return {"code": code, "expires_at": expires, "ttl_seconds": settings.pairing_ttl_seconds}
    except ValidationError as exc:
        raise HTTPException(status_code=422, detail=exc.message) from exc


@router.post("/pair")
def pair(body: PairRequest, session: Session = Depends(get_db), settings=Depends(get_settings)):
    try:
        token, device = auth_service.pair_device(session, settings, body.code, body.device_name)
        return {"token": token, "device": auth_service.device_payload(device)}
    except ValidationError as exc:
        raise HTTPException(status_code=401, detail=exc.message) from exc


@router.get("/me")
def me(device: Device = Depends(get_device)):
    return {"device": auth_service.device_payload(device)}


@router.post("/revoke")
def revoke(device: Device = Depends(get_device), session: Session = Depends(get_db)):
    auth_service.revoke_device(session, device.id)
    return {"ok": True}


@router.get("/devices", dependencies=[Depends(verify_web_origin)])
def devices(session: Session = Depends(get_db)):
    return {"items": [auth_service.device_payload(d) for d in auth_service.list_devices(session)]}


@router.post("/devices/{device_id}/revoke", dependencies=[Depends(verify_web_origin)])
def revoke_device(device_id: str, session: Session = Depends(get_db)):
    try:
        return {
            "device": auth_service.device_payload(auth_service.revoke_device(session, device_id))
        }
    except NotFound as exc:
        raise HTTPException(status_code=404, detail=exc.message) from exc
