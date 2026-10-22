# Android Client — Build Plan

**Status:** Revised, design-integrated plan for review; implementation not started
**Related:** [PLAN.md](PLAN.md) · [AI_INTEGRATION.md](AI_INTEGRATION.md) · [DATA_MODEL.md](DATA_MODEL.md)
**Design reference:** [reference.html](reference.html) ("Echo — voice task manager")
**Proposed package:** `dev.antinormies.gtm` (confirm before scaffold)

---

## 0. Design reference — analysis and synthesis

The reference is a **390 × 844 phone prototype** with three bottom tabs: **Speech**, **Tasks**, **System**. It is a polished interaction study, not a data-contract spec. Its screen language is worth keeping; its sprint/team/vector concepts are not part of GTM's single-user model.

### 0.1 What the prototype does well

- **Speech-first home.** The dark stage, animated orb, state pill, waveform, and live transcript make voice the primary action rather than a hidden feature.
- **Explicit voice states.** `idle → connecting → listening → captured → error`, with permission, retry, discard, and “nothing was saved” copy.
- **Task board + list.** A horizontal four-column board with a segmented control for a grouped list.
- **Detail as a bottom sheet.** Tags, key-value metadata, checklist, and “Move to” status actions in a focused sheet.
- **System/usage screen.** Human-readable stats, a 7-day usage chart, storage breakdown, engine facts, and destructive actions behind confirmation.
- **Real interaction details.** Focus trap, live-region announcements, 44 px touch targets, reduced-motion support, sticky segmented control, toast feedback.
- **A restrained Apple-like visual system.** White/light surfaces, SF-style display type, `#0071e3` accent, 12/14/17/21/28/40 type scale, 4/8/12/16/20/24/32/48 spacing, 8/12/18 radii, 150–220 ms motion.

### 0.2 What does not map to GTM

| Reference | GTM plan | Decision |
|---|---|---|
| “Echo” brand | GTM working name | Keep **GTM**; “Echo” is the reference name |
| Sprint 7 / story points / days left | No sprint entity; `estimate_minutes` | Header becomes **Today**; points become **time estimates** |
| Epics | `projects` | Epic → **Project** |
| Assignees / avatars | Single user | Drop avatars |
| Story/Bug/Chore/Spike type | No `task_type` in v1 | Drop the type column; tags can carry bug/chore |
| Acceptance criteria | Subtasks + notes | Render subtasks as the **checklist** |
| Review column | `waiting` status | Board columns: **To do / In progress / Waiting / Done** |
| Vectors / embeddings / vector index | SQLite + FTS5 only | Replace with **FTS5 search**; no vector tables |
| Claude / GPT / embedding models | `agy` + Gemini 3.8 Flash | Replace with the real AI stack |
| 5 GB local cache, cloud region | SQLite on the local host | Show **DB / backups / logs / workspace** |
| “Sign out” | Device pairing | **Unpair this phone** |
| On-device transcription claim | Plan previously uploaded audio | Default to **device transcript only**; cloud audio is v1.1, off by default |

### 0.3 The middle-ground synthesis

These are the decisions this plan now follows:

1. **Three-tab Android shell: Speak / Tasks / System.** This replaces the earlier four-tab Today/Inbox/Upcoming/Projects shell. GTD views become **filters and groups inside Tasks**; the web app keeps its full GTD sidebar.
2. **Tasks is Board + List.** Board columns are status-based, not sprint-based: **To do** (includes untriaged Inbox cards with an Inbox badge), **In progress**, **Waiting**, **Done**. List is grouped by project with **All / Today / Overdue** chips and an Inbox group.
3. **Speech is the default tab.** Tap the orb to start; tap again or pause for ~10 s to finish. The prototype's state machine and permission/error behavior are kept, adapted to GTM's server-side proposal flow.
4. **On-device speech by default.** Android's `SpeechRecognizer` (on-device when available) produces the transcript; **only the transcript** goes to the backend. The backend uses Gemini 3.8 Flash **text** with the same action schema. Cloud audio transcription is a v1.1 opt-in.
5. **Capture card for creates, proposal sheet for changes.** A simple create can be confirmed from the card; updates, archives, multi-actions, and low-confidence results open a review sheet with field diffs. Destructive actions always require a tap.
6. **Short spoken confirmation, optional.** GTM adds a brief TTS confirmation (“Added to your tasks”) with a mute setting. TTS is not a chat reply.
7. **System is a real diagnostics screen.** Keep the reference's panel layout, but show GTM's tasks, AI usage from `ai_runs`, DB/backups/logs, `agy`/Gemini engine facts, paired device, and actions: back up, rebuild FTS, unpair.
8. **Visual language, not literal pixels.** Use the reference's tokens and hierarchy; implement in Compose with the Android system font (Roboto), edge-to-edge layout, and a dark variant for Tasks/System while the speech stage stays dark.

