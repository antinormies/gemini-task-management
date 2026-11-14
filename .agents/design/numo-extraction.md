# Numo extraction — evidence

Reference: **Numo** (iOS), captured from Mobbin's mobile section (platform `ios`; web
results deliberately ignored). 33 usable screens, 1179×2676 px (@3x, ≈393×892 pt).

Method:

1. Mobbin MCP `search_screens` (deep mode) for task-manager, voice-assistant and app
   chrome surfaces; `search_flows` for the capture and creation journeys.
2. High-resolution images downloaded to `reference/numo/` (`*.webp`), then sampled with
   Pillow: median-cut palettes per screen, plus vertical/horizontal pixel strip scans to
   recover exact fills and section boundaries.
3. Colours classified into semantic roles by Jev (`.agents/design/jev/palette.json`).
4. Anything that could not survive Android's accessibility bar was corrected and the
   correction is recorded in `decisions.md` + `tokens.json`.

Flows used:

- [Adding braindump (AI voice mode)](https://mobbin.com/flows/effe08af-817d-43de-8c8a-94929e1609b8) — 11 screens
- [Creating a task](https://mobbin.com/flows/af1e20df-4e33-4fc5-a00b-8a3b29e16f84) — 13 screens
- [Backlog](https://mobbin.com/flows/156ace3b-b2da-4285-8827-55a13d6f7bb3) — 2 screens

## Screen inventory

| Local file | Surface | What it shows |
|---|---|---|
| `01_voice_entry.webp` | composer | "Switch to AI voice mode" banner, Public pill, big title input, effort chips, Task/Routines tiles |
| `02_voice_entry2.webp` | permission | system mic alert, "Don't Allow" (peach) / "Allow" (blue) |
| `03_voice_entry3.webp` | permission | speech-recognition alert variant |
| `04_voice_idle_orange.webp` | voice | idle: orange 150dp circle, white mic, "Hold and speak", outlined "English" pill |
| `06_voice_recording.webp` | voice | listening: red circle with white rounded-square stop |
| `07_voice_rings.webp` | voice | listening: three expanding translucent red rings |
| `08_voice_error.webp` | voice | error: red circle + "You didn't say anything? Hold and speak your tasks again" |
| `09_voice_processing.webp` | voice | thinking: green circle, three white bouncing dots, "Your tasks incoming…" |
| `10_voice_after.webp` | voice | result actions: blue "Add to your tasks" pill + white "New braindump" pill |
| `11_voice_result.webp` | voice | result card with blue primary action |
| `12_home_blue.webp` | list | blue band, streak pill, "Today ⌄", outlined chips, cream/white rows, mic + FAB |
| `13_create_stack_empty.webp` | composer | stacked white cards on blue canvas, placeholder "Brain dump your tasks…" |
| `14_create_subtask_row.webp` | composer | "Add subtasks" card row |
| `15_create_stack_typed.webp` | composer | typed title "Pick up Harvey", Submit FAB |
| `16_create_subtasks.webp` | composer | "7 subtasks" card, circle checkboxes, remove ×, Reorder, inline input |
| `17_create_time.webp` | composer | time wheel, selected row highlighted white |
| `18_create_tags.webp` | composer | tag chips card + Manage row |
| `19_create_private.webp` | composer | selected tags with × , private-note card |
| `20_create_visibility.webp` | sheet | Public / Private sheet over the composer |
| `21_home_today_blue.webp` | list | list after creation, completed row blue check + strikethrough |
| `22_home_today_green.webp` | list | green accent variant, light-green row fills |
| `23_home_menu.webp` | list | Plans / Backlog / Tags / Reorder circular shortcuts |
| `24_backlog.webp` | list | "‹ Backlog", drag-handle rows, peach "Add new" pill |
| `25_backlog2.webp` | list | backlog scrolled state |
| `26_progress_heatmap.webp` | data | 3 stats (17 / 17 / 9), month heatmap, bottom bar |
| `27_profile_card.webp` | profile | avatar + camera badge, name + count badge, blue profile card, team card |
| `28_profile_menu.webp` | profile | circular icon menu rows, green Inbox badge |
| `29_profile_magenta.webp` | profile | magenta prompt cards, overflow sheet (Report / Block) |
| `30_edit_profile.webp` | form | labelled fields, peach prompt cards, blue Save pill |
| `31_edit_prompts_toast.webp` | form | "Updated" blue toast pill top-centre |
| `32_day_streak.webp` | celebration | orange→red gradient, white card, flame, week circles, SHARE / CONTINUE |
| `33_onboarding_partner.webp` | onboarding | mascot, dashes progress, "Skip onboarding" |
| `34_onboarding_adhders.webp` | onboarding | mascot cluster, heavy centred headline |

## Sampled palette

Global, across all screens (share of sampled pixels):

| Hex | Share | Role (Jev) |
|---|---|---|
| `#FFFFFF` / `#FFFDFE` | 54% | canvas / card |
| `#0067FF` | 4.5% | brand primary |
| `#2F2F2F` | 2.9% | body ink |
| `#FFECCE` / `#FFEED2` / `#FFF2DB` | 3.9% | warm surface |
| `#01C459` | 0.75% | success |
| `#FD31C8` / `#F549C8` | 0.87% | expressive accent |
| `#FF3E32` | 0.52% | danger / recording |
| `#FF9901` | — | attention / idle mic |
| `#F1F1F1` / `#F3F3F3` | 1.4% | empty / dividers |

Strip scans pinned the actual fills:

- `22_home_today_green`: band `#01C459` for the top 14.4% of the frame; rows `#D7F5E4`
  and `#F6F6F6`; inactive squares `#F3F3F3`.
- `12_home_blue`: band `#0067FF`; rows `#FFF2DB` and `#FFFDFE`; body ink `#2F2F2F`.
- `04_voice_idle_orange`: white canvas 82%, orb `#FF9901`, title `#000000`.
- `08_voice_error` / `07_voice_rings`: orb `#F83F33` / `#FF3E32`, rings `#FFDCDB`.
- `09_voice_processing`: orb `#00C458`.
- `13_create_stack_empty`: canvas `#0067FF` (4.4% of a centre-line scan), cards `#FFFDFE`,
  placeholder `#F4F1F4`, ink `#2F2F2F`.

## Geometry recovered (pt at @3x)

| Element | Measured |
|---|---|
| Header band | ≈128pt tall incl. status bar; 28pt bottom radius |
| Streak pill | ≈34pt tall, full round, white |
| Scope chips | ≈36pt tall, full round, 1.5pt outline |
| Task row | ≈72pt tall, 20–22pt radius, 10pt gaps |
| Checkbox | ≈26pt rounded square |
| FAB cluster | mic ≈48pt, plus ≈60pt |
| Bottom bar | ≈68pt, icon over label |
| Orb | ≈150pt |
| Language pill | ≈40pt, full round |
| Cards (composer) | 28–32pt radius, ≈8pt gaps |
| Display type | 34–40pt at weight ≈800, tracking ≈ −1.5% |

## Typeface

Numo's display face is a heavy geometric grotesque (single-storey `g`, tight
tracking, 800–900 weight). The project already ships Plus Jakarta Sans, whose ExtraBold
has the same single-storey `g`, high x-height and tight caps. Jev chose
`jakarta_extrabold` over adding Inter Tight or falling back to Roboto (p=0.97), so a
static 800 instance was generated from the upstream variable font
(`fonttools varLib.instancer`, `wght=800`) and committed next to the existing weights.

## Adopted / adapted / rejected

| Reference pattern | Decision here |
|---|---|
| Saturated header band with huge white title | **Adopted** on Tasks; single band per screen |
| Cream + white alternating task rows | **Adopted** (Jev D5) |
| Orange/red/green state orb | **Adopted**, mapped onto the existing six voice states |
| Expanding recording rings + bouncing dots | **Adopted**, disabled under reduced motion |
| Stacked white cards on a blue composer canvas | **Adopted** for Quick add and detail |
| Mic + plus FAB cluster | **Adopted** (mic starts capture from any tab) |
| S/M/L/XL effort circles | **Adopted**, mapped to 15/30/60/120 min estimates |
| Tag chips with inline remove | **Adopted** |
| Time wheel picker | **Adapted** to explicit time chips (no wheel widget; keeps Compose simple) |
| Public/Private visibility | **Adapted** to project scope + Inbox |
| Social layer (teams, kudos, friends, prompts, mascots) | **Rejected** — single-user product |
| Streak celebration gradient | **Adapted** to a completion-progress card (no gamification loop in v1) |
| Heatmap / 7-day stats | **Adopted** for the System screen |
| Per-list colour theming | **Rejected** (Jev D2): one brand accent + semantic green/orange |
| Bottom bar with 4 tabs | **Adapted** to the product's 3 tabs (Speak / Tasks / System) |
| `#7F7F7F` muted text | **Corrected** to `#6B6B70` (4.00:1 → 5.30:1) |
| White text on green/red/orange | **Corrected** to ink-on-hue or darker `*Fill` variants |

## Corrections forced by accessibility

The reference is not AA-compliant as shipped. Recomputing all pairs found nine failures
worth changing:

| Pair | Reference ratio | Resolution |
|---|---|---|
| `#7F7F7F` on white | 4.00 | muted → `#6B6B70` (5.30) |
| `#7F7F7F` on cream | 3.62 | same |
| `#0067FF` text on cream | 4.33 | `brandStrong` `#0054CC` (6.04) |
| white on `#0067FF` | 4.79 | kept (passes AA) |
| white on `#01C459` | 2.32 | `onSuccess` = black (9.04) |
| white on `#FF3E32` | 3.51 | buttons use `dangerFill` `#D92B20` (4.86) |
| `#FF3E32` as text | 3.51 | `dangerStrong` `#C62828` (5.62) |
| `#01C459` as text | 2.32 | `successStrong` `#067237` (6.05) |
| white on `#FF9901` | 2.14 | `onWarning` = black (9.81) |

`python3 .agents/design/tools/check_contrast.py` re-verifies the final 32 pairs.
