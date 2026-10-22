# Gemini Task Management — Build Plan

**Status:** Revised draft for approval — backend + native Android split
**Last updated:** 2026-10-23
**Repo:** `antinormies/gemini-task-management`
**Working name:** GTM
**Current code:** only the PyCharm `main.py` stub; this plan replaces it with `backend/` and `mobile/`.
**Approval gate:** do not start implementation until this revision is approved.

### Revision summary (2026-10-23)

- Repository is split into `backend/` (FastAPI, SQLite, web UI, AI) and `mobile/` (native Kotlin/Compose Android app).
- Android is a first-class client: pairing, task CRUD, voice capture, TTS, proposal review.
- The Android UI is reconciled with `docs/reference.html`: a 3-tab **Speak / Tasks / System** shell, a Board/List task surface, and a diagnostics screen.
- Voice uses device speech recognition by default; only the transcript goes to Gemini 3.8 Flash with the same JSON schema. `agy` remains the text/async path and slow fallback. Cloud audio is v1.1.
- Device auth adds `devices` + `pairing_codes` tables and bearer-token middleware.
- Remote access is loopback + Tailscale Serve HTTPS; no public bind.
- Delivery plan is now 7 phases, **22–32 focused days** (27–40 with buffer).
- No application code has been written. The root `main.py` stub is untouched.

---

## 1. Executive summary

GTM is a **single-user, local-first task manager** with a server-rendered web UI and a **native Android client**, both backed by one FastAPI service. Text commands run through the **Antigravity CLI (`agy`)**; low-latency phone voice commands use the **Gemini API** with the same action schema.

The core design rule:

> The app owns the data and the rules. Gemini only proposes structured actions. Deterministic server code validates, previews, and applies them.

Manual task management must work fully without AI. The AI layer is optional, degradable, and never has direct database or shell access.

This shape is deliberate. A spike on 2026-10-22 showed that `agy` is an **agent harness**, not a plain model API: it can try to run tools, return `SUCCESS` with an empty response when a tool is denied, and vary from ~5s to >90s. The plan isolates it behind a narrow `AgyClient`, constrains it with an empty workspace and JSON schema, and treats every response as untrusted input.

**Recommended stack:** Python 3.14, FastAPI, Jinja2, HTMX + Alpine.js (no Node build), SQLite (WAL), SQLAlchemy 2.0, Alembic, Pydantic v2, pytest, ruff; Kotlin 2.x + Jetpack Compose + Retrofit for Android.

**Estimated effort:** 22–32 focused solo days across seven phases; 27–40 with a 25–30% buffer. The backend/web/text-AI portion is 13–19 focused days; the Android client, design system, and voice layer add 9–13.

---

## 2. Product definition

### 2.1 Problem

Task capture is slow when it requires filling fields. Planning is manual when priorities and time available change. Existing AI task apps either lock data in a cloud or let the model mutate tasks without review. GTM keeps everything local and makes AI a **proposal engine**, not an autonomous actor.

### 2.2 Primary user

One technical user (you): the web app at the desk, the native Android app on the phone, both talking to the same local backend. No multi-user accounts, sharing, or cloud sync in v1. The phone is paired to the single user, not registered as a separate account.

### 2.3 Jobs to be done

1. Capture a task in under 5 seconds on the phone, including by voice.
2. Turn messy natural language into a structured task.
3. See what actually matters today on both web and Android.
4. Re-plan the day when something slips.
5. Break a large task into actionable steps.
6. Review what got done and what is stuck.
7. Trust that the AI cannot silently change or delete data.
8. Use the phone from anywhere on the private tailnet without exposing the backend publicly.

### 2.4 v1 scope

- **One backend** in `backend/`: FastAPI, SQLite, web UI, JSON API, AI orchestration.
- **One native Android client** in `mobile/`: Kotlin + Jetpack Compose, paired to the backend.
- Local web app, single user; backend binds loopback by default and is reached from the phone through Tailscale Serve HTTPS.
- Tasks with title, notes, status, priority, due date/time, estimate, project, tags, subtasks, and manual ordering.
- Web views: **Today**, **Inbox**, **Upcoming**, **Anytime**, **Waiting**, **Projects**, **Search**, **Done**.
- Android shell: **Speak**, **Tasks**, **System**.
- Tasks: **Board** (To do / In progress / Waiting / Done) and **List** (grouped by project, filters), task detail sheet, search, quick add.
- **Android voice capture** from the Speak tab: tap-to-start, live transcript, capture card, proposal review, optional TTS. Device speech recognition is the default; only the transcript is sent to the backend.
- System: AI usage, storage, engine/device facts, backup/rebuild/unpair actions.
- Manual CRUD, inline editing, drag reorder, keyboard shortcuts on web; tap/long-press actions on Android.
- AI command bar (text) producing proposals for create, update, complete, archive, query, plan, breakdown, and summary.
- Preview + confirmation before applying AI changes; destructive actions require explicit confirmation (tap, not voice alone).
- Device pairing via a short-lived code; bearer token stored in Android Keystore-backed storage; revoke from web.
- SQLite persistence, JSON export/import, backup command.
- Settings page with timezone, work hours, AI model/effort, AI on/off, `agy` health, paired devices, and voice status.
- Activity log per task and per AI run; voice runs are recorded as `voice_interpret`.
- Android debug APK installed directly on the connected phone; release APK sideloaded after hardening.

### 2.5 Explicit non-goals for v1

- Multi-user, teams, sharing, permissions.
- iOS, multi-device sync beyond one server, real-time collaboration.
- Public internet exposure without a VPN/TLS front door.
- Offline writes, background sync, conflict resolution beyond optimistic `version`.
- Always-on listening, wake word, lock-screen recording.
- Full-duplex Gemini Live conversation (v1.1, not v1).
- Cloud audio transcription in v1 (v1.1 opt-in).
- Push notifications, reminders, alarms, recurring tasks.
- Calendar, email, Slack, or Google Tasks integrations.
- Autonomous background agent that changes tasks without approval.
- Rich-text editor; notes are Markdown, rendered safely.
- Time tracking, billing, Pomodoro.
- Semantic/vector search; no embeddings or vector index.
- Sprints, story points, epics as separate entities, teams, or assignees.
- PWA, Flutter, React Native, or a second mobile codebase.

### 2.6 Success criteria

| Metric | Target |
|---|---|
| Manual CRUD latency (local) | p95 < 100 ms |
| AI interpretation latency | p50 < 15 s, p95 < 60 s |
| Scripted utterance success (valid proposal) | ≥ 95% |
| Unconfirmed destructive changes | 0 |
| Data loss on restart/migration | 0 |
| Fresh setup time | < 15 minutes (backend + debug APK install) |
| AI failure behavior | Clear error, manual path still works |
| Android cold start | < 2 s |
| Android list refresh (LAN/Tailscale) | p95 < 1.5 s |
| Voice command latency | p50 < 4 s, p95 < 8 s |
| Voice command success (20 scripted utterances) | ≥ 95% valid proposals |

---

## 3. User experience

### 3.1 Layout

```
┌──────────────────────────────────────────────────────────────────────┐
│  GTM        [ Ask Gemini…                                    ⌘K ]    │
├──────────────┬───────────────────────────────────────┬───────────────┤
│  Today       │  Today · Thu 22 Oct                   │  Task detail  │
│  Inbox    3  │  ─────────────────────────────────    │  ───────────  │
│  Upcoming    │  ○ Call the dentist      15:00  High  │  Title        │
│  Anytime     │  ○ Ship landing page     Fri    Med  │  Notes        │
│  Waiting     │  ● Write weekly report   done         │  Project      │
│  Done        │                                       │  Tags         │
│              │  + Add task                           │  Subtasks     │
│  Projects    │                                       │  Activity     │
│  · Work      │                                       │               │
│  · Personal  │                                       │               │
└──────────────┴───────────────────────────────────────┴───────────────┘
```

- Left sidebar: smart views and projects.
- Center: task list with sections (Overdue, Today, Later).
- Right: detail drawer; closes on `Esc`.
- Top: command bar for AI; `/` focuses search, `c` creates, `?` shows shortcuts.
- Web on a narrow screen: single column, detail drawer becomes a full-screen sheet.
- Android: three-tab native shell — **Speak**, **Tasks**, **System**. Tasks uses Board/List; the web's GTD views become filters and groups. The phone is the primary voice/capture surface. See §3.7 and [MOBILE.md](MOBILE.md).