---

## 1. Purpose and scope

GTM gets a **native Android client** in `mobile/`, backed by the single FastAPI service in `backend/`. The web UI remains the desktop surface; Android is the voice and capture surface.

### v1 includes

- Pair the phone with the local backend using a short-lived code.
- Three-tab shell: **Speak**, **Tasks**, **System**.
- Speak: tap-to-talk, live transcript, capture card, permission/error/retry states, optional TTS.
- Tasks: Board and List, four status columns, project-grouped list, filters, search, quick add.
- Task detail sheet: metadata, checklist/subtasks, notes, move-to status, archive.
- System: stats, 7-day AI usage, storage, engine/device facts, backup/rebuild/unpair actions.
- Clear offline, unauthenticated, and server-unreachable states.
- Debug APK on the connected Infinix; signed release APK later.

### v1 explicitly excludes

- Offline writes or a background sync engine.
- Always-on listening or a wake word.
- Full-duplex Gemini Live conversation.
- Cloud audio transcription by default (v1.1 opt-in).
- Push notifications, reminders, recurring tasks, calendar sync.
- Teams, assignees, sprints, story points, epics as separate entities.
- Vector search or embeddings.
- iOS, tablet-specific layout, Play Store release (sideload first).
- Multi-user accounts; pairing is for one user's devices.

The mobile client is a **thin client**. It never talks to SQLite, `agy`, or Gemini directly, and it never applies an AI proposal locally. The server remains the source of truth and the only writer.

---

## 2. Stack

| Concern | Choice | Why |
|---|---|---|
| Language | Kotlin 2.x | First-class Android, coroutines, Compose |
| UI | Jetpack Compose + Material 3 | Design-system tokens and custom surfaces |
| Min SDK | 31 (Android 12) | Modern speech APIs, reasonable coverage |
| Compile/target SDK | 36 initially; pin after Phase 4 SDK check | SDK 35/36/37 installed; avoid previews |
| Build | Gradle Kotlin DSL + version catalog + wrapper | Reproducible CLI builds |
| Networking | Retrofit + OkHttp | Interceptors, timeouts, MockWebServer |
| JSON | kotlinx.serialization | Kotlin-native |
| Async/state | Coroutines, Flow, ViewModel, StateFlow | Lifecycle-safe state |
| Local settings | DataStore Preferences | Server URL, TTS/mute, language |
| Token storage | Android Keystore-backed encrypted storage | Never store bearer token in plain prefs |
| Voice input | Android `SpeechRecognizer` + partial results | On-device when available; transcript-only by default |
| Voice output | Android `TextToSpeech` | Optional short confirmation |
| DI | Manual `AppContainer` | No Hilt build complexity |
| Tests | JUnit 4, MockWebServer, Compose UI test | Standard Android tooling |
| Local cache | None in v1 | Online-first; retry instead of stale writes |

**Not used in v1:** Hilt, Room, WorkManager, Firebase, push notifications, Gemini Live client, WAV/audio upload, ffmpeg, PWA wrapper.

Pin Gradle/AGP/Kotlin versions in `mobile/gradle/libs.versions.toml` at Phase 4 after the first successful build. The wrapper is committed; `local.properties` is not.

---

## 3. Project layout

