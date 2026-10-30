import json

from app.ai.clients import _gateway_error


def test_firebase_uid_gateway_error_is_structured():
    error = _gateway_error(
        401,
        json.dumps(
            {
                "code": "UNAUTHENTICATED",
                "message": "missing firebase user id",
                "requestId": "req-1",
            }
        ),
    )
    assert error.code == "AI_AUTH_FIREBASE"
    assert "Firebase UID" in error.message
    assert "request req-1" in error.message
    assert "{" not in error.message


def test_gateway_rate_and_unavailable_are_mapped():
    assert _gateway_error(429, "{}").code == "AI_RATE_LIMIT"
    assert _gateway_error(503, "{}").code == "AI_UNAVAILABLE"
    error = _gateway_error(400, json.dumps({"message": "bad request"}))
    assert error.code == "AI_REQUEST"
    assert error.message.startswith("bad request")
