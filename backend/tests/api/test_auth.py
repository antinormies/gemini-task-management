from __future__ import annotations


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
