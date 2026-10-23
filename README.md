# Gemini Task Management (GTM)

A single-user, local-first task manager with a server-rendered web UI and a
native Android client. Gemini/agy proposes structured actions; deterministic
server code validates, previews, and applies them.

```
backend/   FastAPI, SQLite, Jinja UI, JSON API, AI providers
mobile/    Kotlin + Jetpack Compose Android app
docs/      Product plan, mobile spec, AI contract, data model
```

## Quick start

```bash
# backend
cd backend
../.venv/bin/pip install -e ".[dev]"
cp .env.example .env
# set GTM_SECRET_KEY and an AI provider key
../.venv/bin/python -m app
# open http://127.0.0.1:8765

# Android (USB tunnel)
cd ../mobile
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb reverse tcp:8765 tcp:8765
adb shell am start -n dev.antinormies.gtm/.MainActivity
```

Generate a pairing code in the web **Settings & system** page, then enter it in
the Android app.

## AI providers

Text commands try `agy`, then OpenCode Go (`deepseek-v4.1-flash`), then Gemini.
Voice uses the phone's speech recognizer and sends only the transcript; the
backend structures it with the same provider chain. Set one of
`GTM_OPENCODE_API_KEY`, `GTM_GEMINI_API_KEY`, or sign in with `agy`.

## Quality

```bash
cd backend
../.venv/bin/ruff format --check .
../.venv/bin/ruff check .
../.venv/bin/pytest -q

cd ../mobile
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

See [docs/PLAN.md](docs/PLAN.md), [docs/MOBILE.md](docs/MOBILE.md),
[docs/AI_INTEGRATION.md](docs/AI_INTEGRATION.md), and
[docs/DATA_MODEL.md](docs/DATA_MODEL.md).