### 3.2 Views

| View | Query |
|---|---|
| Today | Due today or overdue, plus tasks scheduled today; excludes done/cancelled |
| Inbox | `status = inbox`, no project required |
| Upcoming | Due in the next 7 local days |
| Anytime | `todo` / `in_progress` with no due date |
| Waiting | `status = waiting` |
| Projects | Active tasks for one project, top-level first |
| Search | FTS5 over title/notes, plus tag/project filters |
| Done | Completed in the last 30 days |

**Android mapping.** The same task data is presented as:

- **Board:** To do (inbox + todo, with an Inbox badge), In progress, Waiting, Done. Horizontal, snap-scrolling columns.
- **List:** grouped by project; filter chips All / Today / Overdue; Inbox group first; search sheet.
- **Header:** Today + completion meter, not a sprint. `estimate_minutes` replaces story points; `project_id` replaces epic; `waiting` replaces Review.
- **System:** real usage/storage/engine diagnostics, not vectors or cloud models.

### 3.3 AI command flow

1. User submits text in the command bar.
2. Server creates an `ai_run` (`queued`) and returns `202` with `run_id`.
3. A single in-process worker invokes `agy`, validates the envelope, and stores a proposal.
4. UI polls or subscribes to SSE; shows elapsed time and a cancel option.
5. Proposal card shows exactly what will change, including field diffs.
6. User clicks **Apply** or **Cancel**.
7. Apply runs in one transaction, writes `task_events`, and returns the updated list.
8. **Undo** is available for the last applied proposal (Phase 3).

No AI request blocks the HTTP request for its full duration. No AI response is applied automatically.

The Android voice path reuses steps 5–8. Instead of audio upload, the phone's speech recognizer produces a transcript; the server calls Gemini 3.8 Flash **text** with the same schema and returns the validated proposal synchronously. `agy` remains the text/async path and the slow fallback when Gemini is unavailable. Cloud audio transcription is v1.1, off by default.

### 3.4 Example interactions

| Utterance | Intent | Proposal |
|---|---|---|
| “Call the dentist next Tuesday at 3pm, high priority” | create | One task, due 2026-10-27 15:00 WIB, priority high |
| “Move the report to Friday and make it urgent” | update | Diff: `due_at`, `priority` |
| “What’s on my plate today?” | query | Filter + short summary; no writes |
| “Plan my day, I have 4 hours” | plan_day | Time blocks for selected tasks |
| “Break the landing page into steps” | breakdown | 4–8 subtasks under the parent |
| “Mark the invoice task done” | complete | Status change with confirmation |
| “Delete the old draft” | archive | Archive proposal; explicit confirm |
| “I finished the report” | complete | Match by title; show candidate if ambiguous |

### 3.5 Keyboard shortcuts

| Key | Action |
|---|---|
| `c` | New task |
| `⌘K` / `Ctrl+K` | Focus AI command bar |
| `/` | Search |
| `j` / `k` | Move selection |
| `x` | Complete selected |
| `e` | Edit selected |
| `Esc` | Close drawer/modal |
| `?` | Shortcut help |

### 3.6 States that must be designed

- **AI disabled / `agy` missing / not authenticated:** command bar disabled with a clear setup link; manual app unaffected.
- **AI running:** elapsed timer, “Thinking…” skeleton, cancel button.
- **AI timeout / tool denied / invalid output:** human-readable error with a Retry button and a “Create manually” fallback.
- **Low confidence (< 0.6):** proposal is shown as “Did you mean…?” and requires editing before apply.
- **Ambiguous task match:** show candidate tasks; never guess an ID.
- **No tasks:** useful empty state with `c` and command-bar examples.
- **Offline:** everything except AI works; no external assets/CDNs.
- **Android unpaired / token revoked:** pairing screen with a clear code-entry path; the web Settings page is where the code is generated.
- **Android server unreachable:** banner, Retry, and a diagnostics line with the base URL; no silent local writes.

### 3.7 Android client and voice flow

**Pairing**

1. Web Settings → **Pair a phone** → six-digit code, valid 5 minutes.
2. Android enters the code and a device name.
3. Backend returns a bearer token once; Android stores it in Keystore-backed encrypted storage.
4. Revoking the device from web Settings invalidates the token immediately.

**Shell (design-integrated)**

- Bottom tabs: **Speak** (default), **Tasks**, **System**.
- **Speak:** dark stage, orb, state pill, waveform, live transcript, capture card, error/retry. Tap to start; tap again or pause ~10 s to finish; 60 s max.
- **Tasks:** Board / List segmented control. Board columns **To do / In progress / Waiting / Done**; List grouped by project with All / Today / Overdue filters. Header is Today + completion meter, not a sprint.
- **Task detail:** bottom sheet with priority/project/estimate/due, checklist from subtasks, notes, move-to status, archive.
- **System:** stats, 7-day AI usage, storage, engine/device facts, backup/rebuild-FTS/unpair actions. No vectors, sprints, or cloud sign-out.

**Voice flow**

1. Android `SpeechRecognizer` (on-device when available) streams partial text. Only the transcript is sent.
2. `POST /api/voice/interpret` (JSON) validates transcript length/duration, calls Gemini 3.8 Flash **text** with the shared schema, and returns a proposal.
3. Simple creates show a capture card (Add / Discard); updates, archives, multi-actions, and low-confidence results open a proposal sheet with diffs.
4. Apply is a server call; destructive actions require an explicit tap. Optional short TTS confirms the result.
5. If Gemini is unavailable, the same transcript can use `agy` ("slow mode"). If the recognizer is unavailable, fall back to typed input.

**Design mapping**

- “Sprint 7” → Today/selected filter; story points → `estimate_minutes`; epic → project; Review column → `waiting`; assignee/type/vector/token-model panels → dropped or replaced with real GTM data.
- Visual tokens, spacing, motion, sheet/focus behavior, and accessibility follow `reference.html`, adapted to Android Roboto and Material 3. Full spec: [MOBILE.md](MOBILE.md).

**Not in v1:** always-on listening, wake word, lock-screen recording, Gemini Live duplex conversation, cloud audio transcription, offline writes.

---

## 4. Architecture

### 4.1 System diagram

```
┌────────────────────────────┐          ┌────────────────────────────┐
│ Browser                    │          │ Android (Kotlin/Compose)   │
│ Jinja2 + HTMX + Alpine.js  │          │ pairing, tasks, voice, TTS │
└─────────────┬──────────────┘          └─────────────┬──────────────┘
              │ HTTPS                                  │ HTTPS
              │ (Tailscale Serve)                      │ (Tailscale Serve)
              └───────────────────┬────────────────────┘
                                  ▼
┌──────────────────────────────────────────────────────────────┐
│ FastAPI backend (single process, one uvicorn worker)          │
│                                                              │
│  Web pages / JSON API      Voice API          Text AI API     │
│         │                     │                    │           │
│         ▼                     ▼                    ▼           │
│    TaskService           VoiceService        ProposalService  │
│         │                     │                    │           │
│         ▼                     ▼                    ▼           │
│ SQLAlchemy → SQLite     Gemini 3.8 Flash     AI worker         │
│ (WAL, FTS5)             text + schema        (asyncio, max 1) │
│                                                    │           │
│                                                    ▼           │
│                                              AgyClient         │
│                                              (text, async)    │
│                                                    │           │
│                                                    ▼           │
│                                         `agy -p … --json-schema`│
└──────────────────────────────────────────────────────────────┘
```

### 4.2 Stack and rationale

