from __future__ import annotations

from sqlalchemy import REAL, ForeignKey, Index, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from .db import Base
from .security import new_id
from .timeutils import now_iso


class Project(Base):
    __tablename__ = "projects"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=new_id)
    name: Mapped[str] = mapped_column(String(200), unique=True, nullable=False)
    color: Mapped[str] = mapped_column(String(20), default="#64748b")
    description: Mapped[str] = mapped_column(Text, default="")
    archived_at: Mapped[str | None] = mapped_column(String(40), nullable=True)
    created_at: Mapped[str] = mapped_column(String(40), default=now_iso)
    updated_at: Mapped[str] = mapped_column(String(40), default=now_iso)

    __table_args__ = (Index("ix_projects_archived", "archived_at"),)


class Task(Base):
    __tablename__ = "tasks"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=new_id)
    title: Mapped[str] = mapped_column(String(300), nullable=False)
    description: Mapped[str] = mapped_column(Text, default="")
    status: Mapped[str] = mapped_column(String(20), default="inbox")
    priority: Mapped[str] = mapped_column(String(10), default="none")
    due_at: Mapped[str | None] = mapped_column(String(40), nullable=True)
    due_all_day: Mapped[int] = mapped_column(Integer, default=0)
    scheduled_start_at: Mapped[str | None] = mapped_column(String(40), nullable=True)
    scheduled_end_at: Mapped[str | None] = mapped_column(String(40), nullable=True)
    estimate_minutes: Mapped[int | None] = mapped_column(Integer, nullable=True)
    project_id: Mapped[str | None] = mapped_column(
        ForeignKey("projects.id", ondelete="SET NULL"), nullable=True
    )
    parent_id: Mapped[str | None] = mapped_column(
        ForeignKey("tasks.id", ondelete="CASCADE"), nullable=True
    )
    sort_order: Mapped[float] = mapped_column(REAL, default=0.0)
    source: Mapped[str] = mapped_column(String(10), default="manual")
    ai_run_id: Mapped[str | None] = mapped_column(
        ForeignKey("ai_runs.id", ondelete="SET NULL"), nullable=True
    )
    version: Mapped[int] = mapped_column(Integer, default=1)
    created_at: Mapped[str] = mapped_column(String(40), default=now_iso)
    updated_at: Mapped[str] = mapped_column(String(40), default=now_iso)
    completed_at: Mapped[str | None] = mapped_column(String(40), nullable=True)
    archived_at: Mapped[str | None] = mapped_column(String(40), nullable=True)

    __table_args__ = (
        Index("ix_tasks_status_due", "status", "due_at"),
        Index("ix_tasks_due_active", "due_at"),
        Index("ix_tasks_project", "project_id"),
        Index("ix_tasks_parent", "parent_id"),
        Index("ix_tasks_scheduled", "scheduled_start_at"),
        Index("ix_tasks_updated", "updated_at"),
    )


class Tag(Base):
    __tablename__ = "tags"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=new_id)
    name: Mapped[str] = mapped_column(String(50), unique=True, nullable=False)
    color: Mapped[str] = mapped_column(String(20), default="#64748b")
    created_at: Mapped[str] = mapped_column(String(40), default=now_iso)


class TaskTag(Base):
    __tablename__ = "task_tags"
    task_id: Mapped[str] = mapped_column(
        ForeignKey("tasks.id", ondelete="CASCADE"), primary_key=True
    )
    tag_id: Mapped[str] = mapped_column(ForeignKey("tags.id", ondelete="CASCADE"), primary_key=True)


class TaskEvent(Base):
    __tablename__ = "task_events"
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    task_id: Mapped[str] = mapped_column(ForeignKey("tasks.id", ondelete="CASCADE"), nullable=False)
    event_type: Mapped[str] = mapped_column(String(40), nullable=False)
    actor: Mapped[str] = mapped_column(String(10), default="user")
    field: Mapped[str | None] = mapped_column(String(40), nullable=True)
    old_value: Mapped[str | None] = mapped_column(Text, nullable=True)
    new_value: Mapped[str | None] = mapped_column(Text, nullable=True)
    metadata_json: Mapped[str] = mapped_column(Text, default="{}")
    created_at: Mapped[str] = mapped_column(String(40), default=now_iso)

    __table_args__ = (Index("ix_task_events_task_created", "task_id", "created_at"),)