```
mobile/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── gradlew
├── gradlew.bat
├── local.properties              # git-ignored, sdk.dir=~/Android/Sdk
├── README.md
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/dev/antinormies/gtm/
        │   │   ├── GtmApplication.kt
        │   │   ├── MainActivity.kt
        │   │   ├── AppContainer.kt
        │   │   ├── data/
        │   │   │   ├── api/          # Retrofit service, DTOs, interceptors
        │   │   │   ├── model/        # domain models
        │   │   │   ├── repository/   # Task, Project, Auth, Usage
        │   │   │   └── settings/     # DataStore, token store
        │   │   ├── ui/
        │   │   │   ├── shell/        # 3-tab scaffold, nav, theme
        │   │   │   ├── speak/        # orb, waveform, states, capture card
        │   │   │   ├── tasks/        # board, list, filters, quick add
        │   │   │   ├── detail/       # task bottom sheet
        │   │   │   ├── system/       # usage, storage, engine, devices
        │   │   │   ├── search/
        │   │   │   ├── components/   # cards, chips, sheets, toast
        │   │   │   └── theme/        # tokens, type, color, motion
        │   │   └── voice/
        │   │       ├── SpeechRecognizerController.kt
        │   │       └── TtsPlayer.kt
        │   └── res/                  # strings, themes, icons, network config
        ├── test/                     # JVM unit tests
        └── androidTest/              # instrumented UI tests
```

One Activity (`MainActivity`), Compose Navigation with three top-level destinations. Sheets and dialogs are Compose modal surfaces, not separate activities.

---

## 4. Design system

### 4.1 Tokens

Adopt the reference's semantic tokens; do not scatter raw colors in composables.

| Token | Light | Dark / speech stage |
|---|---|---|
| Background | `#FFFFFF` | `#000000` |
| Surface | `#F5F5F7` | `#1C1C1F` |
| Foreground | `#1D1D1F` | `#F5F5F7` |
| Secondary text | `#6E6E73` | `#A1A1A6` |
| Accent | `#0071E3` | `#2997FF` |
| Success | `#16A34A` | `#30D158` |
| Warning | `#EAB308` | `#FFB340` |
| Danger | `#DC2626` | `#FF453A` |
| Border | `#D2D2D7` / `#E8E8ED` | `rgba(255,255,255,.13)` |

- **Speech screen:** always dark stage, wave `#2997FF`, orb core gradient, green done state, amber error state.
- **Tasks / System:** light surface by default; provide a dark theme using the same semantic tokens.
- **Priority:** urgent `#B3261E`, high `#8A4B00`, medium `#424245`, low `#6E6E73` with tinted pill backgrounds.

### 4.2 Typography

Reference scale: 12 / 14 / 17 / 21 / 28 / 40 / 56 / 80; body 1.47, display 1.05–1.14, display tracking `-0.015em`.

- Android uses the **system sans (Roboto)**; do not bundle SF Pro.
- Display: 28–30 sp semibold for page titles and the speech headline; 17 sp semibold for sheet/card titles.
- Body: 15–17 sp; meta: 11–12 sp; labels: 11 sp uppercase with 0.08–0.09em tracking.
- Use tabular numerals for estimates, progress, and usage numbers.
- Respect font scaling; avoid fixed-height text containers that clip at 200%.

### 4.3 Spacing, shape, motion

- Spacing: 4 / 8 / 12 / 16 / 20 / 24 / 32 / 48 dp.
- Screen gutter: 16–20 dp; board rail gutter 20 dp.
- Radii: 8 (chips), 12 (rows), 14 (cards), 18 (panels), 28 (sheets), pill (buttons/tabs).
- Touch targets: **≥ 44 dp**; tab bar **62 dp**; buttons 44–46 dp.
- Motion: 150 ms fast, 220 ms base, standard easing `cubic-bezier(0.28, 0, 0.22, 1)`.
- Orb: halo pulse, rotating dashed arc, waveform bars; capture-card slide-up; sheet slide-up.
- Honor **reduced motion**: disable halo/waveform animation, keep state changes immediate.

### 4.4 Layout targets

- Reference frame: **390 × 844**; support **360–430 dp** width and edge-to-edge.
- Status bar / gesture inset aware; no content under system bars.
- Board columns: **76% of screen width, max 268 dp**, 12 dp gap, horizontal scroll with snap; a “Scroll sideways” hint.
- Bottom sheets: max 84% height, drag handle, scrim, focus trap.
- Dialogs: max 300 dp wide alert style; permission alert uses two actions.

