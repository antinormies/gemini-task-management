# Data Model — SQLite

**Status:** revised schema for Phase 1 — includes Android device auth and voice runs
**Database:** SQLite 3 (WAL mode, FTS5)
**Related:** [`PLAN.md`](PLAN.md) · [`MOBILE.md`](MOBILE.md) · [`AI_INTEGRATION.md`](AI_INTEGRATION.md)

---

## 1. Principles

1. **Local-first, single writer.** One user, one backend process, short transactions. Web and Android are both clients.
2. **Server-generated IDs.** UUIDv7 strings. AI never supplies primary keys.
3. **UTC storage, local display.** All timestamps are ISO 8601 UTC (`YYYY-MM-DDTHH:MM:SS.sssZ`). The UI converts using `GTM_TIMEZONE`.
4. **Archive, don't delete.** `archived_at` hides records; hard delete is a manual action with explicit confirmation.
5. **Audit everything that matters.** `task_events` records manual and AI changes in the same transaction.
6. **Optimistic concurrency.** `tasks.version` increments on every update; stale writes are rejected; Android receives `409`.
7. **AI output is untrusted.** `ai_runs` stores the raw envelope; `ai_proposals` stores the reviewed action set.
8. **Device tokens are hashed.** Only HMAC-SHA256 hashes are stored; plaintext tokens exist only on the phone.
9. **Mobile is a thin client.** No SQLite access, no local apply, no offline writes in v1.
10. **Migrations are code.** Alembic with `render_as_batch=True`; no ad-hoc `ALTER` in application code.

---

## 2. Entity relationships

```
projects 1──* tasks
tasks    1──* tasks            (parent_id, subtasks)
tasks    *──* tags             (task_tags)
tasks    1──* task_events
ai_runs  1──1 ai_proposals
ai_runs  1──* tasks            (tasks.ai_run_id, nullable)
devices  1──* ai_runs          (ai_runs.device_id, nullable — Android voice/text)
pairing_codes (short-lived, single-use)
settings (key/value)
```

---

## 3. Enumerations

### Task status

| Value | Meaning | Default view |
|---|---|---|
| `inbox` | Captured, not triaged | Inbox |
| `todo` | Committed, not started | Today/Upcoming/Anytime |
| `in_progress` | Actively being worked | Today/Anytime |
| `waiting` | Blocked on someone/something | Waiting |
| `done` | Completed | Done |
| `cancelled` | Intentionally dropped | Done (filter) |

Allowed transitions:

```
inbox → todo | cancelled
todo → in_progress | waiting | done | cancelled
in_progress → waiting | todo | done | cancelled
waiting → todo | in_progress | cancelled
done → todo            (reopen)
cancelled → inbox | todo (restore)
```

The service enforces transitions; the DB check constraint only enforces the enum.

### Priority

| Value | Rank |
|---|---|
| `urgent` | 0 |
| `high` | 1 |
| `medium` | 2 |
| `low` | 3 |
| `none` | 4 |

### Source

`manual`, `ai`, `import`.

### AI run status

`queued`, `running`, `succeeded`, `failed`, `canceled`.

### AI run kind

`interpret`, `voice_interpret`, `plan_day`, `breakdown`, `summarize`, `contract`.

Voice runs store the transcript in `input_text`, use the same envelope/proposal lifecycle, and never store audio. Device speech recognition is the default; only the transcript is sent to the backend.

### Proposal status

`pending`, `applied`, `rejected`, `expired`.

### Task event types

`created`, `updated`, `completed`, `reopened`, `archived`, `restored`, `reordered`, `proposal_applied`, `proposal_undone`.

---

## 4. DDL

> Target: SQLite 3.40+. Run the `PRAGMA` block on every new connection.
> Table order in this document is grouped by concern. The Alembic migration creates `devices` and `pairing_codes` before `ai_runs` so the nullable `ai_runs.device_id` foreign key resolves cleanly.

