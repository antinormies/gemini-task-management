from __future__ import annotations

from contextvars import ContextVar

from sqlalchemy.orm import Session

_current_account_id: ContextVar[str | None] = ContextVar("gtm_account_id", default=None)


def current_account_id() -> str | None:
    return _current_account_id.get()


def set_current_account(account_id: str | None) -> None:
    _current_account_id.set(account_id)


def account_id_for(session: Session) -> str | None:
    """The tenant for this session. Session state is thread-safe and request-scoped."""
    return session.info.get("account_id") or _current_account_id.get()


def set_session_account(session: Session, account_id: str | None) -> None:
    if account_id:
        session.info["account_id"] = account_id
    else:
        session.info.pop("account_id", None)
    _current_account_id.set(account_id)


def clear_session_account(session: Session) -> None:
    set_session_account(session, None)


def reset_current_account() -> None:
    _current_account_id.set(None)
