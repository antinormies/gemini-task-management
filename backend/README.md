# GTM backend

FastAPI + SQLite task manager with a server-rendered web UI and a JSON API for the Android client.

## Setup

```bash
cd backend
../.venv/bin/pip install -e ".[dev]"
cp .env.example .env
# set GTM_SECRET_KEY and an AI provider key
../.venv/bin/python -m app
```

Open <http://127.0.0.1:8765>.

## AI providers

The backend tries providers in this order for text: `agy`, then OpenCode Go
(`deepseek-v4.1-flash`), then Gemini if configured. Voice uses
`GTM_VOICE_PROVIDER` (default `auto`); the Android client sends a transcript,
not audio. This keeps the phone's audio on the device by default.

Set one of:

- `GTM_AGY_BIN` and sign in with `agy`.
- `GTM_OPENCODE_API_KEY` for OpenCode Go.
- `GTM_GEMINI_API_KEY` for the Gemini API.

The API key is read from `.env` and never committed.

## Commands

```bash
cd backend
../.venv/bin/python -m app
../.venv/bin/ruff format --check .
../.venv/bin/ruff check .
../.venv/bin/pytest -q
../.venv/bin/alembic upgrade head
../.venv/bin/python scripts/seed.py
./scripts/backup.sh
```

## Notes

- The server binds loopback by default. For phone access, use Tailscale Serve
  (HTTPS) rather than binding to the LAN.
- Pairing codes are created from the web Settings page. The Android app
  exchanges the code for a bearer token stored in Android Keystore-backed
  storage.
- No audio is uploaded by the Android client in v1; only the transcript is
  sent to the selected AI provider.