```sql
PRAGMA journal_mode = WAL;
PRAGMA synchronous = NORMAL;
PRAGMA foreign_keys = ON;
PRAGMA busy_timeout = 5000;
PRAGMA temp_store = MEMORY;

-- ---------------------------------------------------------------- projects
CREATE TABLE projects (
    id           TEXT PRIMARY KEY,
    name         TEXT NOT NULL COLLATE NOCASE UNIQUE,
    color        TEXT NOT NULL DEFAULT '#64748b',
    description  TEXT NOT NULL DEFAULT '',
    archived_at  TEXT,
    created_at   TEXT NOT NULL,
    updated_at   TEXT NOT NULL
);

-- ---------------------------------------------------------------- ai_runs
CREATE TABLE ai_runs (
    id                    TEXT PRIMARY KEY,
    request_id            TEXT NOT NULL UNIQUE,
    kind                  TEXT NOT NULL,
    status                TEXT NOT NULL,
    model                 TEXT NOT NULL,
    effort                TEXT,
    input_text            TEXT NOT NULL,        -- text prompt or voice transcript
    context_json          TEXT NOT NULL DEFAULT '{}',
    prompt_hash           TEXT,
    prompt_text           TEXT,                 -- opt-in debug only
    raw_envelope_json     TEXT,
    structured_output_json TEXT,
    error_code            TEXT,
    error_message         TEXT,
    duration_ms           INTEGER,
    speech_ms             INTEGER,              -- voice only; utterance duration; NULL for text
    device_id             TEXT REFERENCES devices(id) ON DELETE SET NULL,
    input_tokens          INTEGER,
    output_tokens         INTEGER,
    thinking_tokens       INTEGER,
    total_tokens          INTEGER,
    created_at            TEXT NOT NULL,
    started_at            TEXT,
    finished_at           TEXT,
    CHECK (status IN ('queued','running','succeeded','failed','canceled')),
    CHECK (kind IN ('interpret','voice_interpret','plan_day','breakdown','summarize','contract')),
    CHECK (speech_ms IS NULL OR speech_ms >= 0)
);

-- ---------------------------------------------------------------- tasks
CREATE TABLE tasks (
    id                 TEXT PRIMARY KEY,
    title              TEXT NOT NULL,
    description        TEXT NOT NULL DEFAULT '',
    status             TEXT NOT NULL DEFAULT 'inbox',
    priority           TEXT NOT NULL DEFAULT 'none',
    due_at             TEXT,                    -- UTC ISO 8601
    due_all_day        INTEGER NOT NULL DEFAULT 0,
    scheduled_start_at TEXT,                    -- UTC ISO 8601
    scheduled_end_at   TEXT,
    estimate_minutes   INTEGER,
    project_id         TEXT REFERENCES projects(id) ON DELETE SET NULL,
    parent_id          TEXT REFERENCES tasks(id) ON DELETE CASCADE,
    sort_order         REAL NOT NULL DEFAULT 0,
    source             TEXT NOT NULL DEFAULT 'manual',
    ai_run_id          TEXT REFERENCES ai_runs(id) ON DELETE SET NULL,
    version            INTEGER NOT NULL DEFAULT 1,
    created_at         TEXT NOT NULL,
    updated_at         TEXT NOT NULL,
    completed_at       TEXT,
    archived_at        TEXT,
    CHECK (status IN ('inbox','todo','in_progress','waiting','done','cancelled')),
    CHECK (priority IN ('none','low','medium','high','urgent')),
    CHECK (source IN ('manual','ai','import')),
    CHECK (due_all_day IN (0,1)),
    CHECK (estimate_minutes IS NULL OR estimate_minutes >= 0),
    CHECK (parent_id IS NULL OR parent_id <> id),
    CHECK (
        (scheduled_start_at IS NULL AND scheduled_end_at IS NULL)
        OR (scheduled_start_at IS NOT NULL AND scheduled_end_at IS NOT NULL
            AND scheduled_start_at < scheduled_end_at)
    )
);

-- ---------------------------------------------------------------- tags
CREATE TABLE tags (
    id         TEXT PRIMARY KEY,
    name       TEXT NOT NULL COLLATE NOCASE UNIQUE,
    color      TEXT NOT NULL DEFAULT '#64748b',
    created_at TEXT NOT NULL
);

CREATE TABLE task_tags (
    task_id TEXT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    tag_id  TEXT NOT NULL REFERENCES tags(id)  ON DELETE CASCADE,
    PRIMARY KEY (task_id, tag_id)
);

-- ---------------------------------------------------------------- events
CREATE TABLE task_events (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id       TEXT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    event_type    TEXT NOT NULL,
    actor         TEXT NOT NULL DEFAULT 'user',
    field         TEXT,
    old_value     TEXT,
    new_value     TEXT,
    metadata_json TEXT NOT NULL DEFAULT '{}',
    created_at    TEXT NOT NULL,
    CHECK (actor IN ('user','ai','system'))
);

-- ---------------------------------------------------------------- proposals
CREATE TABLE ai_proposals (
    id                    TEXT PRIMARY KEY,
    ai_run_id             TEXT NOT NULL UNIQUE REFERENCES ai_runs(id) ON DELETE CASCADE,
    status                TEXT NOT NULL DEFAULT 'pending',
    intent                TEXT NOT NULL,
    assistant_message     TEXT NOT NULL DEFAULT '',
    actions_json          TEXT NOT NULL,
    preview_json          TEXT NOT NULL DEFAULT '[]',
    requires_confirmation INTEGER NOT NULL DEFAULT 1,
    confidence            REAL,
    created_at            TEXT NOT NULL,
    expires_at            TEXT,
    resolved_at           TEXT,
    CHECK (status IN ('pending','applied','rejected','expired')),
    CHECK (requires_confirmation IN (0,1)),
    CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1))
);

-- ---------------------------------------------------------------- settings
CREATE TABLE settings (
    key        TEXT PRIMARY KEY,
    value      TEXT NOT NULL,                  -- JSON-encoded
    updated_at TEXT NOT NULL
);

-- ---------------------------------------------------------------- devices
CREATE TABLE devices (
    id            TEXT PRIMARY KEY,
    name          TEXT NOT NULL,               -- e.g. "Infinix X6853"
    token_hash    TEXT NOT NULL UNIQUE,        -- HMAC-SHA256 hex
    token_prefix  TEXT NOT NULL,               -- first 8 chars for diagnostics
    created_at    TEXT NOT NULL,
    last_seen_at  TEXT,
    revoked_at    TEXT,
    CHECK (length(token_hash) = 64),
    CHECK (length(token_prefix) BETWEEN 4 AND 16)
);

-- ---------------------------------------------------------------- pairing
CREATE TABLE pairing_codes (
    code_hash   TEXT PRIMARY KEY,              -- HMAC-SHA256 of the 6-digit code
    created_at  TEXT NOT NULL,
    expires_at  TEXT NOT NULL,
    used_at     TEXT,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    CHECK (attempt_count >= 0)
);
```

