from __future__ import annotations

from fastapi.testclient import TestClient

from app.main import create_app
from app.models import Account, Task
from app.security import SESSION_COOKIE, make_session
from app.tenancy import set_current_account

HEADERS = {"X-GTM-Request": "1", "Origin": "http://127.0.0.1:8765"}


def _fake_google(monkeypatch, *, sub="google-sub-1", email="google@example.com"):
    monkeypatch.setattr(
        "app.services.google_auth.verify_google_id_token",
        lambda token, settings: {
            "sub": sub,
            "email": email,
            "email_verified": True,
            "name": "Google User",
            "picture": "",
        },
    )


def test_google_login_mobile(settings, monkeypatch):
    _fake_google(monkeypatch)
    application = create_app(settings)
    with TestClient(application) as client:
        r = client.post(
            "/api/auth/google",
            json={"id_token": "fake-token", "device_name": "Test phone"},
        )
        assert r.status_code == 200, r.text
        token = r.json()["token"]
        assert token
        assert r.json()["account"]["email"] == "google@example.com"
        me = client.get("/api/auth/me", headers={"Authorization": "Bearer " + token})
        assert me.status_code == 200
        assert me.json()["account"]["email"] == "google@example.com"
        devices = client.get(
            "/api/auth/devices",
            headers={"Authorization": "Bearer " + token, **HEADERS},
        )
        assert devices.status_code == 200
        assert len(devices.json()["items"]) == 1
        logout = client.post("/api/auth/logout", headers={"Authorization": "Bearer " + token})
        assert logout.status_code == 200
        assert (
            client.get("/api/auth/me", headers={"Authorization": "Bearer " + token}).status_code
            == 401
        )


def test_google_login_web_sets_cookie(settings, monkeypatch):
    _fake_google(monkeypatch)
    application = create_app(settings)
    with TestClient(application) as client:
        login_page = client.get("/login")
        assert login_page.status_code == 200
        assert "Google sign-in" in login_page.text or "g_id_signin" in login_page.text
        r = client.post("/api/auth/google", json={"credential": "fake-token"})
        assert r.status_code == 200, r.text
        assert SESSION_COOKIE in client.cookies
        home = client.get("/")
        assert home.status_code == 200
        assert "Google User" in home.text
        me = client.get("/api/auth/me")
        assert me.status_code == 200
        logout = client.post("/logout", follow_redirects=False)
        assert logout.status_code == 303
        assert client.get("/api/auth/me").status_code == 401


def test_unauthenticated_pages_redirect_to_login(settings):
    application = create_app(settings)
    with TestClient(application) as client:
        r = client.get("/", follow_redirects=False)
        assert r.status_code == 303
        assert r.headers["location"] == "/login"


def test_accounts_are_isolated(settings):
    application = create_app(settings)
    with TestClient(application) as base:
        with application.state.db.session() as session:
            first = Account(google_sub="first", email="first@example.com", name="First")
            second = Account(google_sub="second", email="second@example.com", name="Second")
            session.add_all([first, second])
            session.flush()
            first_id, second_id = first.id, second.id
        base.cookies.set(SESSION_COOKIE, make_session(first_id, settings.secret_key))
        other = TestClient(application)
        other.cookies.set(SESSION_COOKIE, make_session(second_id, settings.secret_key))

        created = base.post(
            "/api/tasks",
            json={"title": "Only for first"},
            headers=HEADERS,
        )
        assert created.status_code == 200, created.text
        assert len(base.get("/api/tasks?view=all").json()["items"]) == 1
        assert other.get("/api/tasks?view=all").json()["items"] == []
        assert other.get("/api/projects").json()["items"] == []


def test_first_google_login_claims_legacy_data(settings, monkeypatch):
    application = create_app(settings)
    with TestClient(application) as client:
        with application.state.db.session() as session:
            legacy = Account(
                google_sub=None,
                email="legacy@local",
                name="Legacy",
                is_legacy=1,
            )
            session.add(legacy)
            session.flush()
            legacy_id = legacy.id
            set_current_account(legacy_id)
            session.add(Task(account_id=legacy_id, title="Legacy task"))
            session.flush()
        _fake_google(monkeypatch, sub="first-google", email="first@example.com")
        r = client.post(
            "/api/auth/google",
            json={"id_token": "fake-token", "device_name": "Phone"},
        )
        assert r.status_code == 200, r.text
        token = r.json()["token"]
        tasks = client.get("/api/tasks?view=all", headers={"Authorization": "Bearer " + token})
        assert tasks.status_code == 200
        assert [item["title"] for item in tasks.json()["items"]] == ["Legacy task"]
