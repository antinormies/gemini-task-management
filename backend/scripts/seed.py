from __future__ import annotations

from datetime import timedelta

from app.config import get_settings
from app.db import Database
from app.services import projects as project_service
from app.services import tasks as task_service
from app.timeutils import iso, now_utc


def main() -> None:
    settings = get_settings()
    settings.prepare()
    db = Database(settings)
    db.init()
    with db.session() as session:
        if task_service.list_tasks(session, view="all", include_done=True):
            print("Database already has tasks; seed skipped.")
            return
        work = project_service.create_project(
            session,
            {
                "name": "Work",
                "color": "#0071e3",
                "description": "Projects, follow-ups, and delivery",
            },
        )
        personal = project_service.create_project(
            session,
            {"name": "Personal", "color": "#16a34a", "description": "Life admin and health"},
        )
        now = now_utc()
        task_service.create_task(
            session,
            {
                "title": "Call the dentist",
                "status": "todo",
                "priority": "high",
                "due_at": iso(now + timedelta(hours=3)),
                "estimate_minutes": 15,
                "project_id": personal.id,
                "tags": ["health"],
            },
            tz_name=settings.timezone,
        )
        task_service.create_task(
            session,
            {
                "title": "Renew the domain",
                "status": "todo",
                "priority": "urgent",
                "due_at": iso(now - timedelta(days=1)),
                "project_id": work.id,
                "tags": ["admin"],
            },
            tz_name=settings.timezone,
        )
        task_service.create_task(
            session,
            {
                "title": "Read SQLite WAL docs",
                "status": "inbox",
                "priority": "low",
                "tags": ["learning"],
            },
            tz_name=settings.timezone,
        )
        task_service.create_task(
            session,
            {
                "title": "Waiting for design review",
                "status": "waiting",
                "priority": "medium",
                "project_id": work.id,
            },
            tz_name=settings.timezone,
        )
        parent = task_service.create_task(
            session,
            {
                "title": "Ship the landing page",
                "status": "todo",
                "priority": "high",
                "project_id": work.id,
                "estimate_minutes": 240,
            },
            tz_name=settings.timezone,
        )
        for title in ("Write the copy", "Build the hero", "Add mobile layout"):
            task_service.create_task(
                session,
                {
                    "title": title,
                    "status": "todo",
                    "parent_id": parent.id,
                    "project_id": work.id,
                    "estimate_minutes": 45,
                },
                tz_name=settings.timezone,
            )
        done = task_service.create_task(
            session,
            {"title": "Set up Python 3.14 venv", "status": "todo", "project_id": personal.id},
            tz_name=settings.timezone,
        )
        task_service.complete_task(session, done.id)
        print("Seed data created.")


if __name__ == "__main__":
    main()