### 4.5 Accessibility

- All icon-only buttons need content descriptions.
- Voice state changes use a polite live region and visible text.
- Focus order follows visual order; sheets trap focus and restore it on close.
- Contrast: body text on surface; priority pills with text labels, not color alone.
- Text scaling and dark theme are first-class; test at 200% font scale.
- Haptics: light confirmation on capture/apply, error buzz on failure.

---

## 5. App shell and screens

### 5.1 Navigation

Bottom bar with three tabs:

| Tab | Icon | Purpose |
|---|---|---|
| **Speak** | microphone | Default; voice capture and recent result |
| **Tasks** | board | Board/List, filters, search, quick add |
| **System** | chart | Usage, storage, engine, pairing |

- Active tab uses foreground; inactive muted.
- Tapping Speak from another tab returns to the voice idle/result state; it does not auto-record.
- Task detail and search open as sheets above the current tab.
- Android back closes the top sheet first; otherwise stays on the current tab.

### 5.2 Speak

Layout, top to bottom:

1. Brand row: **GTM** (uppercase, tracked) left; state pill right (`Idle`, `Connecting`, `Listening`, `Captured`, `Needs attention`).
2. Headline: `Tap to speak` / `Listening` / `Task captured` / error-specific.
3. Supporting text: one or two lines, clamped; live transcript while listening.
4. Orb: 222 dp stage, 92 dp core, halo/rings, mic icon; scales/pulses while listening; check icon on capture; alert icon on error.
5. Waveform: 44 bars, visible only while listening.
6. Result slot: idle tip, “Connecting…”, “Building the task…”, capture card, or error block.
7. Hint: `Tap the orb to start` / `Tap when you are done` / `Nothing was saved — the orb is still ready`.

**Voice states**

| State | Pill | Behavior |
|---|---|---|
| `idle` | Idle | Tap starts; tip visible |
| `connecting` | Connecting | Permission/recognition/service warm-up; cancel available |
| `listening` | Listening | Live partial transcript; tap finishes; 10 s silence auto-finishes; 60 s hard cap |
| `captured` | Task captured | Capture card or proposal sheet; Add/Review/Discard |
| `error` | Needs attention | Specific copy, Retry/Allow microphone; nothing saved |

**Permission**

- Explain before requesting: “GTM uses the microphone to turn speech into a task. Audio is handled by your phone's speech service; GTM receives only the transcript.”
- Allow → start. Deny → error state with “Open settings” / “Not now”. Manual Tasks remain usable.
- Re-check permission on resume; never loop the system dialog.

**Capture card** (simple create)

- Title (2 lines max), chips for project, estimate, priority, due, and “Inbox”.
- Actions: **Add to tasks** (primary), **Discard** (ghost), optional **Review** if the proposal has non-create changes.
- The primary action applies the proposal through the server; a toast confirms the new task and its short ID.
- If confidence < 0.6, replace Add with **Review** and open the proposal sheet.

**Proposal sheet** (update/archive/multi/low confidence)

- Shows the transcript, assistant message, and a field-by-field diff.
- Apply/Cancel; destructive actions use a danger button and require a tap.
- Ambiguous matches show candidate tasks; no guessed IDs.

### 5.3 Tasks

**Header**

- Eyebrow: `Today · Fri 23 Oct` (or the selected filter).
- Title: `My tasks` / project name.
- Subtitle: `X tasks · Y done` + completion meter for the current filter/today.
- Search icon; overflow menu with filters and “New task”.

**Segmented control: Board / List**

- **Board** (default): horizontal columns **To do**, **In progress**, **Waiting**, **Done**.
  - To do includes `inbox` and `todo`; inbox cards get a small **Inbox** tag.
  - Waiting replaces the reference's Review column and uses the plan's `waiting` status.
  - Cancelled/archived cards are hidden.
  - Column header: name, count, task/estimate total, and a **+ Add** button prefilled with that status.
  - Card: short ID, estimate chip, title (2 lines), priority, project, due; no avatar.
  - Horizontal scroll with snap; hint “Scroll sideways for Waiting and Done.”