### 4.1 Why these choices

- **`TEXT` timestamps** sort lexicographically when always UTC ISO 8601; no SQLite date-type ambiguity.
- **`version`** enables `UPDATE ... WHERE id = ? AND version = ?`; zero rows means a conflict.
- **`sort_order REAL`** allows inserting between rows (average of neighbors) without renumbering. A periodic renumber job is optional.
- **`due_all_day`** distinguishes “Friday” from “Friday 17:00”. All-day due dates are stored as local midnight converted to UTC; the UI renders the date only.
- **`ai_proposals.actions_json`** is an immutable snapshot of what the user reviewed. Re-validation happens against current DB state before apply.
- **`task_events`** uses an integer autoincrement key for cheap ordering; it is append-only.
- **`ai_runs.prompt_text`** is nullable and off by default. `prompt_hash` is enough for deduplication and debugging. Voice audio never reaches the backend in v1; `speech_ms` and the transcript are enough for audit/cost.
- **`devices.token_hash`** is a unique HMAC-SHA256 digest. The plaintext token is returned once at pairing and never stored server-side. `token_prefix` is the only diagnostic identifier.
- **`pairing_codes`** stores only a hash, an expiry, and an attempt counter; a code is single-use and invalidated when a new one is generated.

---

## 5. Indexes