| Concern | Choice | Why |
|---|---|---|
| Language | Python 3.14.4 (existing venv) | Already installed; good ecosystem; async subprocess support |
| Web framework | FastAPI + Uvicorn | Async, typed, easy SSE/JSON, automatic docs for the API |
| Templates | Jinja2 | Server-rendered, no Node build step, fast iteration |
| Interactivity | HTMX 2 + Alpine.js | Partial updates and small client state without a SPA |
| Styling | One custom CSS file with tokens | No Tailwind/Node toolchain; full control |
| DB | SQLite (WAL, FTS5) | Single-user local-first; zero operations; excellent fit |
| ORM | SQLAlchemy 2.0 (typed) | Mature, migrations, safe parameterization |
| Migrations | Alembic (`render_as_batch=True`) | Standard; handles SQLite ALTER limitations |
| Validation | Pydantic v2 | Same models for API, AI schema validation, settings |
| Settings | pydantic-settings + `.env` | Typed config, testable |
| Tests | pytest + httpx ASGI transport | Fast; no server needed |
| Lint/format | ruff | One tool, fast |
| Packaging | `pyproject.toml`, pip editable install | No extra package manager required |
| Background jobs | In-process `asyncio` queue | One user; no Redis/Celery |
| Frontend deps | Vendored `htmx.min.js`, `alpine.min.js` | Offline-friendly, no CDN |
| Mobile language | Kotlin 2.x | First-class Android, coroutines, Compose |
| Mobile UI | Jetpack Compose + Material 3 | Fast iteration; no XML layouts |
| Mobile networking | Retrofit + OkHttp + kotlinx.serialization | Typed API client, interceptors, testability |
| Mobile state | ViewModel + StateFlow + coroutines | Lifecycle-safe, testable |
| Mobile auth | Pairing code → bearer token in Keystore-backed storage | No password/account system; revocable |
| Mobile voice | Android `SpeechRecognizer` (on-device when available) + `TextToSpeech` | Transcript-only privacy; partial results; offline TTS |
| Mobile build | Gradle Kotlin DSL + wrapper | CLI builds without Android Studio |
| Voice AI | Gemini 3.8 Flash **text** + response schema | Low latency, structured output, same schema; no audio upload by default |
| Remote access | Tailscale Serve (primary), Cloudflare Tunnel (fallback) | Automatic HTTPS; no public bind |

**Deliberately rejected:** React/Vite (build chain, unnecessary), Flask (async subprocess/SSE less natural), raw `sqlite3` (manual migrations/row mapping), Celery/Redis (operational overhead), Tailwind CLI (Node dependency).

### 4.3 Directory layout

```
gemini-task-management/
├── backend/
│   ├── pyproject.toml
│   ├── README.md
│   ├── app/
│   │   ├── __init__.py
│   │   ├── __main__.py              # python -m app
│   │   ├── main.py                  # create_app(), lifespan, router mounting
│   │   ├── config.py                # Settings
│   │   ├── db.py                    # engine, session, PRAGMAs, FTS helpers
│   │   ├── models.py                # SQLAlchemy models
│   │   ├── repositories/
│   │   │   ├── tasks.py
│   │   │   ├── projects.py
│   │   │   ├── ai_runs.py
│   │   │   └── devices.py
│   │   ├── services/
│   │   │   ├── tasks.py             # business rules, events, ordering
│   │   │   ├── search.py            # FTS + filters
│   │   │   ├── proposals.py         # validate, preview, apply, undo
│   │   │   ├── voice.py             # transcript validation + Gemini Flash call
│   │   │   └── auth.py              # pairing, tokens, revocation
│   │   ├── ai/
│   │   │   ├── agy_client.py        # subprocess implementation
│   │   │   ├── gemini_client.py     # text Gemini client (voice transcripts)
│   │   │   ├── prompts.py           # prompt builder, date table
│   │   │   ├── schemas.py           # Pydantic action models
│   │   │   ├── schema.json          # JSON schema shared by text + voice
│   │   │   ├── parser.py            # envelope → proposal
│   │   │   └── actions.py           # action executor
│   │   ├── api/
│   │   │   ├── routes/pages.py
│   │   │   ├── routes/tasks_api.py
│   │   │   ├── routes/projects_api.py
│   │   │   ├── routes/ai_api.py
│   │   │   ├── routes/voice_api.py
│   │   │   ├── routes/auth_api.py
│   │   │   ├── routes/health.py
│   │   │   └── deps.py               # auth/session dependencies
│   │   ├── web/
│   │   │   ├── templates/
│   │   │   └── static/
│   │   └── workers/ai_worker.py
│   ├── migrations/                  # Alembic env + versions
│   ├── scripts/
│   │   ├── dev.sh
│   │   ├── backup.sh
│   │   ├── seed.py
│   │   └── smoke_agy.py
│   └── tests/
│       ├── unit/
│       ├── api/
│       ├── contract/
│       └── fixtures/
├── mobile/
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   ├── gradle/libs.versions.toml
│   ├── gradle/wrapper/
│   ├── gradlew
│   ├── gradlew.bat
│   ├── app/
│   │   ├── build.gradle.kts
│   │   └── src/main/java/dev/antinormies/gtm/
│   │       ├── data/                # Retrofit API, DTOs, repositories
│   │       ├── ui/                  # Compose screens + ViewModels
│   │       └── voice/               # SpeechRecognizer + TTS
│   ├── local.properties             # git-ignored
│   └── README.md
├── docs/
├── .gitignore
├── README.md
└── AGENTS.md
```

Full Android structure and commands: [MOBILE.md](MOBILE.md).

### 4.4 Runtime and deployment

- Run as **one process, one uvicorn worker**. The AI worker queue is in-memory; multiple workers would duplicate jobs and break polling.
- Default bind: `127.0.0.1:8765`. Device pairing is enabled by default, so the app refuses to start if `GTM_SECRET_KEY` is empty. Changing the key invalidates every paired phone. If `GTM_HOST` is not loopback, the app additionally requires `GTM_PUBLIC_ORIGIN` to be a trusted HTTPS origin. The preferred deployment keeps the backend on loopback behind Tailscale Serve; do not expose it directly.
- **Web:** browser → `https://<machine>.<tailnet>.ts.net` → Tailscale Serve → `127.0.0.1:8765`.
- **Android debug:** `adb reverse tcp:8765 tcp:8765` → the debug build points at `http://127.0.0.1:8765`. Traffic stays on the USB tunnel and avoids TLS during development.
- **Android release:** Tailscale Serve HTTPS base URL; release builds refuse cleartext and refuse an empty base URL.
- Data directory: `~/.local/share/gtm/` (or `$XDG_DATA_HOME/gtm`), containing `gtm.db`, `agy-workspace/`, `backups/`, `logs/`.
- Optional `systemd --user` unit: `gtm.service` with `Restart=on-failure`, `WorkingDirectory=<repo>/backend`, `ExecStart=<repo>/.venv/bin/python -m app`.
- Host must be awake for phone access. If the desktop sleeps, use an always-on x86-64 mini PC; `agy` is an x86-64 binary and will not run on a Raspberry Pi.
- No Docker requirement. A `Dockerfile` is optional and not part of v1.
- Android delivery: debug APK over USB; release APK sideloaded. Play Store is optional and not v1.
- Graceful shutdown: stop accepting AI/voice jobs, cancel the running `agy` process group and active voice calls, mark `running` runs as `interrupted`.

### 4.5 Configuration

All settings are environment variables with `GTM_` prefix, loaded via `.env` (never committed).