- **List**: grouped by project; rows show title, short ID/status, estimate/due, priority.
  - Filter chips: **All / Today / Overdue**; Inbox is a group at the top.
  - Empty state per filter; search opens a full-screen sheet.

**Short display ID**

- The reference's `VOX-148` is a sprint ID; GTM uses a deterministic **short display key** derived from the UUID suffix, e.g. `GTM-4F2A`. It is display-only and not a new database key.
- The full UUID remains the API/DB identity.

**Quick add**

- Header **+** opens a compact task form (title, project, priority, due, estimate).
- Column **+ Add** pre-fills status.
- Voice capture always lands in **Inbox** until triaged; the capture card says so.

### 5.4 Task detail sheet

- Eyebrow: short ID · status.
- Title (editable).
- Tag row: priority, project, estimate, due, Inbox/status.
- Key-value rows: Status, Estimate, Project, Due, Created, Last updated.
- Checklist: subtasks with checkboxes; add subtask inline.
- Notes: plain text/Markdown read-only in v1; edit in a full-screen editor if needed.
- Move to: status buttons for the other three columns; archive at the bottom in danger style.
- Save is explicit for field edits; optimistic version conflict shows “This task changed; review again.”
- Close button, drag handle, scrim, focus trap.

### 5.5 System

Keep the reference's panel hierarchy, with real GTM data.

**Stats (2 × 2)**

- Total tasks / completed this week.
- AI runs (30 days) / voice captures.
- DB size / backups size.
- Tokens used (30 days) / estimated cost.

**AI usage panel (dark)**

- 7-day bar chart of tokens and estimated cost from `ai_runs`.
- Total for the period.
- Breakdown by path: text `agy`, text Gemini, voice transcript Gemini (and any cloud audio fallback if enabled).
- No Claude/GPT/embedding rows.

**Storage panel**

- Segmented bar: SQLite DB, backups, logs, `agy` workspace.
- Values in MB; total against the host disk, not a hard-coded 5 GB.

**Engine panel**

- `agy` version/model/effort.
- Voice input: system speech recognizer (on-device when available), language.
- Text/voice interpretation: Gemini 3.8 Flash or `agy`.
- Schema version, search index (FTS5), last AI run.
- Paired device name, last seen, revoke/unpair.

**Actions**

- **Back up now** → creates a SQLite backup, toast with path/size.
- **Rebuild search index** → FTS5 rebuild, confirm sheet.
- **Unpair this phone** → danger confirm; clears token and returns to pairing.
- No cloud sign-out, vector rebuild, or model selection in v1.

**Footer**

`GTM 0.1.0 · Audio is handled by your phone's speech service. GTM receives only the transcript. Cloud audio is off.`

### 5.6 Sheets, alerts, toast

- Task detail: bottom sheet, max 84% height.
- Proposal review: bottom sheet with diff.
- Generic confirm: title, body, primary/danger + Cancel.
- Permission alert: centered alert dialog.
- Toast: bottom above the tab bar, 2.6 s; one at a time.
- Scrim tap and Android back close the sheet; focus returns to the trigger.

### 5.7 Required states

- Unpaired / token revoked.
- Loading skeleton for Tasks; empty Board columns; empty List filter.
- Server unreachable/offline banner; writes disabled; Retry.
- Search empty/results/error.
- Voice permission denied; recognizer unavailable; silence/timeout; server error; low confidence; ambiguous task.
- TTS muted/unavailable.
- 409 version conflict on edit.
- 401 → pairing; 403 → revoked message.
- Dark/light, 200% font scale, reduced motion.

---

## 6. Domain mapping

| Reference concept | GTM field |
|---|---|
| Task ID `VOX-148` | Display-only short key from UUID; full UUID is identity |
| Column | `status`: inbox/todo → To do; in_progress → In progress; waiting → Waiting; done → Done |
| Story points | `estimate_minutes` rendered as `15m`, `1h`, `2h` |
| Epic | `project_id` / project name |
| Priority | `priority`: urgent/high/medium/low/none |
| Assignee | Omitted (single user) |
| Type | Omitted; tags can carry bug/chore |
| Acceptance criteria | Subtasks/checklist + notes |
| Sprint/days left | Today/selected filter + due dates |
| Vector index | FTS5 search index |
| Token burn | `ai_runs` tokens/cost |
| Sign out | Unpair device |

