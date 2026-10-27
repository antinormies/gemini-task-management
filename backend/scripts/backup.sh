#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
../.venv/bin/python - <<'PY'
from app.config import get_settings
from app.db import Database

settings = get_settings()
settings.prepare()
db = Database(settings)
target = db.backup()
print(target)
PY