```sql
-- Active list views
CREATE INDEX idx_tasks_status_due
    ON tasks(status, due_at)
    WHERE archived_at IS NULL;

CREATE INDEX idx_tasks_due_active
    ON tasks(due_at)
    WHERE archived_at IS NULL
      AND status NOT IN ('done','cancelled');

CREATE INDEX idx_tasks_project
    ON tasks(project_id)
    WHERE archived_at IS NULL;

CREATE INDEX idx_tasks_parent
    ON tasks(parent_id);

CREATE INDEX idx_tasks_scheduled
    ON tasks(scheduled_start_at)
    WHERE scheduled_start_at IS NOT NULL;

CREATE INDEX idx_tasks_updated
    ON tasks(updated_at);

-- Activity
CREATE INDEX idx_task_events_task_created
    ON task_events(task_id, created_at);

-- AI history
CREATE INDEX idx_ai_runs_created
    ON ai_runs(created_at);

CREATE INDEX idx_ai_runs_device
    ON ai_runs(device_id)
    WHERE device_id IS NOT NULL;

CREATE INDEX idx_ai_proposals_status
    ON ai_proposals(status, created_at);

-- Device auth
CREATE INDEX idx_devices_active
    ON devices(revoked_at)
    WHERE revoked_at IS NULL;

CREATE INDEX idx_pairing_expiry
    ON pairing_codes(expires_at)
    WHERE used_at IS NULL;
```

Notes:

- Partial indexes keep the common “active tasks” queries small.
- `project_id` and `parent_id` indexes are used by project views and subtask expansion.
- Do not add an index on `title`; FTS handles search.
- `EXPLAIN QUERY PLAN` the Today and Upcoming queries before release; adjust if SQLite chooses a scan.

---

## 6. Full-text search

```sql
CREATE VIRTUAL TABLE tasks_fts USING fts5(
    title,
    description,
    content = 'tasks',
    content_rowid = 'rowid',
    tokenize = 'unicode61 remove_diacritics 2'
);

CREATE TRIGGER tasks_fts_ai AFTER INSERT ON tasks BEGIN
    INSERT INTO tasks_fts(rowid, title, description)
    VALUES (new.rowid, new.title, new.description);
END;

CREATE TRIGGER tasks_fts_ad AFTER DELETE ON tasks BEGIN
    INSERT INTO tasks_fts(tasks_fts, rowid, title, description)
    VALUES ('delete', old.rowid, old.title, old.description);
END;

CREATE TRIGGER tasks_fts_au AFTER UPDATE OF title, description ON tasks BEGIN
    INSERT INTO tasks_fts(tasks_fts, rowid, title, description)
    VALUES ('delete', old.rowid, old.title, old.description);
    INSERT INTO tasks_fts(rowid, title, description)
    VALUES (new.rowid, new.title, new.description);
END;
```

Search query:

```sql
SELECT t.*
FROM tasks_fts f
JOIN tasks t ON t.rowid = f.rowid
WHERE tasks_fts MATCH :query
  AND t.archived_at IS NULL
ORDER BY rank
LIMIT 50;
```

Rules:

- Sanitize the user query for FTS syntax; wrap tokens and add `*` for prefix search.
- For queries shorter than 2 characters, use `LIKE '%q%'` on title instead.
- Rebuild after bulk import: `INSERT INTO tasks_fts(tasks_fts) VALUES('rebuild');`
- FTS5 external-content tables do not automatically index changes made outside the triggers. All writes go through the repository, so this is safe; document it for scripts.

---

## 7. Query patterns

### 7.1 Boundaries

Compute local day boundaries in Python with `zoneinfo`, convert to UTC strings, pass as parameters:

```python
start_local = datetime.combine(day, time.min, tzinfo=tz)
end_local = start_local + timedelta(days=1)
start_utc = start_local.astimezone(UTC).isoformat()
end_utc = end_local.astimezone(UTC).isoformat()
```

Never use SQLite's `date('now')` for user-facing dates; it is UTC and ignores the configured timezone.

### 7.2 Today

Includes overdue tasks and tasks scheduled today:

```sql
SELECT *
FROM tasks
WHERE archived_at IS NULL
  AND status NOT IN ('done','cancelled')
  AND (
      (due_at IS NOT NULL AND due_at < :day_end_utc)
      OR (scheduled_start_at IS NOT NULL AND scheduled_start_at < :day_end_utc)
  )
ORDER BY
  CASE priority
    WHEN 'urgent' THEN 0 WHEN 'high' THEN 1 WHEN 'medium' THEN 2
    WHEN 'low' THEN 3 ELSE 4
  END,
  COALESCE(due_at, scheduled_start_at, '9999') ASC,
  sort_order ASC,
  created_at ASC;
```

### 7.3 Upcoming

```sql
SELECT *
FROM tasks
WHERE archived_at IS NULL
  AND status NOT IN ('done','cancelled')
  AND due_at >= :day_start_utc
  AND due_at < :day_end_plus_7_utc
ORDER BY due_at, priority;
```

