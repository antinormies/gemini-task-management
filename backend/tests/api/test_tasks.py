from __future__ import annotations

from datetime import timedelta

from app.timeutils import iso, now_utc


def test_create_update_complete(client):
    due = iso(now_utc() + timedelta(hours=2))
    r = client.post(
        "/api/tasks",
        json={"title": "Write report", "status": "backlog", "due_at": due, "priority": "high"},
        headers={"X-GTM-Request": "1"},
    )
    assert r.status_code == 200, r.text
    task = r.json()["task"]
    assert task["status"] == "backlog"
    assert task["short_key"].startswith("GTM-")
    r = client.get("/api/tasks?view=today")
    assert r.status_code == 200
    assert any(t["id"] == task["id"] for t in r.json()["items"])
    r = client.patch(
        f"/api/tasks/{task['id']}",
        json={"title": "Write final report", "version": task["version"]},
        headers={"X-GTM-Request": "1"},
    )
    assert r.status_code == 200, r.text
    updated = r.json()["task"]
    assert updated["title"] == "Write final report"
    r = client.patch(
        f"/api/tasks/{task['id']}",
        json={"title": "Stale", "version": task["version"]},
        headers={"X-GTM-Request": "1"},
    )
    assert r.status_code == 409
    r = client.post(f"/api/tasks/{task['id']}/complete", headers={"X-GTM-Request": "1"})
    assert r.status_code == 200, r.text
    assert r.json()["task"]["status"] == "done"
    r = client.get("/api/tasks?view=done")
    assert any(t["id"] == task["id"] for t in r.json()["items"])


def test_archive_and_reopen(client):
    r = client.post(
        "/api/tasks",
        json={"title": "Archive me", "status": "backlog"},
        headers={"X-GTM-Request": "1"},
    )
    task = r.json()["task"]
    r = client.delete(f"/api/tasks/{task['id']}", headers={"X-GTM-Request": "1"})
    assert r.status_code == 200
    r = client.get(f"/api/tasks/{task['id']}")
    assert r.status_code == 404


def test_project_and_search(client):
    r = client.post(
        "/api/projects", json={"name": "Work", "color": "#0071e3"}, headers={"X-GTM-Request": "1"}
    )
    assert r.status_code == 200, r.text
    project = r.json()["project"]
    r = client.post(
        "/api/tasks",
        json={"title": "Quarterly planning", "project_id": project["id"], "status": "backlog"},
        headers={"X-GTM-Request": "1"},
    )
    assert r.status_code == 200, r.text
    r = client.get("/api/search?q=Quarterly")
    assert r.status_code == 200
    assert r.json()["items"]


def test_subtask_inherits_parent_project(client):
    project = client.post(
        "/api/projects", json={"name": "Inheritance"}, headers={"X-GTM-Request": "1"}
    ).json()["project"]
    parent = client.post(
        "/api/tasks",
        json={"title": "Parent", "status": "backlog", "project_id": project["id"]},
        headers={"X-GTM-Request": "1"},
    ).json()["task"]
    child = client.post(
        "/api/tasks",
        json={"title": "Child", "status": "backlog", "parent_id": parent["id"]},
        headers={"X-GTM-Request": "1"},
    ).json()["task"]
    assert child["project_id"] == project["id"]


def test_mobile_bootstrap(client):
    response = client.get("/api/mobile/bootstrap")
    assert response.status_code == 200, response.text
    body = response.json()
    assert "projects" in body
    assert "tasks" in body
    assert "usage" in body
    assert "server_time" in body


def test_legacy_status_maps_and_moves_to_sprint(client):
    task = client.post(
        "/api/tasks",
        json={"title": "Direct drag", "status": "inbox"},
        headers={"X-GTM-Request": "1"},
    ).json()["task"]
    assert task["status"] == "backlog"
    response = client.patch(
        "/api/tasks/" + task["id"],
        json={"status": "in_progress"},
        headers={"X-GTM-Request": "1"},
    )
    assert response.status_code == 200, response.text
    assert response.json()["task"]["status"] == "sprint"
