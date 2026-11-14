# AntaraTask AI — Mobile Design System

**Status:** authoritative for `mobile/` UI on `feat/new-ui`. Supersedes the visual
sections (0, 4, 5) of [MOBILE.md](docs/MOBILE.md), which remain the behavioural and
networking record.

**Origin:** [Numo](https://mobbin.com/apps/numo) (iOS) — 33 Mobbin screens sampled
pixel-by-pixel. Evidence, measurements and Mobbin links: [.agents/design/numo-extraction.md](.agents/design/numo-extraction.md).

**Decided, not guessed:** font, accent model, voice stage, row surface, board
treatment, radii and motion were settled by Jev (TypeSafe System One) against explicit
criteria, with a confidence floor and code-side fallbacks. Ledger:
[.agents/design/decisions.md](.agents/design/decisions.md) · replay:
`python3 .agents/design/tools/jev_judge.py verify`.

**Machine-readable tokens:** [.agents/design/tokens.json](.agents/design/tokens.json) —
the single source of truth. Colour changes are gated by
`python3 .agents/design/tools/check_contrast.py` (32 WCAG-AA pairs).

---

## 1. Principles

1. **The task is the interface.** Every screen answers "what do I do next?". Chrome
   stays flat and quiet so rows and actions carry the weight.
2. **One loud thing per screen.** A saturated header, or a saturated voice circle, or a
   saturated composer canvas — never two competing saturated fields.
3. **Voice is a state machine you can see.** Idle, listening, thinking and error each
   get their own colour, glyph and sentence. No spinner-only ambiguity.
4. **Warm paper, black ink.** White canvas, cream rows, near-black display type. No
   gradients, no glass, no drop shadows except on genuinely floating things.
5. **Motion explains, never decorates.** Animation only marks a state the user must
   perceive (recording, thinking, completion). Reduced motion removes all loops.

---

## 2. Colour

Two complete palettes. No screen may read a raw hex value; everything resolves through
semantic tokens in `NumoColors`.

### 2.1 Light

| Token | Value | Used for |
|---|---|---|
| `brand` | `#0067FF` | coloured header band, primary buttons, FAB, active tab, selected chips |
| `brandStrong` | `#0054CC` | brand as *text* on white/cream (links, inline actions) |
| `brandSoft` | `#E8F1FF` | selected-chip fill, quiet brand tint |
| `onBrand` | `#FFFFFF` | text/icons on `brand` |
| `canvas` | `#FFFFFF` | screen background |
| `surface` | `#FFFDFE` | cards, sheets |
| `surfaceWarm` | `#FFF2DB` | task-row fill (the signature cream) |
| `surfaceWarmSoft` | `#FFF9EE` | subdued warm block, empty states |
| `ink` | `#000000` | display titles, task titles |
| `inkBody` | `#2F2F2F` | body copy, values, numbers |
| `muted` | `#6B6B70` | timestamps, meta, placeholders |
| `mutedSoft` | `#8A8A90` | disabled/decorative only — never body text |
| `line` | `#EFEFF2` | dividers inside cards |
| `lineStrong` | `#E2E2E7` | card outlines, unfilled checkbox stroke |
| `success` | `#01C459` | completion, processing orb, heatmap top |
| `successStrong` | `#067237` | success as text |
| `successSoft` | `#D7F5E4` | success chip/tile fill |
| `onSuccess` | `#000000` | text on `success` |
| `danger` | `#FF3E32` | recording orb + rings, error orb |
| `dangerFill` | `#D92B20` | destructive button fill (white text passes AA) |
| `dangerStrong` | `#C62828` | destructive/error as text |
| `dangerSoft` | `#FFEBEB` | error block fill |
| `onDanger` | `#FFFFFF` | text on `dangerFill` |
| `warning` | `#FF9901` | idle mic orb, reward pill, attention |
| `warningSoft` | `#FFF1D8` | attention chip fill |
| `onWarning` | `#000000` | text on `warning` |
| `magenta` | `#F549C8` | expressive/decorative accent only (celebration, streak) |
| `magentaSoft` | `#FFE9F8` | magenta tint fill |
| `onMagenta` | `#000000` | text on `magenta` |
| `scrim` | `#0000008C` | modal scrim |

### 2.2 Dark

| Token | Value | Note |
|---|---|---|
| `brand` | `#3D8BFF` | brighter on dark; `onBrand` flips to near-black |
| `onBrand` | `#0B0B0F` | 5.93:1 — black on bright blue is the dark-mode button |
| `canvas` | `#0B0B0F` | |
| `surface` | `#15161B` | |
| `surfaceWarm` | `#241E14` | cream darkened, keeps the warm row rhythm |
| `ink` / `inkBody` | `#FFFFFF` / `#E8E8EA` | |
| `muted` | `#9A9AA2` | 6.47:1 on `surface` |
| `success` | `#34D97B` | `onSuccess` = `#0B0B0F` |
| `danger` | `#FF6B61` | `onDanger` = `#0B0B0F` |
| `warning` | `#FFB340` | |

### 2.3 Colour rules

- **Accent allocation (Jev D2 = `brand_plus_semantic`).** Brand blue owns navigation,
  headers, primary actions and selection. Green is reserved for *progress and
  completion*. Orange is reserved for *attention* (idle mic, reward, stale sync).
  Magenta appears only in celebration/streak surfaces. Never use green for a primary
  action or blue for "done".
- **Fill vs text.** A hue used as a *fill* pairs with its `on*` colour; the same hue used
  as *text* uses its `*Strong` variant. `#01C459` as text fails AA (2.32:1) — use
  `successStrong`. This is enforced by the contrast gate.
- **Header band.** Exactly one screen region per screen may be a full-bleed saturated
  band. Inside it, text is `onBrand`; chips are 1.5dp `onBrand` at 60% alpha outlines
  with `onBrand` labels.
- **No gradients.** The only permitted gradient is the streak celebration surface
  (orange→red), which is decorative and always behind a white card.

---

## 3. Typography

**Family:** Plus Jakarta Sans, bundled. Five weights: 400 / 500 / 600 / 700 / **800**.
The 800 (ExtraBold) static instance (`plus_jakarta_sans_extrabold.ttf`) carries the
Numo-style display voice and was added by this work (Jev D1 = `jakarta_extrabold`).

| Style | Size / line | Tracking | Weight | Use |
|---|---|---|---|---|
| `displayXL` | 40 / 44 | −1.6 | 800 | hero screen titles, streak number |
| `displayL` | 34 / 38 | −1.2 | 800 | "Today", "Tasks", voice headline, sheet hero |
| `displayM` | 28 / 32 | −0.9 | 800 | secondary screen titles, empty-state hero |
| `titleL` | 22 / 28 | −0.5 | 800 | composer title input, detail task title |
| `titleM` | 18 / 24 | −0.3 | 700 | task row title, card titles |
| `bodyL` | 17 / 24 | 0 | 500 | transcript, paragraphs |
| `bodyM` | 15 / 21 | 0 | 500 | supporting copy, form values |
| `meta` | 13 / 18 | 0 | 600 | timestamps, ids, counts |
| `label` | 12 / 16 | +0.4 | 700 | chip labels, section eyebrows, tab labels |
| `numberXL` | 44 / 48 | −1.5 | 800 | stat numbers |
| `numberL` | 32 / 36 | −1.0 | 800 | secondary stats |

Rules:

- Display styles are always **sentence case**, never all-caps, and never exceed two
  lines on a 393dp frame. If a title needs three lines, drop to `displayM`.
- Task titles use `titleM` and clamp to 2 lines; body copy never uses 800.
- Tabular numerals for estimates, counts, usage and dates (`FontFeatureSetting("tnum")`).
- Text must scale to 200% without clipping: no fixed-height text containers, rows grow
  (`heightIn(min = 72.dp)`), display styles may wrap to 3 lines at large scale.

---

## 4. Space, shape, elevation, motion

**Spacing scale (dp):** 4, 8, 12, 16, 20, 24, 32, 40. Screen gutter **18dp**; gap
between cards/rows **10dp**; card internal padding **16–20dp**; group separation
**24dp**.

**Radii (Jev D7 = `rs_12_20_28`):** control/input/chip **12**, task row & small tile
**20**, card & sheet **28**, actions and status pills **999** (full).

**Elevation:** flat by default. Only three things float: the FAB cluster
(`0 8 24 rgba(0,0,0,.12)`), bottom sheets (`0 -12 40 rgba(0,0,0,.18)`) and the toast
pill. Cards use `lineStrong` outlines or a warm fill instead of shadows.

**Motion (Jev D8 = `functional_pulse`):**

| Motion | Duration | Notes |
|---|---|---|
| state colour/scale swap | 120 ms | `ease = CubicBezier(0.2, 0, 0, 1)` |
| panel/sheet enter | 200 ms | slide 24dp + fade |
| row strikethrough/check | 200 ms | check scales 0.8→1 |
| recording rings | 1200 ms loop | 3 rings, staggered 400 ms, alpha 0.35→0 |
| processing dots | 900 ms loop | 3 dots, y-offset ±4dp |
| FAB press | 120 ms | scale 0.94 |

Under `Settings.Global.ANIMATOR_DURATION_SCALE == 0` (or Compose reduced-motion
reporter), rings and dots render **static at mid-phase** and all loops stop. Nothing
disappears; the state is still legible.

**Icons:** rounded, 1.75dp stroke, 22dp default (`iconMd`), 18dp inside chips, 26dp for
FAB glyphs. Always paired with a text label except in the bottom bar, the FABs, and
the header's gift/avatar.

---

## 5. Components

Each entry is the contract the Compose implementation must satisfy. Names match the
composables in `ui/components/`.

### 5.1 `AppHeader`
Full-bleed band, `accent` fill, height **min 128dp + status bar**, rounded bottom
corners **28dp** (only the bottom corners).
Layout: row → streak pill, title block, then gift icon and avatar.
- `StreakPill`: white teardrop/flame container 34dp with `numberL` count and a flame
  glyph; hidden when streak is 0.
- Title: `displayL` in `onBrand` with a 22dp chevron when a scope menu exists.
- `Gift`: 24dp glyph at 90% `onBrand`, 44dp touch target.
- `Avatar`: 40dp circle, 2dp `onBrand` ring, initials fallback.
Below the title band, inside the same fill: horizontal `FilterChips` row (18dp gutter,
scrollable, no wrap).

### 5.2 `FilterChips`
36dp tall, full radius, 1.5dp outline at 55% `onBrand`, transparent fill, `label` text
in `onBrand`. Selected: `onBrand` fill (white), `accent` text, no outline. 8dp gap,
18dp gutter, horizontal scroll.

### 5.3 `TaskRow`
Signature list unit.
- Height **min 72dp**, radius **20**, horizontal padding 14dp, vertical 12dp, 10dp gap
  between rows.
- Fill alternates `surfaceWarm` and `surface` by index (Jev D5 = `alternating`).
  Completed rows drop to `surface` with 60% content alpha and a strikethrough title.
- Leading `NumoCheckbox` (26dp), 12dp gap, then a column: `titleM` title (2 lines max),
  and a `meta` line combining project · estimate · due.
- Trailing: due time in `meta` `muted` (tabular), or priority dot (8dp) when urgent.
- Long-press shows a 24dp drag handle on the left (grab affordance) — the whole row
  remains tappable.
- Touch target ≥ 44dp even at 200% scale; rows grow rather than clip.

### 5.4 `NumoCheckbox`
26dp rounded square (radius 8), 2dp `lineStrong` stroke when off. On: `brand` fill,
white check, 200ms scale-in. Completed variant uses `success` fill when the parent row
is in a completion context (celebration), otherwise `brand`.

### 5.5 `FabCluster`
Two circles bottom-right, 18dp gutter, above the bottom bar (16dp):
- `MicFab` 48dp, `surface` fill, `brand` mic glyph, 1.5dp `line` ring — starts voice
  capture from any tab.
- `AddFab` 60dp, `brand` fill, `onBrand` plus glyph — opens the composer.
Overlap order: `AddFab` on top-right, `MicFab` offset 10dp left and 6dp down.

### 5.6 `NumoBottomBar`
68dp, `canvas` background, 1dp top `line`. Three items (Speak / Tasks / System) in this
build; each is icon 22dp above a `label`. Active: `brand` glyph + text; inactive:
`muted`. Active item also gets a 4dp `brand` dot below the label (no pill background).
Badges: 18dp `danger`/`success` circle at the icon's top-end.

### 5.7 `VoiceStage`
The Speak screen's body.
1. Headline `displayL` (2 lines, left-aligned, 18dp gutter).
2. Tip `bodyM` `muted`, max 2 lines.
3. State label 12dp above the orb: `label` in the state colour
   (`warning` idle → "Hold and speak"; `danger` error; `success` thinking →
   "Your tasks incoming…").
4. `StateOrb` 150dp centred.
5. `LanguagePill` 40dp, full radius, 1.5dp `lineStrong` outline, `meta` label.
6. Result area: transcript card or capture card.
7. Actions: `PrimaryButton` ("Add to your tasks") + `GhostButton` ("New braindump").

### 5.8 `StateOrb`
150dp circle, 120ms colour transition, centred glyph 56dp, only these states:

| State | Fill | Glyph | Extra |
|---|---|---|---|
| `idle` | `warning` | white mic | none |
| `connecting` | `brand` | white mic waves | 2 static rings |
| `listening` | `danger` | white rounded-square stop | 3 expanding rings, 1200ms loop |
| `thinking` | `success` | three white dots | dots bounce 900ms |
| `captured` | `success` | white check | single 280ms scale pop |
| `error` | `danger` | white alert-triangle | static |

`contentDescription` on the orb reads the state in words ("Listening. Tap to finish.").

### 5.9 `StackCard` / composer canvas
Composer and detail surfaces are **stacked white cards on a saturated canvas**:
- Canvas: `brand` fill (composer) or `scrim` over the current screen (detail sheet).
- Cards: `surface`, radius **28**, padding 18–20dp, separated by **8dp** gaps that let
  the canvas show through.
- Cards never nest more than one level; a card contains rows, not cards.

### 5.10 Composer pieces
- `ComposerTitle`: `titleL` `ink` input, placeholder `mutedSoft`, min 2 lines, no visible
  box (the card is the box), 20dp top padding.
- `EffortChips`: 40dp circles in a row with a `label` caption ("Tiny effort").
  Off: `surfaceWarm` fill, `inkBody` letter. On: `brand` fill, `onBrand` letter.
  Values `S M L XL` map to 15 / 30 / 60 / 120 minutes.
- `TileToggle`: two 96×76dp tiles, radius 20. On: `brand` fill + `onBrand`.
  Off: `surface` fill + 1.5dp `lineStrong` + `ink`.
- `TagChips`: wrap-flow chips 36dp, radius 12. On: `brand` fill, `onBrand` text, 16dp
  remove glyph. Off: `surface` + `lineStrong` outline + `inkBody`.
- `ScopeRow`: icon 20dp + `titleM` label in a 44dp-min row ("Today", "Tomorrow",
  "Select date", "Someday / Backlog").
- `SubmitFab`: 56dp `brand` circle with an up-arrow, bottom-right of the canvas.

### 5.11 `NumoSheet`
Modal bottom sheet: `surface`, top radius 28, drag handle 36×4 at `lineStrong`, max
height 88%, scrim `scrim`, focus trapped, back/scrim dismiss, `displayM` title,
24dp bottom padding + navigation inset.

### 5.12 Action hierarchy
| Role | Spec |
|---|---|
| Primary | 52dp, full radius, `brand` fill, `onBrand` `titleM` |
| Destructive | 52dp, full radius, `dangerFill` fill, `onDanger` `titleM` |
| Secondary | 52dp, full radius, `surface` fill, 1.5dp `lineStrong`, `ink` |
| Ghost | 44dp, no fill, `inkBody` `titleM`, 12dp horizontal padding |
| Chip-action | 36dp, full radius, `surfaceWarm`, `inkBody` |

### 5.13 `ToastPill`
Top-centre, 12dp below the status bar, `brand` fill, `onBrand` label + check glyph,
radius 999, 44dp tall, 220ms fade/slide, auto-dismiss 2.6s, one at a time, announced as
a polite live region.

### 5.14 Data display
- `StatTile`: `surfaceWarm` card radius 28, 20dp padding, `numberXL` value, `meta`
  `muted` label, 24dp tinted icon badge.
- `Heatmap`: 6 columns × N rows of 40dp rounded squares (radius 12). Empty `line`,
  low `successSoft`, mid `success` at 55%, top `success`.
- `UsageBars`: 7 bars, 10dp wide, radius 6, `brand` at 85%, `meta` day label beneath.
  Implemented but unused until the API exposes a daily series (see §6.7).
- `StorageBar`: 12dp track radius 6 `line`, segments `brand` / `success` / `warning`.
- `SettingsRow`: 44dp min, 40dp tinted circular icon (16% tint of its hue), `titleM`
  label, `meta` value right-aligned, chevron when actionable.

### 5.15 `NumoAlertDialog`
Centred, `surface`, radius 28, 24dp padding, `displayM` title, `bodyM` body, two pills
side by side: decline `surfaceWarm` + `inkBody`, confirm `brand` + `onBrand`.

---

## 6. Screens

All screens are edge-to-edge with `WindowInsets` handling; the status bar takes the
colour of the region behind it.

### 6.1 Login
**Split layout** (Jev tournament X6): saturated `brand` top half carrying the mark
(rounded 28dp white-on-brand spark tile), wordmark and a `displayXL` claim; bottom half
is a white `sheet`-radius panel with the explainer lines (mic → transcript → proposal →
your confirmation), the primary "Continue with Google" button and the privacy fine
print. Fine print `meta` `muted`: audio stays on the phone; only the transcript is sent. States: loading (button shows an inline spinner
and disables), error (`dangerSoft` block with `dangerStrong` text, retry stays available).

### 6.2 Speak (default tab)
White canvas. 20dp top, 18dp gutter. Back-less. Optional AI badge (top-right, `success`
sparkle + "Ai" `label`). Headline `displayL` "Braindump tasks with your voice", tip
`bodyM`. Centre block (orb + label + language pill) is weighted to sit at ~46% height.
Result area below the orb:
- **Transcript card**: `surfaceWarm` radius 28, `bodyL` transcript, `meta` duration.
- **Capture card**: `surface` radius 28 outlined, `titleM` title, chip row (project,
  estimate, priority, due), primary "Add to your tasks", ghost "Discard", optional
  "Review" when the proposal is non-create or confidence < 0.6.
- **Error block**: `dangerSoft` radius 20, `dangerStrong` `bodyM` copy, "Try again" +
  "Open settings" when permission was denied.
Footer hint `meta` `muted`: "Nothing was saved — the orb is ready". If the mic permission
is missing, show `NumoAlertDialog` *before* requesting it, with the privacy sentence.

### 6.3 Tasks — list (default, Jev D3 = `list_first`)
`AppHeader` in **compact band** mode (Jev tournament X1): `displayM` title so the band
stays short and the list keeps the screen. Band = streak pill (hidden when zero), title
with a chevron that cycles the scope (All → Today → Overdue), a search icon, and
`AvatarBadge` when the account has a name. Filter chips and the List/Board
`SegmentedToggle` share a `FlowRow`, so at 200% font scale the toggle wraps to a second
line instead of colliding with the chips. Body on `canvas`:
- 18dp gutter, rows from §5.3, 10dp gaps.
- Grouped variant inserts a `SectionTitle` (label `muted`, 24dp above, 12dp below) per
  project, with the group's active count.
- Completion meter under the header when a filter is active: `meta` "12 tasks · 4 done" +
  4dp `success` bar.
- Empty state: `surfaceWarmSoft` card with `displayM` "Nothing here", `bodyM` guidance,
  primary "Add a task".
- FAB cluster bottom-right; bottom bar §5.6.

### 6.4 Tasks — board (segmented control in header)
Header keeps the band; the segmented control (Board | List, 36dp pill, `onBrand` active
fill) sits under the chips. Columns: 76% screen width cards? No — **columns are 260dp
wide**, 12dp gaps, horizontally scrollable, canvas background.
- Column header: 36dp tinted chip (`brandSoft` for To do, `warningSoft` for In progress
  done? mapping below) + count pill + `+` icon.
- Cards: white rows (radius 20, 1.5dp `lineStrong`) with title, meta and priority dot.
  Long-press opens a status "Move to" sheet listing the four statuses; empty columns show
  a warm "No tasks" block. Free drag was replaced by this sheet: same capability, no
  fragile pointer maths, and it works at any font scale.
- Status tints (Jev D6 = `white_cards_tinted_head`): To do `brandSoft`/`brandStrong`;
  In progress `warningSoft`/`warning`; Waiting `magentaSoft`/`magenta`; Done
  `successSoft`/`successStrong`.

### 6.5 Task detail sheet
`NumoSheet` containing stacked `surfaceWarm`/`surface` cards:
1. Eyebrow `label` muted: short key · status.
2. Title `titleL` + leading checkbox.
3. Chip row: status, priority, project, due, estimate (chips 36dp, radius 999).
4. Key-value card: Status / Estimate / Project / Due / Created / Updated
   (`meta` muted label left, `bodyM` value right).
5. Subtasks card: header "N subtasks" + `titleM`; rows with `NumoCheckbox` + title +
   remove; inline "Type subtask" field with a circular submit.
6. Notes card: `bodyL` read-only, `surfaceWarmSoft`.
7. Move-to row: four status chips.
8. Danger row: "Archive task" in `dangerStrong`.
Save is explicit; a 409 conflict shows a `dangerSoft` notice "This task changed — review
again" and keeps the sheet open.

### 6.6 Quick add (composer, Jev tournament X4 = `full_canvas`)
Full-screen `brand` canvas (§5.9) with the card stack: title card (scope pill "Public ⌄"
+ big title input) → subtasks row → effort chips → Task/Routines tiles → scope rows
(Today 18:00 / Tomorrow 09:00 / Next week / No date) → priority card. Scope and priority
chips sit in `FlowRow`s. The submit action is a full-width **inverted** button (white fill,
brand label) because the canvas is brand; disabled it becomes an outline. A floating FAB
was removed after it overlapped the button on real screens. Keyboard-aware via
`imePadding()`.

### 6.7 System
White canvas, `displayL` "System". A single inline figure strip (Jev tournament X5 =
`list_rows`): total tasks · done this week · AI requests · voice captures, rendered as
`StatFigure`s in one row, then settings-style rows for everything else. Then:
- `surfaceWarm` card: "AI usage · 30 days" with tokens as the hero figure, estimated
  cost and voice captures as rows. The 7-day bar chart is **not** rendered: `Usage` from
  the API carries only aggregates (runs, voiceRuns, tokens, cost, storage), no daily
  series. The `UsageBars`/`Heatmap` components exist and are ready for it once the
  endpoint exposes a per-day breakdown.
- Storage card: `StorageBar` + legend (DB / backups / logs / workspace).
- Engine card: `SettingsRow`s (model, speech recognizer, schema, FTS index, last run).
- Device card: paired device, last seen, "Unpair this phone" in `dangerStrong`.
- Actions: secondary "Back up now", secondary "Rebuild search index".
- Footer `meta` `muted`: version + privacy line.
Destructive actions open `NumoAlertDialog` with the destructive pill in `dangerFill`.

### 6.8 Proposal review
`NumoSheet`: transcript `surfaceWarmSoft` card, assistant message `bodyL`, diff rows
(field · from → to, `meta` labels), primary "Apply", ghost "Discard". Destructive ops
require the `dangerFill` pill.

### 6.9 Search
Full-screen: 52dp input (radius 12, `surfaceWarm` fill, 22dp search glyph), results as
`TaskRow`s, empty state `displayM` "No matches" + `bodyM` tip.

---

## 7. States and content

- **Voice**: idle / connecting / listening / captured / thinking / error, each with a
  distinct sentence, colour and glyph (see §5.8). Errors always state what was *not*
  saved.
- **Data**: loading skeleton rows (warm blocks at 40% alpha), empty per filter, offline
  banner (`warningSoft`) with "Retry", 401 → login, 409 → conflict notice.
- **Copy voice**: sentence case, second person, no exclamation marks except celebration
  surfaces, no jargon ("AI run" → "AI requests"). Numbers use tabular figures.
- **Confirmation**: destructive actions always require a tap; no auto-apply of AI
  proposals.

---

## 8. Accessibility contract

- Contrast: all declared pairs pass WCAG AA — gated by `check_contrast.py`. Never put
  `#01C459`, `#FF3E32`, `#FF9901` or `#F549C8` behind small text without their `on*`
  colour.
- Touch targets ≥ 44dp (FABs, chevrons, checkbox rows, tab items).
- Every icon-only control has a `contentDescription`; decorative glyphs are `null`.
- Voice state changes fire a polite live-region announcement and are also visible text.
- Focus order follows visual order; sheets trap focus and restore it on dismiss.
- Text scale 200%: no clipping, rows grow, boards scroll, display styles wrap.
- Reduced motion: loops stop, state still legible (§4).
- Dark theme is a first-class palette, not an inversion filter.

---

## 9. Implementation map

```
ui/theme/Theme.kt          NumoColors (light/dark), Type, Shapes, Space, Motion, NumoTheme
ui/components/Common.kt    AppHeader, FilterChips, TaskRow, NumoCheckbox, FabCluster,
                           NumoBottomBar, StackCard, ButtonX, SectionTitle, StatTile,
                           SettingsRow, ToastPill, NumoAlertDialog, EmptyState
ui/components/VoiceOrb.kt  StateOrb (+ rings, dots, reduced-motion handling)
ui/components/DataViz.kt   Heatmap, UsageBars, StorageBar, StatFigure
ui/screens/LoginScreen.kt  §6.1
ui/screens/SpeakScreen.kt  §6.2 / §5.7
ui/screens/TasksScreen.kt  §6.3 + §6.4
ui/screens/TaskDetailSheet.kt §6.5
ui/screens/QuickAddDialog.kt  §6.6
ui/screens/SystemScreen.kt    §6.7
ui/screens/ProposalSheet.kt   §6.8
ui/GtmApp.kt               shell, insets, tab wiring, permission flow, toast pill
```

Verified screenshots from the physical device and the emulator live in
`.agents/design/verification/` (see `verification.md` there for the run log).

Rules: screens never hardcode colours or dp magic numbers that exist in the token set;
screens read `NumoTheme.colors`, `NumoTheme.type`, `NumoTheme.space`. New shared UI goes
to `ui/components/`, not into a screen file.

## 10. Do / Don't

| Do | Don't |
|---|---|
| One saturated band or canvas per screen | Stack a blue header on a blue composer |
| Cream rows alternating with white | Tint rows by status (colour is for chrome, not data) |
| `successStrong` for green text | `#01C459` text on white (2.32:1) |
| Heavy 800 display for titles only | 800 weight in body copy, timestamps or buttons |
| Radius 12/20/28 + pill | Mixed arbitrary radii |
| Animate recording/thinking states | Ambient breathing orbs, list entrance animations |
| State spelled out in words | Colour alone to signal listening/error |

## 11. Verification

```bash
python3 .agents/design/tools/check_contrast.py      # 32 AA pairs
python3 .agents/design/tools/jev_judge.py verify    # ledger drift check
cd mobile && ./gradlew :app:assembleDebug --no-daemon -PGTM_GOOGLE_CLIENT_ID=""
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

A UI change is done only with: a green build, the contrast gate passing, and a
screenshot of the affected screen from the emulator at both light and dark. Do not
claim a visual result without the screenshot path.

---

## 12. Decisions of record, deviations and evidence

### 12.1 Feature scope (Jev audit, `jev_judge.py audit`)

| Feature | Verdict | Confidence | In v1 |
|---|---|---|---|
| Voice capture from any tab via the mic FAB | include | 0.70 | yes |
| Pull-to-refresh on Tasks | include | 0.96 | yes |
| Haptics on capture/apply/destructive | include | 0.72 | yes (see note) |
| Search from the Tasks header | include | 0.55 | yes |
| Project colour dot in the meta line | include (fallback) | 0.37 | yes |
| Offline banner with retry | include | 0.86 | yes |
| Keyboard-aware composer | include | 0.70 | yes |
| Warm skeleton loaders | include | 0.68 | yes |
| Per-filter empty states | include | 0.50 | yes |
| Swipe-to-complete | defer (fallback) | 0.28 | no |
| Manual light/dark toggle | defer | 0.55 | no — follows the system |
| Completion celebration card | defer | 0.70 | no — no gamification in v1 |
| In-app reduced-motion preference | defer (fallback) | 0.35 | no — respects the OS setting |
| **Board status change** | *override* | — | yes, as a long-press "Move to" sheet |
| **409 conflict notice** | *override* | — | yes, surfaced through the shell toast + retry |

**Overrides are deliberate.** Jev was asked whether to defer board status changes and the
conflict notice; both capabilities already exist in v0.1, so deferring them would be a
functional regression. A rebuild may change how a capability looks, never silently remove
it. The board keeps status changes through a move sheet (not free drag) and the shell
keeps surfacing conflicts.

**Haptics note:** `LocalHapticFeedback` fires on task completion and on applying a
proposal. No vibration permission is required.

### 12.2 Design alternatives (pairwise tournament, `jev_judge.py tournament`)

Single-pick questions came back with low discrimination, so each was re-run as a
pairwise Noul tournament with Copeland aggregation:

| Question | Winner | Copeland |
|---|---|---|
| Tasks header composition | `compact_band` | 2.0 |
| Board column width | `col_232` (232dp) | 2.0 |
| Voice result placement | `below_orb` | 1.5 |
| Composer surface | `full_canvas` (stacked cards on brand) | 2.0 |
| System statistics | `list_rows` | 2.0 |
| Login layout | `split` (brand block + white panel) | 1.5 |

Two of these reversed the single-pick answers (composer: bottom sheet → full canvas;
System: stat grid → list rows). That is exactly why the tournament exists.

### 12.3 Where the implementation deviates from the reference, and why

| Reference detail | Shipped | Reason |
|---|---|---|
| Streak pill in the header | Hidden when the count is 0 | The product has no streak data; a fake "0" is noise |
| Gift icon | Search icon | No rewards system; search is a real need |
| S/M/L/XL effort chips in the composer | Omitted | `createTask` persists no estimate; the chips would lie |
| Tags + private note in the composer | Omitted | Same reason — not persisted on create |
| Time-wheel picker | Explicit scope rows | No wheel widget; rows are faster and accessible |
| Free drag between board columns | Long-press move sheet | Same capability, robust at any font scale |
| Floating submit FAB in the composer | Full-width inverted button | On device the FAB overlapped the button |
| 7-day usage bars / heatmap | Not rendered | The API exposes aggregates only |
| Per-list colour theming | Single brand accent | Jev D2; keeps semantics unambiguous |

### 12.4 Defects found on device and fixed

Found only by running the build on real hardware, not by compiling:

1. Voice headline sat under the status bar — Speak lacked `statusBarsPadding`.
2. `Locale.ROOT` produced "6 M11" instead of "6 Nov" (root locale has no month names).
3. Hint/footer copy used `mutedSoft`, a decorative-only token (failed AA) — switched to `muted`.
4. Detail-sheet cards were invisible (white card on white sheet) — warm surfaces instead.
5. "Waiting" chip clipped in the move row — `FlowRow` wrapping.
6. Composer's disabled primary button was unreadable on the brand canvas — inverted button
   with an outline disabled state.
7. The submit FAB overlapped the composer button — FAB removed.
8. Status-bar icons were dark-on-dark in dark mode for Speak/System — icon polarity now
   derives from the palette, not the tab alone.
9. At 200% font scale the header chips collided with the segmented toggle and the bottom
   bar clipped its labels — the band is now a `FlowRow` and the bar uses `heightIn(min)`.

Full reproduction log and screenshots: `.agents/design/verification.md`.