### 7.4 Inbox / Anytime / Waiting

```sql
-- Inbox
SELECT * FROM tasks
WHERE archived_at IS NULL AND status = 'inbox'
ORDER BY created_at DESC;

-- Anytime
SELECT * FROM tasks
WHERE archived_at IS NULL
  AND status IN ('todo','in_progress')
  AND due_at IS NULL
ORDER BY priority, sort_order, created_at;

-- Waiting
SELECT * FROM tasks
WHERE archived_at IS NULL AND status = 'waiting'
ORDER BY updated_at DESC;
```

### 7.5 Project view

```sql
SELECT *
FROM tasks
WHERE archived_at IS NULL
  AND project_id = :project_id
  AND parent_id IS NULL
ORDER BY status, priority, due_at, sort_order;
```

Subtasks are loaded separately (`WHERE parent_id = :id`) and expanded in the UI.

### 7.6 Optimistic update

```sql
UPDATE tasks
SET title = :title,
    updated_at = :now,
    version = version + 1
WHERE id = :id
  AND version = :expected_version
  AND archived_at IS NULL;
```

If `rowcount == 0`, raise `TaskConflict` and return `409` with the current task.

### 7.7 Complete

```sql
UPDATE tasks
SET status = 'done',
    completed_at = :now,
    updated_at = :now,
    version = version + 1
WHERE id = :id
  AND version = :expected_version
  AND status NOT IN ('done','cancelled');
```

Write a `completed` event in the same transaction. Idempotent if already done.

### 7.8 Device token lookup

```sql
SELECT id, name, token_prefix, created_at, last_seen_at
FROM devices
WHERE token_hash = :hmac_sha256
  AND revoked_at IS NULL;
```

Update `last_seen_at` at most once per minute per device to avoid turning every request into a write:

```sql
UPDATE devices
SET last_seen_at = :now
WHERE id = :device_id
  AND (last_seen_at IS NULL OR last_seen_at < :one_minute_ago);
```

Pairing-code redemption is a short transaction: `SELECT ... WHERE code_hash = :hash AND used_at IS NULL AND expires_at > :now`, then set `used_at` and insert the device. A failed attempt increments `attempt_count`; at 5, the code is invalidated.

---

## 8. Business invariants (service layer)

The database enforces shape; the service enforces meaning.

1. `project_id` must reference an existing, non-archived project.
2. `parent_id` must reference a non-archived task; max depth 3; no cycles.
3. A subtask inherits the parent's project if none is supplied; it may not point to a different project in v1.
4. Completing a parent does **not** auto-complete children; the UI may offer “complete all”.
5. Archiving a parent archives its children in the same transaction (or blocks with a count).
6. `due_all_day = 1` requires `due_at` at local midnight; `due_all_day = 0` allows any time.
7. `scheduled_start_at` and `scheduled_end_at` are either both null or both set, start < end, and do not overlap another scheduled task on apply.
8. `estimate_minutes` is 0–1440.
9. `title` is trimmed, non-empty, ≤300 chars; `description` ≤5,000 chars.
10. Every task mutation writes a `task_events` row with actor `user`, `ai`, or `system`.
11. Only `manual` hard delete is allowed; AI-originated deletes are archives.
12. `ai_run_id` is set only when the task was created or last materially changed by an applied proposal.
13. A device token must be active (`revoked_at IS NULL`) and is checked on every `/api/*` request except health/pairing.
14. Voice runs must have `kind='voice_interpret'`, a transcript in `input_text`, and `speech_ms` > 0; audio bytes are never persisted.
15. Mobile and web edits use the same `version` check; stale writes return `409` with the current task.

---

## 9. AI run and proposal storage

### `ai_runs.context_json`

Store only the keys/IDs needed to reproduce the prompt, not the full database:

```json
{
  "view": "today",
  "selected_task_id": null,
  "project_ids": ["01J...", "01J..."],
  "now": "2026-10-22T21:10:00+07:00",
  "timezone": "Asia/Jakarta",
  "schema_version": 1
}
```

### `ai_runs.raw_envelope_json`

Full `agy` envelope, including `status`, `response`, `structured_output`, `denied_actions`, `usage`, and `error`. For voice, store the Gemini response envelope or a redacted equivalent. Redact nothing here except secrets (there should be none). This is the debugging source of truth.

