from __future__ import annotations


def test_pages_and_health(client):
    r = client.get("/api/health")
    assert r.status_code == 200
    assert r.json()["app"] == "gtm"
    for path in ["/", "/today", "/inbox", "/settings"]:
        r = client.get(path)
        assert r.status_code == 200, path
        assert "GTM" in r.text