| Variable | Default | Purpose |
|---|---|---|
| `GTM_HOST` | `127.0.0.1` | Bind address |
| `GTM_PORT` | `8765` | Port |
| `GTM_DB_PATH` | `~/.local/share/gtm/gtm.db` | SQLite file |
| `GTM_TIMEZONE` | system tz (`Asia/Jakarta` here) | Display and date parsing |
| `GTM_WORK_START` | `09:00` | Day planning |
| `GTM_WORK_END` | `17:00` | Day planning |
| `GTM_AI_ENABLED` | `true` | Kill switch |
| `GTM_AGY_BIN` | `agy` | Binary path/name |
| `GTM_AGY_MODEL` | `gemini-3.8-flash-low` | Parsing model |
| `GTM_AGY_EFFORT` | `low` | Parsing effort |
| `GTM_AGY_TIMEOUT_SECONDS` | `120` | Hard app-level timeout |
| `GTM_AGY_PRINT_TIMEOUT` | `90s` | Passed to `agy` |
| `GTM_AGY_WORKSPACE` | `~/.local/share/gtm/agy-workspace` | Empty cwd for `agy` |
| `GTM_AI_MAX_CONCURRENCY` | `1` | AI worker semaphore |
| `GTM_SECRET_KEY` | empty | HMAC key for device tokens/pairing; required for pairing even on loopback; changing it revokes all devices |
| `GTM_PUBLIC_ORIGIN` | `http://127.0.0.1:8765` | Trusted origin for Origin/CSRF checks; set to the Tailscale HTTPS origin for phone access |
| `GTM_TRUST_PROXY_HEADERS` | `true` behind Tailscale | Honor `X-Forwarded-Proto/Host` from the local proxy |
| `GTM_PAIRING_TTL_SECONDS` | `300` | Pairing code lifetime |
| `GTM_VOICE_ENABLED` | `true` | Voice endpoint kill switch |
| `GTM_VOICE_PROVIDER` | `gemini` | `gemini` (fast) or `agy` (slow fallback) |
| `GTM_GEMINI_API_KEY` | empty | Gemini API key for text/voice interpretation; never logged |
| `GTM_GEMINI_VOICE_MODEL` | `gemini-3.8-flash` | Structured-output text model for voice transcripts |
| `GTM_VOICE_INPUT` | `transcript` | `transcript` (device STT) or `audio` (v1.1 cloud fallback) |
| `GTM_VOICE_AUDIO_FALLBACK` | `false` | Enable cloud WAV fallback in v1.1 only |
| `GTM_VOICE_MAX_SECONDS` | `60` | Max utterance duration |
| `GTM_VOICE_MAX_CHARS` | `4000` | Max transcript length |
| `GTM_VOICE_TIMEOUT_SECONDS` | `20` | Hard interpretation timeout |
| `GTM_AUTH_TOKEN` | empty | Optional static token for CLI/diagnostics; pairing is preferred |
| `GTM_LOG_LEVEL` | `INFO` | Logging |

Generate the secret once and put it in `backend/.env` (never committed):

```bash
../.venv/bin/python -c "import secrets; print(secrets.token_urlsafe(48))"
```

### 4.6 Key request flows

**Web manual CRUD**
`HTMX form → FastAPI → TaskService → repository → SQLite → partial/JSON → DOM swap`

**Android manual CRUD**
`Compose screen → Retrofit (Bearer token) → FastAPI → TaskService → SQLite → JSON → StateFlow → recomposition`

**Pairing**
`Web Settings → POST /api/auth/pairing-code → six-digit code → Android POST /api/auth/pair → devices row + token (returned once) → Keystore storage`

**AI interpret (text)**
`POST /api/ai/interpret → create ai_run(queued) → 202 → worker → AgyClient → envelope validation → Pydantic → action validation → ai_proposal(pending) → UI preview`

**Voice interpret (Android)**
`SpeechRecognizer transcript → POST /api/voice/interpret (JSON, sync, 20s cap) → validate text/duration → GeminiClient (3.8 Flash text, schema) → Pydantic + action validation → ai_run/ai_proposal → transcript + preview + assistant_message → TTS + capture card/proposal sheet`

**Apply proposal**
`POST /api/ai/proposals/{id}/apply → load proposal → re-validate against current DB → transaction → task changes + events → proposal applied → updated views`

Re-validation on apply is mandatory: the DB may have changed while the user was reviewing. Android uses the same endpoint and never applies locally.

### 4.7 Dependency and ownership boundaries

- `backend/app/repositories/` is the only layer that talks to SQLAlchemy.
- `backend/app/services/` owns business rules and transactions; it never shells out.
- `backend/app/ai/` owns subprocess/Gemini invocation, prompts, schemas, parsing; it never writes to the DB.
- `backend/app/api/` owns HTTP validation, auth dependencies, and rendering; no business logic.
- `backend/app/web/` owns templates/static assets; no business logic.
- `backend/app/workers/` owns text-AI job lifecycle; one worker, one semaphore.
- `mobile/` owns Android UI, local token storage, speech recognition control, and TTS; it never owns business rules or SQLite. Only the transcript crosses the API boundary.
- `models.py` is shared; schema changes go through Alembic only.
- **Auth boundary:** the web UI trusts the Tailscale perimeter plus Origin/`X-GTM-Request` checks (no separate web login in v1); Android uses a bearer device token. Both resolve to the same single user. A web password/session is a deliberate v1.1 option, not a hidden assumption.

---

## 5. Data model (summary)

Full DDL, indexes, FTS triggers, and query patterns are in [`DATA_MODEL.md`](DATA_MODEL.md).

Core tables:

| Table | Purpose |
|---|---|
| `tasks` | Task records, hierarchy, scheduling, optimistic `version` |
| `projects` | Grouping and color |
| `tags` / `task_tags` | Many-to-many labels |
| `task_events` | Audit trail for manual and AI changes |
| `ai_runs` | Every AI invocation, envelope, usage, error |
| `ai_proposals` | Reviewable action sets and their lifecycle |
| `settings` | Runtime preferences (timezone, work hours, model) |
| `devices` | Paired Android devices, HMAC token hashes, last-seen, revocation |
| `pairing_codes` | Short-lived, single-use pairing codes (hashed) |

Key invariants:

- IDs are server-generated UUIDv7 strings; AI never supplies IDs.
- Timestamps are UTC ISO 8601; date-only tasks use `due_all_day = 1` and local-midnight conversion.
- AI can only **archive** tasks; hard delete is a manual, typed-confirmation action.
- Every applied proposal writes `task_events` in the same transaction.
- Updates use a `version` column for optimistic concurrency; Android and web can edit the same task and receive `409`.
- Device tokens are stored only as HMAC hashes; the plaintext token is returned once at pairing.
- Voice runs store `speech_ms` and a transcript; no audio is uploaded in v1.
- Mobile is a thin client: no SQLite access, no local proposal application.

---

## 6. AI design (summary)

Full contract, measured behavior, prompt, and schema are in [`AI_INTEGRATION.md`](AI_INTEGRATION.md).

### 6.1 Invocation

`agy` is called as a subprocess, never via `shell=True`:

```bash
agy -p "$PROMPT" \
  --output-format json \
  --json-schema "$SCHEMA_PATH" \
  --model gemini-3.8-flash-low \
  --effort low \
  --print-timeout 90s \
  --sandbox \
  --disable-slash-commands
```

Run with:

- `cwd` = an **empty** runtime workspace, not the repo and not `$HOME`.
- Minimal environment allowlist (`HOME`, `PATH`, `LANG`, `TZ`); no API keys.
- `start_new_session=True`; kill the process group on timeout.
- One concurrent call (`asyncio.Semaphore(1)`).
- Hard timeout in the app, independent of `--print-timeout`.

Never use `--dangerously-skip-permissions` or `--add-dir`.

### 6.2 Structured output

`--json-schema` returns `structured_output` in the JSON envelope. The app reads **only** `structured_output`, validates it with Pydantic (`extra="forbid"`), and treats `response` as untrusted text.

A `SUCCESS` envelope is not enough. Treat as failure when:

- `status != "SUCCESS"`
- `structured_output` is missing
- `denied_actions` is non-empty
- `response` is empty
- Pydantic validation fails
- the app-level timeout fires

### 6.3 Date handling

The prompt includes:

- current datetime with offset
- weekday
- timezone
- a 21-day reference table (`2026-10-22 Thursday`, …)
- explicit instruction to resolve relative dates only from that table and not use tools

This avoids the observed failure where the agent tried to run `date`, was denied, and returned an empty response.

### 6.4 Action contract

The AI returns an envelope with `intent`, `assistant_message`, `actions[]`, `requires_confirmation`, and `confidence`.

Supported actions:

`create_task`, `update_task`, `complete_task`, `archive_task`, `query_tasks`, `plan_day`, `create_subtasks`, `summarize`, `unknown`.

Server-side rules:

- `project_id` must reference an existing project.
- `task_id` must reference an existing, non-archived task.
- `parent_id` cannot be the task itself; max depth 3.
- Max 20 actions per proposal; max 10 subtasks.
- Destructive actions always require confirmation.
- `plan_day` must fit work hours, avoid overlaps, and use existing tasks.
- `query_tasks` is executed by the server; the model does not get the whole database.

### 6.5 Confirmation and apply

- Low-risk creates/updates: one **Apply** click.
- Any archive, bulk update (>3 tasks), or status change to done in bulk: explicit confirmation with a summary.
- Hard delete never comes from AI.
- Apply is transactional and idempotent (`proposal_id` + `applied_at`).
- If a task version changed since the proposal, abort with “This task changed; review again.”
- Undo (Phase 3) applies inverse events for the last AI proposal within 10 minutes.

