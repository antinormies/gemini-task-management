from __future__ import annotations

from app.ai.schemas import Interpretation
from tests.conftest import FakeAI


def test_voice_requires_transcript(client):
    r = client.post("/api/voice/interpret", json={"transcript": "", "duration_ms": 100})
    assert r.status_code == 422


def test_voice_proposal_and_apply(app, client):
    application, _ = app
    interpretation = Interpretation.model_validate(
        {
            "intent": "create_task",
            "assistant_message": "Added the dentist task.",
            "requires_confirmation": False,
            "confidence": 0.95,
            "actions": [
                {
                    "type": "create_task",
                    "client_ref": "t1",
                    "title": "Call the dentist",
                    "status": "backlog",
                    "priority": "high",
                }
            ],
        }
    )
    application.state.ai_service = FakeAI(interpretation)
    r = client.post(
        "/api/voice/interpret",
        json={"transcript": "Call the dentist tomorrow", "duration_ms": 1200},
        headers={"X-GTM-Request": "1"},
    )
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["proposal"]["intent"] == "create_task"
    assert body["proposal"]["preview"]
    pid = body["proposal"]["id"]
    r = client.post(f"/api/ai/proposals/{pid}/apply", headers={"X-GTM-Request": "1"})
    assert r.status_code == 200, r.text
    assert r.json()["task_ids"]
    r = client.get("/api/tasks?view=anytime")
    assert any(t["title"] == "Call the dentist" for t in r.json()["items"])


def test_voice_reject(app, client):
    application, _ = app
    interpretation = Interpretation.model_validate(
        {
            "intent": "archive_task",
            "assistant_message": "Archive old draft?",
            "requires_confirmation": True,
            "confidence": 0.9,
            "actions": [
                {
                    "type": "archive_task",
                    "task_id": "nonexistent",
                }
            ],
        }
    )
    application.state.ai_service = FakeAI(interpretation)
    r = client.post(
        "/api/voice/interpret",
        json={"transcript": "Archive old draft", "duration_ms": 1000},
        headers={"X-GTM-Request": "1"},
    )
    assert r.status_code != 200
