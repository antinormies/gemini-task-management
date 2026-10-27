from __future__ import annotations

from fastapi.testclient import TestClient

from app.main import create_app


def test_pairing_flow(client):
    r = client.post("/api/auth/pairing-code", headers={"X-GTM-Request": "1"})
    assert r.status_code == 200, r.text
    code = r.json()["code"]
    assert len(code) == 6
    r = client.post("/api/auth/pair", json={"code": code, "device_name": "Test phone"})
    assert r.status_code == 200, r.text
    token = r.json()["token"]
    r = client.get("/api/auth/me", headers={"Authorization": "Bearer " + token})
    assert r.status_code == 200
    assert r.json()["device"]["name"] == "Test phone"
    r = client.get("/api/auth/devices", headers={"X-GTM-Request": "1"})
    assert r.status_code == 200
    assert r.json()["items"]
    r = client.post("/api/auth/revoke", headers={"Authorization": "Bearer " + token})
    assert r.status_code == 200
    r = client.get("/api/auth/me", headers={"Authorization": "Bearer " + token})
    assert r.status_code == 401


def test_pairing_code_is_single_use(client):
    r = client.post("/api/auth/pairing-code", headers={"X-GTM-Request": "1"})
    code = r.json()["code"]
    assert client.post("/api/auth/pair", json={"code": code}).status_code == 200
    assert client.post("/api/auth/pair", json={"code": code}).status_code == 401


def test_auto_pair_on_loopback(settings):
    application = create_app(settings)
    with TestClient(application, client=("127.0.0.1", 5555)) as local:
        r = local.post("/api/auth/auto-pair", json={"device_name": "Test emulator"})
        assert r.status_code == 200, r.text
        token = r.json()["token"]
        me = local.get("/api/auth/me", headers={"Authorization": "Bearer " + token})
        assert me.status_code == 200
        assert me.json()["device"]["name"] == "Test emulator"


def test_auto_pair_rejects_non_loopback(settings):
    application = create_app(settings)
    with TestClient(application) as remote:
        r = remote.post("/api/auth/auto-pair", json={"device_name": "Remote"})
        assert r.status_code == 403


def test_auto_pair_rejects_proxied_requests(settings):
    application = create_app(settings)
    with TestClient(application, client=("127.0.0.1", 5555)) as local:
        r = local.post(
            "/api/auth/auto-pair",
            json={"device_name": "Remote"},
            headers={"Tailscale-User-Login": "someone@example.com"},
        )
        assert r.status_code == 403
