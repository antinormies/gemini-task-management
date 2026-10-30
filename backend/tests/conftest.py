from __future__ import annotations

from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.ai.clients import AIResult
from app.ai.schemas import Interpretation
from app.config import Settings
from app.main import create_app
from app.models import Account
from app.security import SESSION_COOKIE, make_session

HEADERS = {"X-GTM-Request": "1", "Origin": "http://127.0.0.1:8765"}


@pytest.fixture()
def settings(tmp_path: Path) -> Settings:
    return Settings(
        _env_file=None,
        data_dir=str(tmp_path / "data"),
        db_path=str(tmp_path / "data" / "gtm.db"),
        secret_key="test-secret-key-not-for-production",
        public_origin="http://127.0.0.1:8765",
        ai_enabled=True,
        ai_base_url="http://127.0.0.1:8080/internal/ai/complete",
        ai_api_key="test-self-key",
        ai_model="deepseek-v4.1-flash",
        google_client_id="test-google-client",
    )


@pytest.fixture()
def app(settings: Settings):
    application = create_app(settings)
    with TestClient(application) as client:
        with application.state.db.session() as session:
            account = Account(
                google_sub="test-google-sub",
                email="test@example.com",
                name="Test User",
            )
            session.add(account)
            session.flush()
            account_id = account.id
        client.cookies.set(
            SESSION_COOKIE,
            make_session(account_id, settings.secret_key),
        )
        yield application, client


@pytest.fixture()
def client(app):
    return app[1]


class FakeAI:
    def __init__(self, interpretation: Interpretation):
        self.interpretation = interpretation
        self.calls = 0

    async def interpret(
        self,
        prompt: str,
        *,
        provider: str | None = None,
        system: str | None = None,
        firebase_uid: str | None = None,
    ) -> AIResult:
        self.calls += 1
        return AIResult(
            interpretation=self.interpretation,
            provider="fake",
            model="fake-model",
            raw={"ok": True},
            duration_ms=12,
            usage={"input_tokens": 10, "output_tokens": 5, "total_tokens": 15},
        )
