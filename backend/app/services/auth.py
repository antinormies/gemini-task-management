from __future__ import annotations

from datetime import timedelta

from sqlalchemy import select
from sqlalchemy.orm import Session

from ..config import Settings
from ..models import Account, Device
from ..security import hmac_hex, new_token, token_prefix
from ..tenancy import account_id_for, set_session_account
from ..timeutils import iso, now_iso, now_utc
from .errors import NotFound, ValidationError


def account_payload(account: Account) -> dict:
    return {
        "id": account.id,
        "email": account.email,
        "name": account.name,
        "picture": account.picture,
    }


def login_with_google(session: Session, settings: Settings, info: dict) -> Account:
    sub = str(info.get("sub") or "").strip()
    email = str(info.get("email") or "").strip()[:320]
    name = str(info.get("name") or "").strip()[:200]
    picture = str(info.get("picture") or "").strip()[:2000]
    if not sub:
        raise ValidationError("Google account subject is missing")
    account = session.scalar(select(Account).where(Account.google_sub == sub))
    if account is None:
        legacy = session.scalar(
            select(Account).where(Account.is_legacy == 1).order_by(Account.created_at).limit(1)
        )
        if legacy is not None and not legacy.google_sub:
            account = legacy
            account.is_legacy = 0
        else:
            account = Account(google_sub=sub, email=email, name=name, picture=picture)
            session.add(account)
            session.flush()
    account.google_sub = sub
    account.email = email
    account.name = name or email
    account.picture = picture
    account.last_login_at = now_iso()
    account.updated_at = now_iso()
    set_session_account(session, account.id)
    session.flush()
    return account


def create_device(
    session: Session, settings: Settings, account: Account, device_name: str
) -> tuple[str, Device]:
    if not settings.secret_key:
        raise ValidationError("GTM_SECRET_KEY is empty; device login is disabled")
    token = new_token()
    device = Device(
        account_id=account.id,
        name=(device_name.strip() or "Android phone")[:120],
        token_hash=hmac_hex(token, settings.secret_key),
        token_prefix=token_prefix(token),
    )
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
    set_session_account(session, device.account_id)
    return device


def list_devices(session: Session) -> list[Device]:
    return list(session.scalars(select(Device).order_by(Device.created_at.desc())).all())


def revoke_device(session: Session, device_id: str) -> Device:
    device = session.get(Device, device_id)
    if not device or device.account_id != account_id_for(session):
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
