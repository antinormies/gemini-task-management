#!/usr/bin/env bash
# Runs on the VPS as the deploy user after the workflow rsyncs backend/.
set -euo pipefail

APP_DIR=${APP_DIR:-/home/deploy/antaratask/backend}
SERVICE=${SERVICE:-antaratask-backend}
PORT=${PORT:-8765}

cd "$APP_DIR"

if [ ! -f .env ]; then
  echo "Missing $APP_DIR/.env. Create it from deploy/env.example before deploying." >&2
  exit 1
fi

if ! grep -q '^GTM_AI_CAPABILITY=chat.standard$' .env; then
  echo "GTM_AI_CAPABILITY must be chat.standard (this deployment is standard-only)." >&2
  exit 1
fi

if [ ! -d .venv ]; then
  python3 -m venv .venv
fi

.venv/bin/python -m pip install --upgrade pip
.venv/bin/pip install --no-cache-dir -e .
.venv/bin/alembic upgrade head

if sudo -n systemctl restart "$SERVICE" 2>/dev/null; then
  :
elif systemctl --user restart "$SERVICE" 2>/dev/null; then
  :
else
  echo "Could not restart $SERVICE. Install deploy/antaratask-backend.service and allow the deploy user to restart it." >&2
  exit 1
fi

for _ in $(seq 1 30); do
  if curl -fsS "http://127.0.0.1:$PORT/api/health" >/dev/null; then
    echo "Backend healthy on port $PORT."
    exit 0
  fi
  sleep 2
done

echo "Health check failed on http://127.0.0.1:$PORT/api/health" >&2
exit 1
