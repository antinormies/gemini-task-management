from __future__ import annotations

import hashlib
import hmac
import secrets
import uuid


def new_id() -> str:
    uuid7 = getattr(uuid, "uuid7", None)
    if uuid7 is not None:
        return str(uuid7())
    # RFC 9562 UUIDv7 fallback for interpreters without uuid.uuid7.
    millis = int(__import__("time").time() * 1000) & ((1 << 48) - 1)
    rand_a = secrets.randbits(12)
    rand_b = secrets.randbits(62)
    value = (millis << 80) | (0x7 << 76) | (rand_a << 64) | (0b10 << 62) | rand_b
    return str(uuid.UUID(int=value))


def hmac_hex(value: str, secret: str) -> str:
    return hmac.new(secret.encode(), value.encode(), hashlib.sha256).hexdigest()


def new_token() -> str:
    return secrets.token_urlsafe(32)


def new_pairing_code() -> str:
    return f"{secrets.randbelow(1_000_000):06d}"


def token_prefix(token: str) -> str:
    return token[:8]


def safe_equals(a: str, b: str) -> bool:
    return hmac.compare_digest(a, b)


def origin_allowed(origin: str | None, configured: str, host: str | None = None) -> bool:
    if not origin:
        return True
    normalized = origin.rstrip("/")
    if normalized == configured.rstrip("/"):
        return True
    return normalized in {"http://127.0.0.1:8765", "http://localhost:8765"}