### 6.6 Latency and cost

Measured on this machine (agy 1.3.6; the installed 1.4.5 must be re-baselined in Phase 0):

| Configuration | Result | Wall time |
|---|---|---|
| Flash High, date hint, schema | correct `structured_output` | ~45 s |
| Flash Low, effort low, date hint, schema | correct `structured_output` | ~9 s |
| Flash Low, effort low, 21-day date table | correct `structured_output` | ~34 s |

Use Flash Low for parsing; reserve Medium/High for planning and breakdown. Track token usage in `ai_runs`; show it in settings. Cache identical `(text, context_hash)` interpretations for 10 minutes to avoid duplicate spend.

### 6.7 Privacy

Task text and context are sent to Google through `agy`. For voice, the phone's speech service produces the transcript; **only the transcript** is sent to the backend/Gemini by default. Audio is not uploaded by GTM. The system recognizer may use on-device or Google-hosted recognition depending on the device; Settings must say “on-device when available”, not claim universal on-device processing. Cloud audio fallback is v1.1, off by default. AI can be disabled entirely; no telemetry is added.

### 6.8 Voice path (Android)

The reference design is speech-first, with a dark stage, orb, state pill, waveform, live transcript, capture card, and explicit permission/error states. GTM keeps that interaction but uses deterministic server-side application:

- Android `SpeechRecognizer` (on-device when available) produces partial/final text. Tap starts; tap again or pause ~10 s to finish; 60 s cap.
- `POST /api/voice/interpret` receives **JSON transcript** (not audio), validates length/duration, and calls **Gemini 3.8 Flash text** with the shared JSON schema, or `agy` as fallback.
- The server runs the same Pydantic + cross-field validation, creates a pending proposal, and returns the transcript, assistant message, and preview synchronously (20 s hard timeout, one concurrent request).
- Simple creates show a **capture card** (Add to tasks / Discard); updates, archives, multi-actions, and low-confidence results open a **proposal sheet** with diffs. Apply is a server call; destructive actions require an explicit tap.
- Optional short `TextToSpeech` confirmation after apply; muted in Settings. This is not a chat reply.
- If recognition is unavailable, typed input remains; if Gemini is unavailable, the transcript can use `agy` (“slow mode”).
- Cloud audio fallback (WAV → Gemini 3.8 Flash audio) is **v1.1**, off by default, with explicit consent. It is not part of v1.
- Gemini Live (full duplex) is **v1.1**, not v1.

Design mapping: “Sprint 7” → Today, story points → `estimate_minutes`, epic → project, Review column → `waiting`; vectors, assignees, and cloud models are dropped. Full Android spec: [MOBILE.md](MOBILE.md).

---

## 7. Security and safety

- **Localhost + private TLS front door.** Refuse to start with pairing enabled and an empty `GTM_SECRET_KEY`; refuse non-loopback bind without a trusted `GTM_PUBLIC_ORIGIN`. The supported deployment is loopback behind Tailscale Serve HTTPS; no direct public bind.
- **Device auth.** Android pairs with a six-digit code (5 min, one use) and receives a bearer token once. The server stores only an HMAC-SHA256 hash and a short prefix. Tokens are revocable; `401` clears the client token, `403` means revoked. Pairing attempts are rate-limited.
- **Transport.** HTTPS is required outside `adb reverse`/localhost. Release Android builds disable cleartext. Web uses the Tailscale perimeter + Origin checks; Android uses `Authorization: Bearer`.
- **Web perimeter.** There is no app-level web login in v1: only devices on the tailnet can reach the UI. Use Tailscale ACLs/device approval; do not share the tailnet. A web password/session is a v1.1 option if the UI ever leaves the tailnet.
- **No browser CORS.** Reject state-changing web requests whose `Origin` is not `GTM_PUBLIC_ORIGIN`. HTMX sends `HX-Request`; JSON mutations also require `X-GTM-Request: 1`. Native Android is not subject to CORS but must present a device token.
- **CSRF:** with no auth cookie in v1, Origin + custom-header checks are the CSRF boundary. If a web session is added later, add SameSite=Lax and a CSRF token at the same time.
- **XSS:** Jinja autoescape; Markdown rendered through `markdown-it-py` + `nh3` sanitization; never inject AI HTML. Android renders plain text only.
- **SQL injection:** SQLAlchemy parameter binding; no string-built SQL from user input.
- **Prompt injection:** user text/transcript is JSON-encoded and labeled as data; schema constrains output; deterministic validation; confirmation before apply; destructive actions always reviewed.
- **Agent containment:** empty cwd, no `--add-dir`, `--sandbox`, no permission-skipping, minimal env, hard timeout, output size cap (e.g. 1 MB). `agy` is never exposed to the phone.
- **Voice transcript safety:** cap 60 s / 4,000 chars, validate JSON and language fields, reject malformed/oversized input, never log the transcript by default. No audio reaches the backend in v1. One concurrent voice request. Cloud audio fallback (v1.1) would add MIME/size/duration validation and temp-file deletion.
- **Secrets:** never log env values, auth tokens, pairing codes, API keys, or full prompts by default; `ai_runs.prompt_text` is opt-in debug. Android stores the token in Keystore-backed storage and never logs it.
- **Rate limits:** one concurrent text AI run and one voice run; max 10 interpretations/minute; max 5 pairing attempts/5 minutes; return `429` with retry-after.
- **Backups:** `backend/scripts/backup.sh` uses `sqlite3 .backup`; run before every migration; keep 7 daily copies. Revoked devices are excluded from export.
- **Export:** JSON export includes projects, tasks, tags, and task events; no devices, pairing codes, tokens, or secrets.

---

## 8. Testing and quality

### 8.1 Test pyramid

| Level | What | Tooling |
|---|---|---|
| Unit (backend) | Date normalization, action validation, diffing, repositories, auth/token hashing | pytest |
| API (backend) | Task CRUD, filters, proposal apply/reject, pairing/revoke, voice transcript JSON, usage, errors | pytest + httpx ASGI |
| UI (web) | Critical flows (manual CRUD, AI preview/apply) | Playwright (optional, phase 6) |
| Contract (text) | Real `agy` envelope, schema, latency canary | pytest marker `agy`, opt-in |
| Contract (voice) | Real Gemini 3.8 Flash text + schema from a fixed transcript | pytest marker `gemini`, opt-in |
| Prompt safety | Injection strings, unicode, huge input, relative dates | pytest |
| Android unit | ViewModels, repository mapping, short-key display, transcript validation, error mapping | JUnit 4 + MockWebServer |
| Android UI | Speak states, board/list, detail sheet, system panels, pairing | Compose UI test |
| Android device | Install/pair/CRUD/voice on the Infinix; permission, font scale, dark theme | instrumented + manual |

The default test suites **never call `agy` or Gemini**. `FakeAgyClient` and `FakeGeminiClient` return fixtures from `backend/tests/fixtures/`. Android uses MockWebServer.

### 8.2 Commands

```bash
# backend setup
cd backend
../.venv/bin/pip install -e ".[dev]"

# run
../.venv/bin/python -m app
# or
../.venv/bin/uvicorn app.main:app --reload --port 8765

# quality gates (in this order)
../.venv/bin/ruff format --check .
../.venv/bin/ruff check .
../.venv/bin/pytest -q

# focused test
../.venv/bin/pytest tests/unit/test_actions.py::test_create_task -q

# real agy contract test (costs credits, requires auth)
RUN_AGY_CONTRACT=1 ../.venv/bin/pytest tests/contract/test_agy_contract.py -q -s

# migrations
../.venv/bin/alembic upgrade head
../.venv/bin/alembic revision --autogenerate -m "add ..."

# Android
cd ../mobile
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb reverse tcp:8765 tcp:8765
```

### 8.3 Contract test

`backend/tests/contract/test_agy_contract.py`:

1. asserts `agy --version` is ≥ 1.3.6; the currently installed binary is 1.4.5 and must be re-baselined before Phase 3;
2. runs one trivial schema-constrained prompt with a 120s timeout;
3. asserts `status == SUCCESS`, `structured_output` is present, and no `denied_actions`;
4. records the envelope to `backend/tests/fixtures/agy_envelope_success.json` (redacted) for unit tests.

