from __future__ import annotations

from datetime import timedelta

from sqlalchemy import select
from sqlalchemy.orm import Session

from ..config import Settings
from ..models import Device, PairingCode
from ..security import (
    hmac_hex,
    new_pairing_code,
    new_token,
    token_prefix,
)
from ..timeutils import iso, now_iso, now_utc
from .errors import NotFound, ValidationError


def create_pairing_code(session: Session, settings: Settings) -> tuple[str, str]:
    if not settings.secret_key:
        raise ValidationError("GTM_SECRET_KEY is empty; pairing is disabled")
    session.query(PairingCode).delete()
    code = new_pairing_code()
    expires = iso(now_utc() + timedelta(seconds=settings.pairing_ttl_seconds))
    session.add(
        PairingCode(
            code_hash=hmac_hex(code, settings.secret_key),
            expires_at=expires,
        )
    )
    session.flush()
    return code, expires


def pair_device(
    session: Session, settings: Settings, code: str, device_name: str
) -> tuple[str, Device]:
    if not settings.secret_key:
        raise ValidationError("Pairing is not configured")
    code_hash = hmac_hex(code.strip(), settings.secret_key)
    row = session.get(PairingCode, code_hash)
    if not row or row.used_at:
        raise ValidationError("Pairing code is invalid or already used")
    if row.expires_at < now_iso():
        raise ValidationError("Pairing code has expired")
    if row.attempt_count >= settings.pairing_max_attempts:
        raise ValidationError("Too many pairing attempts; generate a new code")
    token = new_token()
    device = Device(
        name=(device_name.strip() or "Android phone")[:120],
        token_hash=hmac_hex(token, settings.secret_key),
        token_prefix=token_prefix(token),
    )
    row.used_at = now_iso()
    session.add(device)
    session.flush()
    return token, device


def authenticate(session: Session, settings: Settings, token: str) -> Device:
    if not token or not settings.secret_key:
        raise ValidationError("Missing device token")
    device = session.scalar(
        select(Device).where(Device.token_hash == hmac_hex(token, settings.secret_key))
    )
    if not device:
        raise NotFound("Unknown device token")
    if device.revoked_at:
        raise ValidationError("Device has been revoked")
    last = device.last_seen_at
    if not last or last < iso(now_utc() - timedelta(seconds=60)):
        device.last_seen_at = now_iso()
    return device


def list_devices(session: Session) -> list[Device]:
    return list(session.scalars(select(Device).order_by(Device.created_at.desc())).all())


def revoke_device(session: Session, device_id: str) -> Device:
    device = session.get(Device, device_id)
    if not device:
        raise NotFound("Device not found")
    device.revoked_at = now_iso()
    session.flush()
    return device


def revoke_all(session: Session) -> None:
    for device in session.scalars(select(Device).where(Device.revoked_at.is_(None))).all():
        device.revoked_at = now_iso()
    session.flush()


def device_payload(device: Device) -> dict:
    return {
        "id": device.id,
        "name": device.name,
        "token_prefix": device.token_prefix,
        "created_at": device.created_at,
        "last_seen_at": device.last_seen_at,
        "revoked_at": device.revoked_at,
    }