### `ai_runs` for voice

- `kind = 'voice_interpret'`
- `input_text` = the device transcript (never empty; reject with `VOICE_TRANSCRIPT_MISSING` if absent)
- `speech_ms` = client-reported/validated utterance duration (≤ 60 s)
- `device_id` = the paired device
- `context_json` includes `view`, `selected_task_id`, `timezone`, `schema_version`, `voice_input='system_stt'`, and `language`
- No audio is uploaded or stored in v1. A v1.1 cloud-audio fallback would add a transient file path and explicit consent; it is not part of this schema default.

### Usage aggregation

`/api/usage?days=7` sums `ai_runs` by day and kind (tokens, duration, count) and reads DB/backup/log/workspace sizes from the filesystem. Estimated cost is computed from a configured rate table; it is not stored. No new table is required.

### `ai_proposals.actions_json`

Exact validated action list that the UI displayed. Never regenerate actions from the prompt at apply time.

### `ai_proposals.preview_json`

Human-readable diff computed at proposal time:

```json
[
  {"op": "create", "title": "Call the dentist", "fields": {"due_at": "2026-10-27T08:00:00Z", "priority": "high"}},
  {"op": "update", "task_id": "01J...", "changes": {"priority": {"from": "medium", "to": "urgent"}}},
  {"op": "archive", "task_id": "01J...", "title": "Old draft"}
]
```

### Expiry

- Proposals expire after 24 hours (`expires_at`).
- A background sweep marks `pending` → `expired` and clears the UI badge.
- Applying an expired proposal returns `410`.

### Device authentication data

| Table | Lifecycle |
|---|---|
| `pairing_codes` | Created by web Settings; expires after 5 minutes; single-use; old code invalidated when a new one is created; purge expired rows after 24 hours |
| `devices` | Created on successful pairing; token hash only; `last_seen_at` updated at most once/minute; revoked from web; revoked rows retained for audit for 90 days, then purged |
| Device token | Returned once as `secrets.token_urlsafe(32)`; phone stores it in Keystore-backed storage; server stores `HMAC-SHA256(secret, token)` |

Token checks are constant-time. Logs may include `device_id` and `token_prefix`, never the token or code. A revoked device gets `403`; an unknown token gets `401`. The HMAC secret is required at startup whenever pairing is enabled; changing it invalidates every device and forces re-pairing.

### Retention

| Data | Retention |
|---|---|
| `ai_runs` | 90 days (configurable) |
| `ai_proposals` | 90 days |
| `task_events` | Indefinite (small) |
| `pairing_codes` | 24 hours after expiry/use |
| `devices` | Active until revoked; revoked rows kept 90 days for audit |
| Backups | 7 daily copies |
| Deleted/archived tasks | Until manual purge |

Purge job (Phase 6) deletes old AI rows and stale device/pairing rows; task events remain.

---

## 10. Migrations

### Tooling

- Alembic, `render_as_batch=True` (SQLite table rebuilds), `compare_type=True`.
- One migration per logical change; never edit an applied migration.
- The initial migration creates tables (including `devices` and `pairing_codes`), indexes, FTS table, and triggers in one transaction.

### Commands

```bash
cd backend

# apply
../.venv/bin/alembic upgrade head

# create after model change
../.venv/bin/alembic revision --autogenerate -m "add waiting_on"

# inspect
../.venv/bin/alembic current
../.venv/bin/alembic history
```

### Backup before migration

```bash
DB="${GTM_DB_PATH:-$HOME/.local/share/gtm/gtm.db}"
mkdir -p "$(dirname "$DB")/backups"
sqlite3 "$DB" ".backup '$(dirname "$DB")/backups/gtm-$(date +%Y%m%d-%H%M%S).db'"
```

The app runs this automatically before `upgrade head` in production mode (Phase 6). If the backup fails, abort the migration.

### SQLite caveats

- `ALTER TABLE` support is limited; use Alembic batch mode for column changes.
- Adding a `NOT NULL` column requires a default or a table rebuild.
- FTS virtual tables and triggers are raw `op.execute` in migrations; Alembic autogenerate will not manage them.
- Test `downgrade` for at least the last migration in development; production rollback is restore-from-backup.

