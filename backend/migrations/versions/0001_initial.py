"""initial schema

Revision ID: 0001_initial
Revises:
Create Date: 2026-10-23
"""

from alembic import op
from sqlalchemy import text

from app import models  # noqa: F401
from app.db import FTS_SQL, Base

revision = "0001_initial"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    bind = op.get_bind()
    Base.metadata.create_all(bind=bind)
    for statement in FTS_SQL:
        bind.execute(text(statement))


def downgrade() -> None:
    bind = op.get_bind()
    for table in reversed(Base.metadata.sorted_tables):
        bind.execute(text(f"DROP TABLE IF EXISTS {table.name}"))
    bind.execute(text("DROP TABLE IF EXISTS tasks_fts"))