class Device(Base):
    __tablename__ = "devices"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=new_id)
    name: Mapped[str] = mapped_column(String(120), nullable=False)
    token_hash: Mapped[str] = mapped_column(String(64), unique=True, nullable=False)
    token_prefix: Mapped[str] = mapped_column(String(16), nullable=False)
    created_at: Mapped[str] = mapped_column(String(40), default=now_iso)
    last_seen_at: Mapped[str | None] = mapped_column(String(40), nullable=True)
    revoked_at: Mapped[str | None] = mapped_column(String(40), nullable=True)

    __table_args__ = (Index("ix_devices_active", "revoked_at"),)


class PairingCode(Base):
    __tablename__ = "pairing_codes"
    code_hash: Mapped[str] = mapped_column(String(64), primary_key=True)
    created_at: Mapped[str] = mapped_column(String(40), default=now_iso)
    expires_at: Mapped[str] = mapped_column(String(40), nullable=False)
    used_at: Mapped[str | None] = mapped_column(String(40), nullable=True)
    attempt_count: Mapped[int] = mapped_column(Integer, default=0)

    __table_args__ = (Index("ix_pairing_expiry", "expires_at"),)


class AIRun(Base):
    __tablename__ = "ai_runs"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=new_id)
    request_id: Mapped[str] = mapped_column(String(80), unique=True, nullable=False)
    kind: Mapped[str] = mapped_column(String(30), nullable=False)
    status: Mapped[str] = mapped_column(String(20), default="queued")
    model: Mapped[str] = mapped_column(String(80), default="")
    effort: Mapped[str | None] = mapped_column(String(20), nullable=True)
    input_text: Mapped[str] = mapped_column(Text, default="")
    context_json: Mapped[str] = mapped_column(Text, default="{}")
    prompt_hash: Mapped[str | None] = mapped_column(String(64), nullable=True)
    prompt_text: Mapped[str | None] = mapped_column(Text, nullable=True)
    raw_envelope_json: Mapped[str | None] = mapped_column(Text, nullable=True)
    structured_output_json: Mapped[str | None] = mapped_column(Text, nullable=True)
    error_code: Mapped[str | None] = mapped_column(String(60), nullable=True)
    error_message: Mapped[str | None] = mapped_column(Text, nullable=True)
    duration_ms: Mapped[int | None] = mapped_column(Integer, nullable=True)
    speech_ms: Mapped[int | None] = mapped_column(Integer, nullable=True)
    input_tokens: Mapped[int | None] = mapped_column(Integer, nullable=True)
    output_tokens: Mapped[int | None] = mapped_column(Integer, nullable=True)
    thinking_tokens: Mapped[int | None] = mapped_column(Integer, nullable=True)
    total_tokens: Mapped[int | None] = mapped_column(Integer, nullable=True)
    device_id: Mapped[str | None] = mapped_column(
        ForeignKey("devices.id", ondelete="SET NULL"), nullable=True
    )
    created_at: Mapped[str] = mapped_column(String(40), default=now_iso)
    started_at: Mapped[str | None] = mapped_column(String(40), nullable=True)
    finished_at: Mapped[str | None] = mapped_column(String(40), nullable=True)

    __table_args__ = (
        Index("ix_ai_runs_created", "created_at"),
        Index("ix_ai_runs_device", "device_id"),
    )


class AIProposal(Base):
    __tablename__ = "ai_proposals"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=new_id)
    ai_run_id: Mapped[str] = mapped_column(
        ForeignKey("ai_runs.id", ondelete="CASCADE"), unique=True, nullable=False
    )
    status: Mapped[str] = mapped_column(String(20), default="pending")
    intent: Mapped[str] = mapped_column(String(40), default="unknown")
    assistant_message: Mapped[str] = mapped_column(Text, default="")
    actions_json: Mapped[str] = mapped_column(Text, default="[]")
    preview_json: Mapped[str] = mapped_column(Text, default="[]")
    undo_json: Mapped[str] = mapped_column(Text, default="{}")
    requires_confirmation: Mapped[int] = mapped_column(Integer, default=1)
    confidence: Mapped[float | None] = mapped_column(REAL, nullable=True)
    created_at: Mapped[str] = mapped_column(String(40), default=now_iso)
    expires_at: Mapped[str | None] = mapped_column(String(40), nullable=True)
    resolved_at: Mapped[str | None] = mapped_column(String(40), nullable=True)

    __table_args__ = (Index("ix_ai_proposals_status", "status", "created_at"),)


class Setting(Base):
    __tablename__ = "settings"
    key: Mapped[str] = mapped_column(String(100), primary_key=True)
    value: Mapped[str] = mapped_column(Text, default="")
    updated_at: Mapped[str] = mapped_column(String(40), default=now_iso)
