# Documentation

Design and implementation planning for **Gemini Task Management (GTM)**.

| Document | Contents |
|---|---|
| [PLAN.md](PLAN.md) | Product scope, UX, backend/mobile architecture, security, delivery phases, risks, decisions |
| [MOBILE.md](MOBILE.md) | Android client stack, screens, pairing, voice pipeline, build commands, tests, release |
| [AI_INTEGRATION.md](AI_INTEGRATION.md) | Verified `agy` contract, measured failure modes, prompt/schema, and the transcript-only voice path |
| [DATA_MODEL.md](DATA_MODEL.md) | SQLite schema, DDL, indexes, FTS5, device auth, migrations, export |
| [reference.html](reference.html) | Voice-task UI design reference ("Echo"); reconciliation decisions are in MOBILE.md |

**Status:** v0.1 implemented on `feat/rock` (backend, web UI, Android client). Run instructions are in the root `README.md`; the docs below remain the design and decision record.

**Decisions locked:**

- Repository split: `backend/` (FastAPI, SQLAlchemy/SQLite, Jinja2 + vanilla JS UI, AI) and `mobile/` (native Kotlin/Jetpack Compose Android app).
- Local web UI: FastAPI + Jinja2 + a small vanilla JS client, no Node build.
- Mobile: native Android, Kotlin + Compose + OkHttp/org.json; 3-tab **Speak / Tasks / System** shell; Board/List tasks; online-first; pairing via short-lived code; no PWA.
- Design: reference tokens, spacing, motion, sheets, and accessibility adapted to Android; no sprints/vectors/teams.
- Storage: SQLite (WAL) with SQLAlchemy 2.0 + Alembic; one backend process.
- AI text: `agy` headless subprocess first, then OpenCode Go (`deepseek-v4.1-flash`), then Gemini; JSON schema and deterministic validation.
- AI voice: Android speech recognition produces the transcript; only the transcript goes to OpenCode Go (default) or Gemini with the same action schema. `agy` remains the slow fallback; cloud audio is v1.1.
- AI proposes; deterministic server code validates and applies after confirmation.
- Remote access: loopback backend behind Tailscale Serve HTTPS; Cloudflare Tunnel as fallback.
- Single user, single backend, single uvicorn worker.
