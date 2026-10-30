"""accounts and tenant ownership

Revision ID: 0002_accounts
Revises: 0001_initial
Create Date: 2026-10-30

The first migration used Base.metadata.create_all, so a fresh database already
contains the current account schema. This migration is therefore idempotent:
it adds account ownership only to an older, single-user database and assigns
existing rows to a claimable legacy account.
"""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op

revision = "0002_accounts"
down_revision = "0001_initial"
branch_labels = None
depends_on = None

LEGACY_ID = "00000000-0000-7000-8000-000000000001"
SCOPED_TABLES = ("projects", "tasks", "tags", "devices", "ai_runs", "ai_proposals")


def _now() -> str:
    return (
        __import__("datetime")
        .datetime.now(__import__("datetime").timezone.utc)
        .isoformat(timespec="milliseconds")
        .replace("+00:00", "Z")
    )


def _columns(bind, table: str) -> set[str]:
    return {column["name"] for column in sa.inspect(bind).get_columns(table)}


def _indexes(bind, table: str) -> set[str]:
    return {index["name"] for index in sa.inspect(bind).get_indexes(table)}


def upgrade() -> None:
    bind = op.get_bind()
    inspector = sa.inspect(bind)
    if "accounts" not in inspector.get_table_names():
        op.create_table(
            "accounts",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("google_sub", sa.String(255), nullable=True, unique=True),
            sa.Column("email", sa.String(320), nullable=False, server_default=""),
            sa.Column("name", sa.String(200), nullable=False, server_default=""),
            sa.Column("picture", sa.Text(), nullable=False, server_default=""),
            sa.Column("is_legacy", sa.Integer(), nullable=False, server_default="0"),
            sa.Column("created_at", sa.String(40), nullable=False, server_default=""),
            sa.Column("updated_at", sa.String(40), nullable=False, server_default=""),
            sa.Column("last_login_at", sa.String(40), nullable=True),
        )
        bind.execute(
            sa.text(
                "INSERT INTO accounts (id, google_sub, email, name, picture, is_legacy, "
                "created_at, updated_at) VALUES (:id, NULL, :email, :name, '', 1, :now, :now)"
            ),
            {"id": LEGACY_ID, "email": "legacy@local", "name": "Legacy data", "now": _now()},
        )
        for table in SCOPED_TABLES:
            if "account_id" not in _columns(bind, table):
                op.add_column(
                    table,
                    sa.Column("account_id", sa.String(36), nullable=True),
                )
            index_name = f"ix_{table}_account_id"
            if index_name not in _indexes(bind, table):
                op.create_index(index_name, table, ["account_id"])
            bind.execute(
                sa.text(f"UPDATE {table} SET account_id = :id WHERE account_id IS NULL"),
                {"id": LEGACY_ID},
            )
        return

    # A fresh 0001 run created the current schema (including account_id columns).
    for table in SCOPED_TABLES:
        if "account_id" in _columns(bind, table):
            index_name = f"ix_{table}_account_id"
            if index_name not in _indexes(bind, table):
                op.create_index(index_name, table, ["account_id"])


def downgrade() -> None:
    # Account ownership is additive; keep the data rather than destroying it.
    pass