---

## 11. Export / import

### Export format

```json
{
  "version": 1,
  "exported_at": "2026-10-22T21:10:00+07:00",
  "timezone": "Asia/Jakarta",
  "projects": [
    {"id": "01J...", "name": "Work", "color": "#64748b", "description": ""}
  ],
  "tags": [
    {"id": "01J...", "name": "health", "color": "#22c55e"}
  ],
  "tasks": [
    {
      "id": "01J...",
      "title": "Call the dentist",
      "description": "",
      "status": "todo",
      "priority": "high",
      "due_at": "2026-10-27T08:00:00Z",
      "due_all_day": false,
      "estimate_minutes": 15,
      "project_id": "01J...",
      "parent_id": null,
      "tags": ["health"],
      "created_at": "...",
      "updated_at": "...",
      "completed_at": null,
      "archived_at": null
    }
  ]
}
```

Excluded: `ai_runs`, `ai_proposals`, `task_events`, `devices`, `pairing_codes`, settings. Those are local diagnostics/security state, not portable data. Offer a separate full backup for disaster recovery.

### Import rules (Phase 6)

- Validate `version` and every record.
- Default: generate new IDs for imported tasks/projects/tags to avoid collisions.
- Optional `preserve_ids` for restoring a full backup; abort on duplicate IDs unless `replace=true`.
- Resolve project/tag references within the imported file; unknown refs are an error.
- Import in one transaction; report counts.
- Rebuild FTS after import.

---

## 12. Concurrency and performance

- **WAL** allows readers during a write. One writer at a time is fine for one user.
- Keep transactions short; never call `agy` or Gemini inside a DB transaction.
- The AI worker opens its own session; do not share sessions across threads/tasks.
- SQLAlchemy sessions: one per request (sync `Session` dependency), one per worker job, and one for voice requests.
- Device auth adds a small read per API request and at most one `last_seen_at` write per device per minute.
- `busy_timeout=5000` handles brief lock contention; Android and web can write concurrently, so keep transactions short.
- Expected scale: 10k–50k tasks, 100k events; SQLite handles this comfortably.
- If a query slows down, inspect with `EXPLAIN QUERY PLAN` and add a targeted index; do not index everything.

---

## 13. Timezone and date semantics

- All stored timestamps: UTC ISO 8601.
- Display: `GTM_TIMEZONE` (default system).
- All-day tasks:
  - User selects a date.
  - Server stores local `00:00` on that date converted to UTC, `due_all_day=1`.
  - UI renders only the date.
- Timed tasks:
  - User enters local date/time; server converts to UTC.
  - UI converts back to local.
- “Today” means the local calendar day in `GTM_TIMEZONE`, not UTC.
- Tests must cover:
  - 23:59 local → next UTC day.
  - DST start/end (if the configured zone has DST).
  - Month/year boundaries.
  - “Next Tuesday” across a weekend.
- Future change: per-task timezone and floating times. Not v1.

---

## 14. Example seed data

For development only; `backend/scripts/seed.py` creates:

- Projects: Work, Personal.
- Tags: deep-work, errand, health.
- Tasks:
  - Inbox: “Read SQLite WAL docs”
  - Today: “Call the dentist” (high, 15:00)
  - Overdue: “Renew domain” (urgent)
  - Waiting: “Waiting for design review”
  - Project: “Ship landing page” with three subtasks
  - Done: “Set up Python 3.14 venv”

Seed data must be idempotent (delete-and-recreate only in dev) and never run automatically in production.

---

## 15. Future extensions (not v1)

| Extension | Schema impact |
|---|---|
| Recurring tasks | `recurrence_rule`, `recurrence_parent_id`, occurrence table |
| Reminders | `reminders` table + scheduler |
| Calendar sync | `external_id`, `external_source`, sync cursor table |
| Attachments | `attachments` table + local file store |
| Full AI chat | `conversations`, `messages` tables |
| Gemini Live duplex | `voice_sessions` table (or transient session state + resumption token), not v1 |
| Multi-user | `users`, ownership columns, auth; SQLite → Postgres likely |
| Waiting details | `waiting_on`, `follow_up_at` |
| Importance/Eisenhower | `important` boolean or replace priority with two axes |

Do not add these columns/tables until the feature is scheduled; empty placeholders compound migration debt.
