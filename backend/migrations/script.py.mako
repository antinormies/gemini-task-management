"""${message}"""

from alembic import op
import sqlalchemy as sa
${imports if imports else ""}

revision = ${up_revision}
down_revision = ${down_revision | comma,n}
branch_labels = ${branch_labels | comma,n}
depends_on = ${depends_on | comma,n}


def upgrade() -> None:
    ${upgrades if upgrades else "pass"}


def downgrade() -> None:
    ${downgrades if downgrades else "pass"}