Skipped by default. Run it after any `agy` update. A similar opt-in test for the Gemini transcript path records a redacted schema response.

### 8.4 CI (phase 6)

GitHub Actions, two jobs:

**Backend** (Python 3.14, working directory `backend/`):

```bash
pip install -e ".[dev]"
ruff format --check .
ruff check .
pytest -q
```

**Android** (JDK 21, Android SDK 36, working directory `mobile/`):

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

No real `agy`/Gemini call in CI (no cached auth/credits). Optionally add a manual `workflow_dispatch` contract job with a secret. Instrumented tests stay local on the Infinix.

### 8.5 Definition of done for any feature

- Tests cover happy path and at least one failure path.
- `ruff format --check`, `ruff check`, `pytest` pass for backend changes.
- `./gradlew :app:assembleDebug :app:testDebugUnitTest` passes for Android changes.
- No secrets in logs or fixtures.
- Docs updated if behavior or commands changed.
- AGENTS.md updated if the command surface changed.

---

## 9. Delivery plan

Estimates are focused solo days for one developer using AI assistance. They assume Phase 0 decisions are closed and the plan is approved.

### Phase 0 — Spike and decisions (0.5 day) — mostly done

**Deliverables**

- [x] Install/verify `agy` (1.3.6) and model availability.
- [x] Verify JSON envelope, `structured_output`, `--json-schema`, timeout behavior.
- [x] Measure latency and failure modes.
- [x] Choose backend stack, storage, and the backend/mobile repository split.
- [x] Write the plan, AI contract, data model, and Android client docs.
- [ ] Re-baseline `agy` on the installed **1.4.5** (flags, envelope, model slugs, latency).
- [ ] Confirm Android package name, min/target SDK, voice provider, design reference binding, and Gemini API key availability.
- [ ] Confirm the 3-tab Speak/Tasks/System shell and transcript-only voice default.

**Exit criteria:** contract revalidated or documented as blocked; no open architectural blockers; user approval to execute.

### Phase 1 — Backend foundation (2–3 days)

**Goal:** a tested task store and JSON API under `backend/`, with no feature UI yet.

- [ ] Create `backend/pyproject.toml` with runtime/dev deps, pytest, ruff, and package config.
- [ ] Create `backend/app/`: `config.py`, `db.py`, `main.py`, `__main__.py`.
- [ ] SQLAlchemy models: tasks, projects, tags, events, AI runs/proposals, settings, devices, pairing codes.
- [ ] Alembic init + `0001` migration including FTS5 tables/triggers and device-auth tables.
- [ ] Repositories + TaskService: create, update, complete, reopen, archive, reorder, filters, optimistic `version`.
- [ ] `/api/health`, task/project CRUD, search, error envelope.
- [ ] Unit/API tests; seed script; base template + CSS tokens.
- [ ] Generate and document `GTM_SECRET_KEY`; app refuses to start with pairing enabled and no secret.
- [ ] Remove the root `main.py` stub.

**Exit criteria:** `pytest` green; CRUD via JSON works; DB survives restart; `alembic upgrade head` on a fresh file.

### Phase 2 — Web UI (3–4 days)

**Goal:** manage tasks comfortably without AI, on desktop and narrow browser widths.

- [ ] Pages: Today, Inbox, Upcoming, Anytime, Waiting, Projects, Done, Search.
- [ ] Task list/row/partials, detail drawer, inline edit, quick add.
- [ ] Complete/archive/reopen, drag reorder, toasts.
- [ ] Projects and tags management.
- [ ] Keyboard shortcuts, focus management, empty states.
- [ ] Accessibility pass: labels, contrast, focus traps, reduced motion.
- [ ] HTMX tests for key partials; API tests for filters.
- [ ] Pairing-code UI in Settings (the Android client will consume it in Phase 4).

**Exit criteria:** full manual workflow on desktop; no full-page reload for CRUD; usable at 1280/1024/768; web app still works with AI disabled.

### Phase 3 — AI command bar and planning (5–7 days)

**Goal:** text-based natural language with safe preview/apply, plus the planning features.

- [ ] `AgyClient` protocol + subprocess implementation + tests with a fake.
- [ ] Prompt builder with date table, projects, selected task, injection defenses.
- [ ] Shared JSON schema + Pydantic models + cross-field validation.
- [ ] `ai_runs` / `ai_proposals` repositories and lifecycle.
- [ ] In-process worker: queue, semaphore, cancel, timeout, restart recovery.
- [ ] `POST /api/ai/interpret` (202), status polling, optional SSE.
- [ ] Proposal preview/apply/reject endpoints; transactional apply; audit events.
- [ ] Command bar + preview card + error/retry states.
- [ ] `plan_day`, `create_subtasks`, `query_tasks`/summary, ambiguous-match UI, undo window.
- [ ] Interpretation cache, rate limiting, usage display, AI health panel.
- [ ] `/api/usage` aggregation (tokens/cost/storage) for the Android System tab.
- [ ] Contract test on `agy` 1.4.5; prompt snapshot and injection tests.

**Exit criteria:** 10 scripted utterances produce valid proposals; no API request waits >1s; apply is transactional; destructive actions require confirmation; `agy` failure leaves manual app usable; contract test passes on the installed version.

### Phase 4 — Android foundation (6–8 days)

**Goal:** a native Android client that pairs, reads, and edits the same tasks, built around the reference's 3-tab shell.

- [ ] `mobile/` Gradle project (Kotlin DSL, version catalog, wrapper, `local.properties` ignored); pin AGP/Gradle/Kotlin after the first build.
- [ ] Compose theme/tokens (light/dark + dark speech stage), typography, spacing, motion; 3-tab shell.
- [ ] App container, Retrofit/OkHttp, kotlinx.serialization DTOs; pairing + Keystore token; auth interceptor.
- [ ] `/api/mobile/bootstrap`, `/api/usage`; board/list state; optimistic `version` conflict handling.
- [ ] Speak screen shell: orb, state pill, waveform, transcript area, capture card, permission/error states (without live recognizer yet).
- [ ] Tasks: Board (To do/In progress/Waiting/Done) + List (project groups, All/Today/Overdue chips), quick add, search sheet.
- [ ] Task detail sheet: metadata, checklist/subtasks, notes, move-to, archive.
- [ ] System: stats, usage chart, storage, engine/device, backup/rebuild/unpair actions; maintenance endpoints.
- [ ] Loading/empty/error/offline/unpaired/401/403/409 states; back/rotation/safe areas; 200% font scale.
- [ ] Unit tests (MockWebServer), Compose UI tests; install debug APK on the Infinix.
- [ ] Debug networking: `adb reverse tcp:8765 tcp:8765`; cleartext only for localhost in debug.

**Exit criteria:** APK installs on the Infinix; pairing works; board/list/detail/system render with real data; CRUD and 409 work; a revoked token returns to pairing; process death/rotation does not lose state; unit/UI tests green.

### Phase 5 — Voice (3–5 days)

**Goal:** the reference's tap-to-talk capture, backed by device transcription and safe server application.

- [ ] Backend `VoiceService`: transcript validation (1–4,000 chars, 60 s), single semaphore, 20 s timeout; no audio endpoint.
- [ ] `GeminiClient` text mode + shared schema; `ai_runs.kind='voice_interpret'`; `speech_ms`; transcript stored; rate limiting.
- [ ] `POST /api/voice/interpret` JSON endpoint; fake client tests; usage counters.
- [ ] Android `SpeechRecognizer` controller: on-device preference, partial results, 10 s silence auto-finish, 60 s cap, cancel, permission flow.
- [ ] Speak states (idle/connecting/listening/captured/error), live transcript, waveform; capture card for creates.
- [ ] Proposal sheet with transcript/diff for updates/archives/low confidence; destructive actions require a tap.
- [ ] Optional `TextToSpeech` confirmation, locale setting, mute/stop.
- [ ] Fallbacks: typed input when recognition is unavailable; `agy` when Gemini is unavailable (“slow mode”).
- [ ] 20 scripted utterances on the device; p50/p95; noisy-room, silence, denial, 401/403 tests.

**Exit criteria:** 20 utterances yield ≥95% valid proposals; p50 <4 s and p95 <8 s on the phone; no unconfirmed destructive change; permission denial leaves text CRUD usable; no audio is uploaded or stored.

### Phase 6 — Hardening and release (3–5 days)

