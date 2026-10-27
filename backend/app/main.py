from __future__ import annotations

from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles
from sqlalchemy import update

from .ai.service import AIService
from .api.routes import ai_api, auth_api, health, pages, projects_api, tasks_api
from .config import Settings, get_settings
from .db import Database
from .models import AIRun
from .services.errors import ServiceError
from .timeutils import now_iso


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or get_settings()

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        settings.prepare()
        db = Database(settings)
        db.init()
        app.state.settings = settings
        app.state.db = db
        app.state.ai_service = AIService(settings)
        app.state.background_tasks = set()
        with db.session() as session:
            session.execute(
                update(AIRun)
                .where(AIRun.status.in_(("queued", "running")))
                .values(
                    status="failed",
                    error_code="INTERRUPTED",
                    error_message="Backend restarted",
                    finished_at=now_iso(),
                )
            )
        yield
        for task in list(app.state.background_tasks):
            task.cancel()

    app = FastAPI(title="Call Task AI", version="0.1.0", lifespan=lifespan)
    app.state.settings = settings
    app.state.templates = pages.TEMPLATES
    app.mount(
        "/static",
        StaticFiles(directory=str(Path(__file__).resolve().parent / "web" / "static")),
        name="static",
    )

    @app.exception_handler(ServiceError)
    async def service_error_handler(_request: Request, exc: ServiceError):
        return JSONResponse(
            status_code=exc.status_code,
            content={"error": {"code": exc.code, "message": str(exc)}},
        )

    app.include_router(health.router)
    app.include_router(auth_api.router)
    app.include_router(tasks_api.router)
    app.include_router(projects_api.router)
    app.include_router(ai_api.router)
    app.include_router(pages.router)
    return app


app = create_app()
