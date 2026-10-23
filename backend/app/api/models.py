from __future__ import annotations

from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class TaskCreate(BaseModel):
    title: str = Field(min_length=1, max_length=300)
    description: str = ""
    status: str = "inbox"
    priority: str = "none"
    due_at: str | None = None
    due_all_day: bool = False
    estimate_minutes: int | None = Field(default=None, ge=0, le=1440)
    project_id: str | None = None
    parent_id: str | None = None
    tags: list[str] = Field(default_factory=list)


class TaskUpdate(BaseModel):
    model_config = ConfigDict(extra="forbid")
    title: str | None = None
    description: str | None = None
    status: str | None = None
    priority: str | None = None
    due_at: str | None = None
    due_all_day: bool | None = None
    estimate_minutes: int | None = None
    project_id: str | None = None
    version: int | None = None
    tags: list[str] | None = None


class ProjectCreate(BaseModel):
    name: str
    color: str = "#64748b"
    description: str = ""


class ProjectUpdate(BaseModel):
    name: str | None = None
    color: str | None = None
    description: str | None = None
    archived: bool | None = None


class PairRequest(BaseModel):
    code: str
    device_name: str = "Android phone"


class TextInterpretRequest(BaseModel):
    text: str
    view: str = "today"
    selected_task_id: str | None = None


class VoiceRequest(BaseModel):
    transcript: str
    duration_ms: int = 0
    view: str = "speak"
    selected_task_id: str | None = None
    language: str = "en-US"


class OrderRequest(BaseModel):
    sort_order: float


class ImportRequest(BaseModel):
    data: dict[str, Any]
