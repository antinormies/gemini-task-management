---
name: antaratask-ui
description: Build or change AntaraTask AI's Android UI. Load before touching anything under mobile/app/src/main/java/id/ai/antaratask/ui, before adding a screen or component, or when reviewing a UI diff. Carries the token API, component contracts, screen specs, accessibility gates and the Jev decision ledger so new UI matches the Numo-derived system instead of inventing a new look.
---

# AntaraTask UI

The mobile UI is one design system, extracted from Numo (iOS) and encoded in
`design.md`. Read the relevant section of that file before writing a screen; this skill
is the working summary and the rules that are easy to get wrong.

## When to use

- Adding or restyling a screen, sheet, dialog or component in `mobile/`.
- Reviewing a UI diff for system drift.
- Changing a colour, a type style, a radius or a motion value.

Do **not** use it for the web UI in `backend/` or for release/CI work.

## Read order

1. `design.md` — tokens, components, screen specs, do/don't.
2. `.agents/design/tokens.json` — the machine values, single source of truth.
3. `.agents/design/decisions.md` — why the system looks like this (Jev ledger).
4. `.agents/design/numo-extraction.md` — the reference evidence, if you need to argue
   about fidelity.

## Token API

Never write a hex, a raw dp padding, or a font size in a screen file.

```kotlin
NumoTheme.colors.brand            // Color
NumoTheme.colors.surfaceWarm      // task-row cream
NumoTheme.colors.ink / inkBody / muted
NumoTheme.typography.displayL     // TextStyle (weight 800, tracking -1.2)
NumoTheme.typography.titleM
NumoTheme.spacing.gutter / md / xxl
NumoTheme.radii.row / card / pill
NumoTheme.motion.quick / base / pulse
```

Colour rules that bite:

- `brand` is a **fill**. As text on white/cream use `brandStrong`.
- Never `success` (`#01C459`), `danger` (`#FF3E32`), `warning` (`#FF9901`) or `magenta`
  (`#F549C8`) as small text. Use `successStrong`, `dangerStrong`, or the matching
  `on*` colour on a fill.
- One saturated region per screen. A `brand` header plus a `brand` composer canvas on
  the same screen is a bug.
- Dark palette is a real palette (`onBrand` becomes near-black). Do not invert.
- `Project.color` data is a decoration dot only — never a header or row fill (Jev D2).

## Type rules

- Weight 800 (`displayXL/L/M`, `titleL`, `numberXL/L`) is for titles and numbers only.
  Body, meta, chips, buttons use 500–700.
- Display styles are sentence case, max two lines at 393dp.
- Tabular numerals for times, counts, usage.
- Respect 200% font scale: rows use `heightIn(min = …)`, never a fixed height that clips.

## Component contracts

Shared UI lives in `ui/components/`; screens compose it. Do not copy a component into a
screen file. See `design.md` §5 for anatomy and sizes. The set:

`AppHeader` `FilterChips` `TaskRow` `NumoCheckbox` `FabCluster` `NumoBottomBar`
`StackCard` `PrimaryButton` `DestructiveButton` `SecondaryButton` `GhostButton`
`SectionTitle` `StatTile` `SettingsRow` `ToastPill` `NumoAlertDialog` `EmptyState`
`StateOrb` `Heatmap` `UsageBars` `StorageBar`.

Behavioural requirements that are not optional:

- `StateOrb` has six states and each has its own colour, glyph and spoken description.
- Recording rings / processing dots stop under reduced motion; the state stays legible.
- Every icon-only control has `contentDescription`; decorative glyphs use `null`.
- Touch targets ≥ 44dp, including chevrons and checkbox rows.

## Adding a screen

1. Find its section in `design.md` §6. If it is not there, add the spec first, then code.
2. Compose from the shared components; screens own layout and state, not styling.
3. Keep data wiring: screens receive `UiState` and `MainViewModel`, they never call the
   repository directly.
4. Cover the states in `design.md` §7 (loading, empty, error, offline).
5. Verify.

## Verification (required before claiming done)

```bash
python3 .agents/design/tools/check_contrast.py     # must print: All 32 pairs pass
cd mobile && ./gradlew :app:assembleDebug --no-daemon -PGTM_GOOGLE_CLIENT_ID=""
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n id.ai.antaratask/.MainActivity
adb exec-out screencap -p > /tmp/ui.png             # screenshot light + dark
```

A visual claim without a screenshot is not accepted. Also check dark mode, 200% font
scale (`adb shell settings put system font_scale 2.0`) and reduced motion.

## Known traps

- The reference's social layer (teams, kudos, prompt cards, mascots, magenta profiles,
  streaks-as-gamification) is **out of scope** — single-user product. Reuse the *layout*
  discipline, not the features.
- The reference palette is **not** AA-compliant; it was corrected. Do not "restore"
  `#7F7F7F` text, white-on-green buttons or white-on-orange buttons.
- Time-wheel picker was replaced by explicit chips; do not reintroduce a wheel.
- Per-list colour theming was rejected. Boards use one brand accent plus semantic status
  tints, not one hue per column.
- Changing a token means re-running the contrast gate and `jev_judge.py verify`, and
  updating `tokens.json` + `design.md` in the same commit.