**Goal:** safe, documented, reproducible, and installable.

- [ ] Security review: pairing/revocation, token storage, Origin/CSRF, TLS assumptions, transcript validation, env stripping, prompt injection.
- [ ] Export/import JSON with validation; backup/restore script; migration backup check.
- [ ] systemd user unit with `WorkingDirectory=backend/`; Tailscale Serve runbook.
- [ ] README with backend + Android setup, pairing, voice, troubleshooting.
- [ ] CI: backend job (ruff + pytest) and Android job (assembleDebug + unit tests + lint).
- [ ] Release APK signing (keystore outside the repo); versionCode/versionName.
- [ ] Update `AGENTS.md` with real commands and architecture.
- [ ] Tag `v0.1.0` on `master` after merging through `development`.

**Exit criteria:** fresh clone setup <15 min; backend CI green; Android CI green; release APK installs; pairing + voice + backup/restore verified; no high-severity security findings.

### Total

**22–32 focused days**, plus a 25–30% buffer → **27–40**. The backend/web/text-AI portion is 13–19; the Android design system/shell/board/system plus voice adds 9–13. At 2–3 evenings per week, double or triple the calendar time.

---

## 10. Risks and mitigations

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| `agy` tries tools and returns empty `SUCCESS` | High | High | Date table, no-tools prompt, `denied_actions` detection, one retry, manual fallback |
| `agy` latency spikes / timeouts | Medium | Medium | Flash Low, async jobs, 120s hard timeout, cache, progress UI |
| `agy` CLI flags/models change | Medium | High | Pin minimum version, startup health check, contract test, config not hardcoded; docs were verified on 1.3.6 but 1.4.5 is installed |
| Auth/credits unavailable | Medium | Medium | Clear setup message, AI kill switch, manual app unaffected |
| Gemini API key/billing unavailable | Medium | Medium | Voice disabled cleanly; device transcript + `agy` slow mode; typed input remains |
| Android Gradle/AGP setup without Android Studio | Medium | High | Pin wrapper + versions; build `assembleDebug` on day one; use SDK 36; keep `local.properties` local |
| Android target/permission changes | Medium | Medium | Target SDK 36; no background mic; test on the physical Infinix; explicit permission UX |
| Pairing/token loss or revocation loop | Medium | Medium | Clear token on 401; explicit re-pair; web revoke; rate-limited pairing |
| Phone cannot reach host (sleep, Tailscale/WARP) | High | High | Always-on x86 host; Tailscale Serve; test WARP coexistence; clear offline banner |
| Voice latency/quality on device | Medium | High | Tap-to-talk + Gemini text; 60 s cap; progress states; Live deferred; measure p50/p95 on the phone |
| Device speech recognizer unavailable/inaccurate | Medium | High | Prefer on-device recognizer; typed fallback; cloud audio v1.1; test Indonesian/English |
| Design fidelity/visual scope | High | Medium | Token/component first; screenshot-test three tabs; defer nonessential polish; reference is inspiration, not pixel contract |
| Compose board/sheet complexity | Medium | Medium | Snap LazyRow + ModalBottomSheet; fall back to List if board slips |
| System screen scope (charts/storage) | Medium | Low | Ship stats/usage/storage basics; chart can be a simple 7-bar view |
| Sprints/vectors/teams creep from reference | High | Medium | Explicit mapping to projects/statuses/FTS; no new entities |
| Cleartext/self-signed TLS mistakes | Medium | High | `adb reverse` for debug; Tailscale Serve for release; release refuses cleartext |
| Mobile scope creep (offline, notifications) | High | Medium | Explicit non-goals; online-first v1; phase gate before v1.1 |
| Structured output missing/invalid | Medium | High | Pydantic validation, `response` fallback parse, no apply on failure |
| Prompt injection leads to bad proposal | Medium | High | Schema, server-side validation, confirmation, no tools, no destructive auto-apply |
| SQLite write contention | Low | Medium | WAL, single process, short transactions, busy_timeout |
| Python 3.14 dependency issues | Low | Medium | Validate install in phase 1; fall back to 3.13 only if a blocker appears |
| Scope creep into integrations | High | Medium | Explicit non-goals; phase gates |
| Data loss/migration bug | Low | High | Backups, Alembic, restore test |
| Timezone bugs | Medium | Medium | UTC storage, one configured tz, tests around midnight/DST |

---

## 11. Observability

- Structured JSON logs with `request_id`, `device_id`, `run_id`, `proposal_id`, duration, error code.
- `ai_runs` is the audit source of truth: prompt hash, context keys, envelope, usage, error, latency; voice runs add `speech_ms` and transcript.
- `/api/health`: app status, DB writable, schema version, `agy --version`, AI/voice enabled, pairing availability. Cache the `agy` version check for 5 minutes.
- `/api/usage?days=7`: token/cost/storage summary from `ai_runs` for the System tab; aggregated only, no raw transcript text.
- Settings page: last 20 AI runs with status, latency, tokens, error code; paired devices; voice status; “copy diagnostics” (redacted).
- Android diagnostics: base URL, app version, last error/request ID; no token, audio, or prompt content.
- No external telemetry and no crash-reporting SDK in v1.

---

## 12. Assumptions and open questions

**Assumptions if unanswered**

- Single user; backend loopback behind Tailscale Serve; English UI, with Indonesian TTS/voice where available.
- Android package `dev.antinormies.gtm`, minSdk 31, compile/target SDK 36 (pin after Phase 4 check).
- Timezone defaults to the system (`Asia/Jakarta` here); work hours 09:00–17:00.
- Tasks are personal/project work; no calendar sync in v1.
- Archive, not hard delete, by default.
- AI is allowed to read task text and project names sent in the prompt; for voice, only the transcript (not audio) is sent to Gemini by default.
- Android is online-first; no offline writes in v1.
- The web UI has no separate login in v1; the Tailscale tailnet is the perimeter. A web session/password is deferred.
- Host is expected to stay awake for phone access; otherwise an always-on x86 mini PC/VPS is in scope.

**Questions to confirm before execution**

1. Android package name and app label?
2. Is minSdk 31 / target 36 acceptable?
3. English-only UI, or English + Indonesian from day one?
4. Is a Gemini API key acceptable for voice interpretation, with `agy` as fallback?
5. Sideload-only release, or prepare for the Play Store?
6. Is the host always on, or should the plan include an always-on mini PC/VPS?
7. What are your real work hours and default day-planning window?
8. Do you want recurring tasks/reminders in v1, or explicitly later?
9. Should AI create projects/tags implicitly, or only use existing ones?
10. Do you want an “Eisenhower” importance flag in addition to priority?
11. Any need to import from Todoist/Google Tasks/Notion at launch?
12. Is `docs/reference.html` ("Echo — voice task manager") the binding visual direction, or inspiration? The plan currently treats it as a design reference and adapts it.
13. Is the 3-tab **Speak / Tasks / System** shell acceptable, with Board columns To do / In progress / Waiting / Done?
14. Is device speech recognition (transcript-only) acceptable as the default, with cloud audio deferred to v1.1?
15. Should TTS be on by default or muted by default?
16. Preferred visual direction beyond the reference (e.g., dark-first, different accent)?

Items 1–6 block the scaffold; the rest can be answered during Phase 1–3.

---

## 13. Decision record (summary)

