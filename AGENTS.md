# AGENTS.md

## Project state

- Planning is drafted for a **backend + native Android** split; implementation has not started.
- Read `docs/PLAN.md` first, then `docs/MOBILE.md`, `docs/AI_INTEGRATION.md`, and `docs/DATA_MODEL.md` before changing code.
- Android UI follows the synthesis in `docs/MOBILE.md` §0: 3-tab **Speak / Tasks / System**, Board/List, device-transcript voice. `docs/reference.html` is user-provided design input; do not edit it, and do not copy its sprint/vector/team concepts.
- **Approval gate:** the user is reviewing the revised plan. Do not start implementation until the user explicitly approves it. After approval, execute the phases in `docs/PLAN.md` in order.
- The only current source file is the original `main.py` PyCharm stub. It will be removed when `backend/app/` is created.
- Target layout: `backend/` (FastAPI, SQLite, AI, web UI) and `mobile/` (Kotlin/Compose Android app). There is no PWA deliverable.

## Commands

### Backend (after Phase 1 scaffold)

- Use the repo-local `.venv` (CPython 3.14.4) directly; do not create a second venv.
- Install: `cd backend && ../.venv/bin/pip install -e ".[dev]"`
- Run: `cd backend && ../.venv/bin/python -m app`
- Dev server alternative: `cd backend && ../.venv/bin/uvicorn app.main:app --reload --port 8765`
- Quality gates, in order:
  - `cd backend && ../.venv/bin/ruff format --check .`
  - `cd backend && ../.venv/bin/ruff check .`
  - `cd backend && ../.venv/bin/pytest -q`
- Migrations: `cd backend && ../.venv/bin/alembic upgrade head`
- Real AI contract tests are opt-in and may cost credits:
  - Text: `cd backend && RUN_AGY_CONTRACT=1 ../.venv/bin/pytest tests/contract -q -s`
  - Voice: `cd backend && RUN_GEMINI_CONTRACT=1 ../.venv/bin/pytest tests/contract -q -s`

### Android (after Phase 4 scaffold)

- SDK: `$HOME/Android/Sdk` (platforms 35/36/37 installed). `local.properties` is git-ignored.
- Build: `cd mobile && ./gradlew :app:assembleDebug`
- Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- USB API tunnel for the debug build: `adb reverse tcp:8765 tcp:8765`
- Unit tests: `cd mobile && ./gradlew :app:testDebugUnitTest`
- Instrumented tests: `cd mobile && ./gradlew :app:connectedDebugAndroidTest` (device must be connected)
- Lint: `cd mobile && ./gradlew :app:lintDebug`
- Android Studio is not installed; use the committed Gradle wrapper from the terminal.

### Environment facts

- Python 3.14.4 at `.venv/bin/python`; SQLite 3.46.1 with FTS5; ffmpeg 8.0.1 available but not required for v1 transcript-only voice.
- Android device: Infinix X6853 via ADB; Java 21; SDK at `~/Android/Sdk`.
- Remote access: Tailscale is not installed yet; `cloudflared` is available. Tailscale Serve is the preferred HTTPS path.
- No test, lint, typecheck, or format tooling is configured until the scaffolds land.

## Git and delivery

- Remote `origin` is `git@github-hobby:antinormies/gemini-task-management.git`.
- The SSH host alias is **`github-hobby` with a hyphen** (defined in `~/.ssh/config`), not `github_hobby` or `github.com`. It authenticates as the `antinormies` account that owns the repo; `git@github.com` resolves to a different identity, so do not switch the remote URL.
- Branch model: `master` is the GitHub default branch, `development` is the integration branch, and work happens on `feature/<name>` branches created from `development`.
- Push new branches with `git push -u origin <branch>`.
- The current docs are staged on `feature/project-setup`. After approval, commit them, merge through `development`, and start `feature/foundation`.

## Repository hygiene

- `.gitignore` intentionally excludes `.venv/`, `.idea/`, `*.iml`, `__pycache__/`, and `.env*`.
- When `mobile/` is scaffolded, also ignore `mobile/local.properties`, `.gradle/`, `build/`, `.kotlin/`, `.cxx/`, `captures/`, `*.apk`, `*.aab`, and release keystores.
- Never commit API keys, device tokens, pairing codes, `local.properties`, or signing material.
