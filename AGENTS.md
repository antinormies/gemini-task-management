# AGENTS.md

## Project state

- Implementation is in progress on `feat/rock`: a working FastAPI backend, web UI, SQLite store, AI proposal flow, and a native Android app.
- Read `docs/PLAN.md`, `docs/MOBILE.md`, `docs/AI_INTEGRATION.md`, and `docs/DATA_MODEL.md` before changing behavior.
- Android UI follows `docs/MOBILE.md` §0: 3-tab **Speak / Tasks / System**, Board/List, device-transcript voice. `docs/reference.html` is user-provided design input; do not edit it.
- `main.py` was the old PyCharm stub and has been removed. Entry point is `backend/app/main.py`.
- Layout: `backend/` (FastAPI, SQLite, web UI, JSON API, AI) and `mobile/` (Kotlin/Compose Android app). There is no PWA deliverable.

## Commands

### Backend

- Use the repo-local `.venv` (CPython 3.14.4) directly; do not create a second venv.
- Install: `cd backend && ../.venv/bin/pip install -e ".[dev]"`
- Run: `cd backend && ../.venv/bin/python -m app` (or `../.venv/bin/uvicorn app.main:app --port 8765`)
- Quality gates:
  - `cd backend && ../.venv/bin/ruff format --check .`
  - `cd backend && ../.venv/bin/ruff check .`
  - `cd backend && ../.venv/bin/pytest -q`
- Migrations: `cd backend && ../.venv/bin/alembic upgrade head`
- Seed: `cd backend && ../.venv/bin/python scripts/seed.py`
- Backup: `cd backend && ./scripts/backup.sh`
- Opt-in live AI checks (cost credits):
  - `cd backend && RUN_AGY_CONTRACT=1 ../.venv/bin/pytest tests/contract -q -s`
  - `cd backend && RUN_GEMINI_CONTRACT=1 ../.venv/bin/pytest tests/contract -q -s`

### Android

- SDK: `$HOME/Android/Sdk`; `local.properties` is git-ignored.
- Build: `cd mobile && ./gradlew :app:assembleDebug`
- Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- USB API tunnel: `adb reverse tcp:8765 tcp:8765`
- Unit tests: `cd mobile && ./gradlew :app:testDebugUnitTest`
- Instrumented tests: `cd mobile && ./gradlew :app:connectedDebugAndroidTest` (device connected)
- Lint: `cd mobile && ./gradlew :app:lintDebug`
- Android Studio is not required; use the committed Gradle wrapper.
- Emulator testing on this host needs a reduced-memory AVD (`-memory 1536 -cores 2`); earlyoom can kill the default 4 GB emulator.

### AI configuration

- `backend/.env` is local and ignored. It must contain a non-empty `GTM_SECRET_KEY`.
- Provider order for text is `agy` → OpenCode Go (`deepseek-v4.1-flash`) → Gemini, controlled by `GTM_AI_PROVIDER`.
- Voice is transcript-only: the Android speech recognizer produces the text; the backend uses `GTM_VOICE_PROVIDER`. Never upload audio in v1.
- Never print, commit, or log API keys, device tokens, or pairing codes.

### Environment facts

- Python 3.14.4 at `.venv/bin/python`; SQLite 3.46.1 with FTS5; ffmpeg available but not required.
- Android device: Infinix X6853 (when connected); Java 21; SDK at `~/Android/Sdk`.
- Remote access: Tailscale Serve is the preferred HTTPS path; `cloudflared` is available. The backend must stay on loopback by default.
- The current web UI is server-rendered Jinja plus a small vanilla JS client; no Node build is required.

## Git and delivery

- Remote `origin` is `git@github-hobby:antinormies/gemini-task-management.git`.
- The SSH host alias is **`github-hobby` with a hyphen** (defined in `~/.ssh/config`), not `github_hobby` or `github.com`. Do not switch the remote URL.
- Branch model: `master` is the GitHub default, `development` is integration, and work happens on `feature/<name>` branches from `development`. Current work is on `feat/rock`.
- Commit messages in this implementation use the requested `implementation: {small changes desc}` format.
- Push new branches with `git push -u origin <branch>`.

## Repository hygiene

- `.gitignore` excludes `.venv/`, `.idea/`, `*.iml`, `__pycache__/`, `.env*`, backend runtime DB/cache files, and Android build/local/signing artifacts.
- Never commit `backend/.env`, API keys, device tokens, pairing codes, `mobile/local.properties`, or signing material.
