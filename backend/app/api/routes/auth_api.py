from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from ...models import Device
from ...services import auth as auth_service
from ...services.errors import NotFound, ValidationError
from ..deps import get_db, get_device, get_settings, verify_web_origin
from ..models import PairRequest

router = APIRouter(prefix="/api/auth", tags=["auth"])


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