---

## 7. Networking and server contract

### Base URL

Configured through `BuildConfig.GTM_BASE_URL`:

- **Debug:** `http://127.0.0.1:8765` with `adb reverse tcp:8765 tcp:8765`, or `http://10.0.2.2:8765` from the emulator.
- **Release:** `https://<machine>.<tailnet>.ts.net` through Tailscale Serve, or a Cloudflare Tunnel hostname.
- No hardcoded production URL; set it in `local.properties` / a Gradle property.

Release builds set `android:usesCleartextTraffic="false"`. Debug uses a network security config that permits cleartext only for `127.0.0.1` and `10.0.2.2`.

### Endpoints used by Android

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/auth/pair` | Exchange a pairing code for a device token |
| GET | `/api/auth/me` | Validate token, return device/server info |
| POST | `/api/auth/revoke` | Revoke the current device |
| GET | `/api/mobile/bootstrap` | Projects, counts, today/board/list data, server time, usage summary |
| GET | `/api/tasks` | Filtered list (`view`, `q`, `project`, `include_done`) |
| POST | `/api/tasks` | Create |
| GET | `/api/tasks/{id}` | Detail |
| PATCH | `/api/tasks/{id}` | Update with `version` |
| POST | `/api/tasks/{id}/complete` | Complete |
| POST | `/api/tasks/{id}/reopen` | Reopen |
| DELETE | `/api/tasks/{id}` | Archive |
| PUT | `/api/tasks/{id}/order` | Reorder |
| GET | `/api/projects` | Project list |
| GET | `/api/search?q=` | FTS |
| GET | `/api/usage?days=7` | Token/cost/storage summary for System |
| POST | `/api/maintenance/backup` | Create a SQLite backup; return path/size |
| POST | `/api/maintenance/rebuild-search` | Rebuild the FTS5 search index |
| POST | `/api/voice/interpret` | Transcript → validated proposal |
| POST | `/api/ai/interpret` | Text fallback; async `202 {run_id}` |
| GET | `/api/ai/runs/{id}` | Text run status |
| POST | `/api/ai/proposals/{id}/apply` | Apply proposal |
| POST | `/api/ai/proposals/{id}/reject` | Reject proposal |
| GET | `/api/health` | Server/DB/AI health (minimal) |

All other `/api/*` routes require `Authorization: Bearer <device-token>`. The web UI uses the Tailscale perimeter + Origin checks; Android uses bearer auth.

Headers:

- `Authorization: Bearer …`
- `X-GTM-Client: android/<version>`
- `Accept: application/json`

Timeouts: 10 s connect/read for CRUD, 30 s for voice interpretation; no automatic retry for POST/PATCH. GETs may retry once. The error envelope is rendered into a short message; never show raw HTML.

---

## 8. Pairing and token storage

1. Web Settings → **Pair a phone** → six-digit code, valid 5 minutes.
2. Android posts `{code, device_name}` to `/api/auth/pair`.
3. Backend verifies the hashed code, creates a `devices` row, returns a 32-byte token **once**.
4. Android stores the token in Keystore-backed encrypted storage; never logs it.
5. Every request attaches `Authorization: Bearer <token>`; backend checks the HMAC hash and updates `last_seen_at`.
6. Revoking from web or System invalidates the token immediately.

Rules:

- Pairing attempts are rate-limited (5 per 5 minutes per IP).
- Code is single-use; generating a new one invalidates the old.
- Tokens are shown once; server stores only a hash and prefix.
- `401` clears the token and returns to pairing; `403` shows revoked-device copy.
- The app never stores the pairing code.

---

## 9. Voice pipeline (v1: device transcription)

### 9.1 Capture and recognition

- Tap the orb to start; request `RECORD_AUDIO` with the design's permission explanation on first use.
- Use Android `SpeechRecognizer` with `RecognitionListener`; prefer `createOnDeviceSpeechRecognizer` when `isOnDeviceRecognitionAvailable` is true, otherwise the system recognizer.
- Request `EXTRA_PARTIAL_RESULTS`, `EXTRA_LANGUAGE` (`id-ID` / `en-US`), and free-form dictation.
- Show partial transcript in the speech text area; keep the last final result.
- Tap the orb to finish; auto-finish after **10 s of silence**; hard cap **60 s**.
- Cancel discards the session and nothing is saved.
- If recognition is unavailable or errors, show a specific error with Retry and a typed-input fallback.
- The system recognizer may use on-device or Google-hosted recognition depending on the device; GTM itself does not upload audio.

### 9.2 Backend interpretation

`POST /api/voice/interpret` with JSON:

```json
{
  "transcript": "Move the report to Friday and make it urgent",
  "view": "board",
  "selected_task_id": null,
  "language": "en-US",
  "duration_ms": 4200
}
```

The backend:

1. Validates transcript length (1–4,000 chars) and duration (≤ 60 s).
2. Calls Gemini 3.8 Flash **text** with the shared action schema, or `agy` if configured.
3. Validates with Pydantic and the deterministic action rules.
4. Persists `ai_runs(kind='voice_interpret', input_text=transcript, speech_ms=duration_ms, device_id=…)` and `ai_proposals(pending)`.
5. Returns the transcript, assistant message, preview, and proposal ID synchronously (20 s cap, one concurrent voice request).

The model only proposes; the server applies after confirmation. No audio reaches the backend in v1.

### 9.3 Result UX

- **Simple create:** capture card with title/project/estimate/priority/due; **Add to tasks** or **Discard**. Add calls the apply endpoint.
- **Update/archive/multi/low confidence:** proposal sheet with diff; Apply/Cancel; destructive requires a tap.
- **Spoken confirmation:** optional short TTS after apply (“Added to your tasks” / “Moved to Friday”); muted in Settings. Never a long chat reply.
- **Toast + live region** after apply; new task shows its short display key.
- On error: “Nothing was saved — the orb is still ready”; Retry/Allow microphone.

### 9.4 Privacy and fallback

- Default: audio is handled by the phone's speech service; only the transcript is sent to GTM/Gemini.
- Copy must not claim universal on-device processing; use “on-device when available”.
- **Cloud audio fallback** (WAV → Gemini 3.8 Flash audio) is v1.1, off by default, requires explicit consent and a clear Settings disclosure. It is not part of v1 code, tests, or endpoints.
- No audio is stored by GTM in either mode.

### 9.5 Targets

| Metric | Target |
|---|---|
| Tap-to-capture result, p50 | < 4 s |
| Tap-to-capture result, p95 | < 8 s |
| Voice command success (20 scripted utterances) | ≥ 95% valid proposals |
| Silence auto-finish | 10 s |
| Max utterance | 60 s |
| Unconfirmed destructive changes | 0 |

### 9.6 Live conversation

Gemini 3.8 Live duplex is **v1.1**, not v1. It would add streaming audio, barge-in, session resumption, and a new cost model. The current voice path is deliberately a command capture, not a phone call.

---

## 10. Local development

### Prerequisites

- Backend `.env` has a non-empty `GTM_SECRET_KEY`.
- Android SDK at `~/Android/Sdk` (installed; platforms 35/36/37).
- Java 21 (installed). Gradle runs on 21; compile with the Java 17 toolchain.
- USB debugging on the Infinix X6853; `adb devices` shows it.
- Android Studio is **not** required; use the committed Gradle wrapper.

### Build and install

```bash
cd mobile
printf 'sdk.dir=%s\n' "$HOME/Android/Sdk" > local.properties
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Connect to the backend

```bash
# Terminal 1: backend
cd backend
../.venv/bin/python -m app

# Terminal 2: USB tunnel so the phone's localhost reaches the laptop
adb reverse tcp:8765 tcp:8765

# Launch
adb shell am start -n dev.antinormies.gtm/.MainActivity
```

For remote use, run `tailscale serve --bg --https=443 localhost:8765` on the host, install Tailscale on the phone, and set the base URL to the `*.ts.net` HTTPS address. Do not expose the backend directly.

### Commands

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest     # device connected
./gradlew :app:lintDebug
adb logcat -s GTM
```

The first Gradle run downloads the wrapper/dependencies. Pin versions after the first successful build.

---

## 11. Testing

| Level | Coverage |
|---|---|
| Unit | ViewModels, repository mapping, short-key display, transcript validation, error mapping |
| Network | MockWebServer: success, 401/403, 409 version conflict, 5xx, timeout, voice payload |
| Compose UI | Speak states, permission alert, capture card, proposal sheet, board/list, detail sheet, system panels |
| Instrumented | Install/pair/CRUD/voice on the Infinix; rotation; process death; 200% font scale; dark/light |
| Manual | Mic allow/deny, on-device vs system recognizer, silence/timeout, noisy room, Indonesian/English, TTS mute, Tailscale on/off, revoked token |
| Backend contract | `/api/mobile/bootstrap`, auth, voice JSON, usage, proposal apply/reject |

No test hits the real Gemini/agy API by default. `FakeGeminiClient`/`FakeAgyClient` return fixtures; Android uses a fake recognizer and MockWebServer. Gated contract tests are opt-in.

Screenshot tests for the three tabs and task detail are optional but useful for keeping reference fidelity.

---

## 12. Build, release, distribution

- `versionCode` increments per build; `versionName` follows the backend tag.
- Debug uses the default debug keystore.
- Release signing uses a local keystore via environment variables; never commit it.
- First delivery: sideload `app-release.apk`.
- Play Store is optional, requires a signed AAB, privacy policy, and data-safety disclosure. Not v1.
- Release build refuses a cleartext or empty base URL.
- Version design tokens and screen specs with the app; update this document when the reference changes.

---

## 13. Milestones

| Milestone | Deliverable | Maps to |
|---|---|---|
| M1 | Gradle project, theme/tokens, 3-tab shell, empty screens; APK installs | Phase 4 |
| M2 | Pairing + bootstrap + Board/List + detail sheet + CRUD/conflict | Phase 4 |
| M3 | System panels (usage/storage/engine) + actions | Phase 4 |
| M4 | Speak states, device STT, capture card, proposal sheet, TTS | Phase 5 |
| M5 | Error/offline/auth states, tests, release notes | Phase 5–6 |

---

## 14. Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Design fidelity costs more than expected | Delay | Tokens/components first; screenshot-test three tabs; defer nonessential polish |
| Compose horizontal board complexity | Delay | Simple snap LazyRow; fall back to vertical list if needed |
| Device speech recognizer unavailable/inaccurate | Voice UX | Check availability, prefer on-device, typed fallback; cloud audio v1.1 |
| System recognizer sends audio off-device | Privacy copy wrong | Say “on-device when available”; disclose system service; no GTM upload |
| Permission denial/loop | Voice blocked | Clear error, settings deep link, text path unaffected |
| Board/list data inconsistency | Confusing | One repository, one bootstrap snapshot, refresh on focus |
| Design tokens drift from reference | Visual debt | Centralize tokens; screenshot tests; review against reference |
| Gradle/AGP mismatch without Android Studio | Build blocked | Pin wrapper/AGP early; build `assembleDebug` day one |
| Tailscale/WARP conflict | Phone unreachable | Test both; prefer Tailscale; document fallback |
| Scope creep (sprints, teams, vectors) | Delay | Explicit non-goals; map to projects/statuses |
| TTS voice/locale gaps | No Indonesian voice | `isLanguageAvailable`; fall back to English; mute option |

---

## 15. Decisions to confirm before scaffold

1. Package name: `dev.antinormies.gtm` or another?
2. App label: **GTM** (reference uses “Echo”)?
3. Three-tab **Speak / Tasks / System** shell acceptable?
4. Board columns **To do / In progress / Waiting / Done** acceptable?
5. Device speech recognition as default (transcript-only), with cloud audio deferred?
6. Minimum SDK 31 / compile-target 36 acceptable?
7. English-only UI, or English + Indonesian from day one?
8. TTS on by default or muted by default?
9. Sideload only, or prepare for Play Store?
10. Is the reference's visual direction binding, or inspiration?