| # | Decision | Why | Alternative rejected |
|---|---|---|---|
| 1 | Local web UI | Rich views, keyboard + mouse, easy AI preview | CLI/TUI (less approachable) |
| 2 | SQLite | Single user, zero ops, FTS5, easy backup | Postgres (overkill) |
| 3 | `agy` headless subprocess for text | Uses existing Antigravity auth/credits; no extra text bill | Direct Gemini API for everything (new billing/auth) |
| 4 | Server-rendered HTMX | No Node build; fast iteration | React SPA (complexity) |
| 5 | AI proposes, server applies | Trust, auditability, injection safety | AI writes directly to DB |
| 6 | Flash Low for text parsing | ~9s vs ~45s measured | Flash High for everything |
| 7 | In-process worker | One user; no Redis | Celery/RQ |
| 8 | Archive instead of delete | Recoverable; safer AI | Hard delete |
| 9 | Empty cwd + sandbox | Limits agent file access | Run in repo/`$HOME` |
| 10 | One uvicorn worker | In-memory queue correctness | Multiple workers |
| 11 | `backend/` + `mobile/` split | One backend contract; native client independent of web rendering | Single mixed root (unclear ownership) |
| 12 | Native Kotlin/Compose Android | Reliable mic/TTS, background path later, no PWA limits | PWA/WebView (mic/background quirks) |
| 13 | Pairing code + bearer token | No account system; revocable; works over Tailscale | Static shared token (poor UX/revocation) |
| 14 | Device speech recognition → Gemini 3.8 Flash text for voice | Privacy: only transcript leaves phone; structured output; 1–4s range; same schema | `agy` for voice (9–34s, text-out); cloud audio default (privacy) |
| 15 | Tailscale Serve HTTPS | Automatic TLS, private tailnet, no public ingress | Public bind + manual TLS |
| 16 | Online-first Android v1 | Smaller, reliable scope | Offline sync engine (defer) |
| 17 | 3-tab Speak / Tasks / System shell | Matches reference's focus; voice is primary; GTD remains as filters | 4-tab GTD + mic FAB (less focused) |
| 18 | Board/List task surface | Reference's board + plan's list/GTD data; one screen | Separate GTD screens (fragmented) |
| 19 | On-device/transcript-only voice default | Reference's privacy promise; smaller backend; no audio handling | Gemini audio upload (better accuracy, worse privacy) |
| 20 | Design tokens/screens from reference | Cohesive, accessible, high-quality UI | Generic Material defaults (weaker identity) |

---

## 14. Immediate next steps

1. **User reviews and approves this revised plan.** No implementation starts before that.
2. After approval: commit the docs on `feature/project-setup`, merge through `development`, and create `feature/foundation`.
3. Re-baseline `agy` 1.4.5; update `AI_INTEGRATION.md` with the result.
4. Phase 1: create `backend/`, models/migration (including devices/pairing), CRUD, tests.
5. Phase 2: web UI + pairing-code screen.
6. Phase 3: text AI, proposals, planning features.
7. Phase 4: `mobile/` scaffold, 3-tab shell, pairing, Board/List/detail/System, read/write tasks on the Infinix.
8. Phase 5: Speak screen, device STT, transcript → Gemini Flash text → capture card/proposal → optional TTS.
9. Phase 6: hardening, CI, release APK, tag `v0.1.0`.

The root `main.py` remains untouched until Phase 1 starts.

---

## Appendix A — API surface (planned)

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/health` | App/DB/AI/voice health |
| POST | `/api/auth/pairing-code` | Web UI (tailnet + Origin/header protected): create a 6-digit pairing code |
| POST | `/api/auth/pair` | Android: exchange code for a device token |
| GET | `/api/auth/me` | Android: validate token, device/server info |
| POST | `/api/auth/revoke` | Revoke the current device |
| GET | `/api/mobile/bootstrap` | Android: projects, counts, board/list data, server time, usage summary |
| GET | `/api/usage?days=7` | System: tokens/cost/storage summary |
| POST | `/api/maintenance/backup` | System: create a SQLite backup (returns path/size) |
| POST | `/api/maintenance/rebuild-search` | System: rebuild the FTS5 index |
| GET | `/api/tasks` | List with filters (`status`, `due`, `project`, `q`, `include_done`) |
| POST | `/api/tasks` | Create |
| GET | `/api/tasks/{id}` | Detail |
| PATCH | `/api/tasks/{id}` | Update (version check) |
| POST | `/api/tasks/{id}/complete` | Complete |
| POST | `/api/tasks/{id}/reopen` | Reopen |
| DELETE | `/api/tasks/{id}` | Archive |
| PUT | `/api/tasks/{id}/order` | Reorder |
| GET | `/api/projects` | List |
| POST | `/api/projects` | Create |
| PATCH | `/api/projects/{id}` | Update/archive |
| GET | `/api/search?q=` | FTS search |
| POST | `/api/ai/interpret` | Queue text interpretation; `202 {run_id}` |
| GET | `/api/ai/runs/{id}` | Run status + proposal |
| POST | `/api/voice/interpret` | Android transcript JSON → validated proposal (sync) |
| POST | `/api/ai/proposals/{id}/apply` | Apply |
| POST | `/api/ai/proposals/{id}/reject` | Reject |
| POST | `/api/ai/proposals/{id}/undo` | Undo (Phase 3) |
| GET | `/api/export` | JSON export |
| POST | `/api/import` | JSON import (Phase 6) |

## Appendix B — Error envelope

```json
{
  "error": {
    "code": "AGY_TIMEOUT",
    "message": "Gemini did not finish within 120 seconds.",
    "details": {"run_id": "01J...", "retryable": true}
  }
}
```

UI maps codes to actionable messages; never shows raw stderr unless the user opens diagnostics.

## Appendix C — Manual test script (v1 exit)

### Web / backend

1. Create a task manually; complete it; reopen it; archive it.
2. “Call the dentist next Tuesday at 3pm, high priority” → preview shows correct date/time; apply; verify DB.
3. “Move it to Friday” with the task selected → diff only due date.
4. “Plan my day, 4 hours” → blocks respect work hours, no overlap.
5. “Break ‘Launch landing page’ into steps” → 5–8 children, max depth respected.
6. “Mark the invoice done” with two similar tasks → candidate picker, no guess.
7. Stop `agy`/rename binary → command bar shows `AGY_NOT_FOUND`; manual CRUD still works.
8. Kill the app during an AI run → restart marks the run interrupted; no stuck spinner.
9. Apply the same proposal twice → second call is a no-op/idempotent.
10. Run the `agy` contract test → envelope and schema valid on 1.4.5.

### Android

11. Install the debug APK; launch; complete pairing with a web-generated code.
12. Force-stop/relaunch → token persists; Today shows the same tasks as web.
13. Create a task on Android; verify it on web; edit on web; refresh Android and see the change.
14. Edit the same task on both clients → one gets `409` and a review prompt, not silent overwrite.
15. Tap-to-talk “Move the report to Friday and make it urgent” → live transcript, correct diff; Apply; optional spoken confirmation.
16. Say “Archive the old draft” → proposal requires an explicit tap; cancel leaves the task intact.
17. Revoke the device on web → next Android request returns to pairing; no request loop.
18. Deny mic permission → voice disabled, manual CRUD still works.
19. Stop the backend / turn off Tailscale → offline banner + Retry; no local writes.
20. Run voice with Gemini key absent → “slow mode” fallback via `agy`.

## Appendix D — Dependency manifest (planned)

Runtime:

| Package | Use |
|---|---|
| `fastapi` | HTTP app, validation, OpenAPI |
| `uvicorn[standard]` | ASGI server + reload |
| `jinja2` | Templates |
| `sqlalchemy>=2.0` | ORM and transactions |
| `alembic` | Migrations |
| `pydantic>=2` | API/schema validation |
| `pydantic-settings` | Env config |
| `python-multipart` | HTML form parsing |
| `markdown-it-py` | Notes rendering |
| `nh3` | HTML sanitization |
| `platformdirs` | XDG data/config paths |
| `google-genai` | Gemini 3.8 Flash text client (voice transcripts) |

Development:

| Package | Use |
|---|---|
| `pytest` | Tests |
| `pytest-asyncio` | Async worker/client tests |
| `httpx` | ASGI test client |
| `ruff` | Lint + format |
| `playwright` (optional) | Phase 6 web UI smoke |
| `pytest-cov` (optional) | Coverage reporting |

Android (pinned in `mobile/gradle/libs.versions.toml`):

| Dependency | Use |
|---|---|
| Kotlin + Compose BOM + Material 3 | UI |
| AndroidX Activity Compose, Lifecycle, Navigation | App shell |
| Retrofit + OkHttp + kotlinx.serialization | API client |
| DataStore Preferences | Settings |
| Android Keystore / security-crypto | Token storage |
| Android SpeechRecognizer / TextToSpeech | Voice input/output (platform APIs) |
| JUnit 4, MockWebServer, Compose UI Test | Tests |
| Gradle wrapper + AGP | Build |

Pin exact Python versions in `backend/pyproject.toml` at Phase 1 after verifying they install on Python 3.14.4. Pin Android versions at Phase 4 after the first successful `assembleDebug`. `tzdata` is only needed if the host lacks the system timezone database.
