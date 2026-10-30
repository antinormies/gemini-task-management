from __future__ import annotations

from google.auth.transport import requests as google_requests
from google.oauth2 import id_token

from ..config import Settings
from .errors import ValidationError


def verify_google_id_token(raw_token: str, settings: Settings) -> dict:
    audiences = [
        value for value in (settings.google_client_id, settings.google_android_client_id) if value
    ]
    if not audiences:
        raise ValidationError("Google sign-in is not configured")
    try:
        info = id_token.verify_oauth2_token(
            raw_token,
            google_requests.Request(),
            audience=audiences,
        )
    except Exception as exc:
        raise ValidationError("Google sign-in could not be verified") from exc
    if info.get("iss") not in ("accounts.google.com", "https://accounts.google.com"):
        raise ValidationError("Google sign-in issuer is invalid")
    if not info.get("email_verified"):
        raise ValidationError("Google email is not verified")
    if not str(info.get("sub") or "").strip():
        raise ValidationError("Google account subject is missing")
    return info
