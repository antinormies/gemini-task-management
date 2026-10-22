from __future__ import annotations

import json
from pathlib import Path
from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator

SCHEMA_PATH = Path(__file__).with_name("schema.json")
SCHEMA = json.loads(SCHEMA_PATH.read_text())

Status = Literal["inbox", "todo", "in_progress", "waiting", "done", "cancelled"]
Priority = Literal["none", "low", "medium", "high", "urgent"]


class Strict(BaseModel):
    model_config = ConfigDict(extra="forbid")


class CreateTaskAction(Strict):
    type: Literal["create_task"]
    client_ref: str = Field(min_length=1, max_length=40)
    title: str = Field(min_length=1, max_length=300)
    description: str | None = Field(default=None, max_length=5000)
    status: Literal["inbox", "todo"] = "inbox"
    priority: Priority = "none"
    due_at: str | None = None
    due_all_day: bool = False
    project_id: str | None = None
    tags: list[str] = Field(default_factory=list, max_length=10)
    estimate_minutes: int | None = Field(default=None, ge=0, le=1440)
    parent_ref: str | None = Field(default=None, max_length=40)


class UpdateChanges(Strict):
    title: str | None = Field(default=None, min_length=1, max_length=300)
    description: str | None = Field(default=None, max_length=5000)
    status: Status | None = None
    priority: Priority | None = None
    due_at: str | None = None
    due_all_day: bool | None = None
    project_id: str | None = None
    estimate_minutes: int | None = Field(default=None, ge=0, le=1440)

    @model_validator(mode="after")
    def not_empty(self):
        if not self.model_dump(exclude_unset=True):
            raise ValueError("changes must not be empty")
        return self


class UpdateTaskAction(Strict):
    type: Literal["update_task"]
    task_id: str
    changes: UpdateChanges


class CompleteTaskAction(Strict):
    type: Literal["complete_task"]
    task_id: str


class ArchiveTaskAction(Strict):
    type: Literal["archive_task"]
    task_id: str
    reason: str | None = Field(default=None, max_length=300)


class QueryFilter(Strict):
    status: list[Status] = Field(default_factory=list)
    due_before: str | None = None
    due_after: str | None = None
    project_id: str | None = None
    search: str | None = Field(default=None, max_length=200)
    include_completed: bool = False


class QueryTasksAction(Strict):
    type: Literal["query_tasks"]
    filter: QueryFilter = Field(default_factory=QueryFilter)
    sort: Literal["due_at", "priority", "created_at", "updated_at"] = "due_at"
    limit: int = Field(default=50, ge=1, le=100)


class PlanItem(Strict):
    task_id: str
    start: str = Field(pattern=r"^([01]\d|2[0-3]):[0-5]\d$")
    end: str = Field(pattern=r"^([01]\d|2[0-3]):[0-5]\d$")


class PlanDayAction(Strict):
    type: Literal["plan_day"]
    date: str = Field(pattern=r"^\d{4}-\d{2}-\d{2}$")
    items: list[PlanItem] = Field(default_factory=list, max_length=30)


class SubtaskSpec(Strict):
    title: str = Field(min_length=1, max_length=300)
    description: str | None = Field(default=None, max_length=2000)
    estimate_minutes: int | None = Field(default=None, ge=0, le=1440)


class CreateSubtasksAction(Strict):
    type: Literal["create_subtasks"]
    parent_id: str
    subtasks: list[SubtaskSpec] = Field(min_length=1, max_length=10)


class SummarizeAction(Strict):
    type: Literal["summarize"]
    text: str = Field(max_length=2000)


ActionUnion = (
    CreateTaskAction
    | UpdateTaskAction
    | CompleteTaskAction
    | ArchiveTaskAction
    | QueryTasksAction
    | PlanDayAction
    | CreateSubtasksAction
    | SummarizeAction
)
Action = Annotated[ActionUnion, Field(discriminator="type")]


class Interpretation(Strict):
    intent: Literal[
        "create_task",
        "update_task",
        "complete_task",
        "archive_task",
        "query_tasks",
        "plan_day",
        "create_subtasks",
        "summarize",
        "unknown",
    ]
    assistant_message: str = Field(default="", max_length=500)
    transcript: str | None = Field(default=None, max_length=4000)
    requires_confirmation: bool = True
    confidence: float = Field(default=0.0, ge=0, le=1)
    actions: list[Action] = Field(default_factory=list, max_length=20)

    @property
    def destructive(self) -> bool:
        return any(isinstance(a, ArchiveTaskAction) for a in self.actions)

    def action_dicts(self) -> list[dict]:
        return [a.model_dump(mode="json") for a in self.actions]


def parse_interpretation(data: dict) -> Interpretation:
    return Interpretation.model_validate(data)


def redacted_actions(actions: list[dict]) -> list[dict]:
    return actions
