# Design decision ledger (Jev)

Every decision below was made by **Jev** (`jev-latest`, TypeSafe System One) against
explicit criteria — not by preference. Requests, answers and confidence scores are
stored next to this file in `jev/`. Re-run:

```bash
python3 .agents/design/tools/jev_judge.py decide    # regenerate
python3 .agents/design/tools/jev_judge.py verify    # diff against the stored ledger
```

**Rule:** an answer below the confidence floor (`0.40`) is *not* applied; the code-side
fallback in `jev_judge.py` wins and the row says `fallback`. That keeps an uncertain
model answer from silently steering the build.

## Decisions

| # | Question | Jev answer | Confidence | Applied |
|---|---|---|---|---|
| D1 | Display typeface | `jakarta_extrabold` | 0.97 | Jev — static 800 instance generated + committed |
| D2 | Accent allocation | `brand_plus_semantic` | 0.83 | Jev — blue brand, green progress, orange attention, magenta celebration |
| D3 | Tasks default view | `list_first` | **0.10** | **fallback** → `list_first` (below floor; coincidentally the same answer, recorded as fallback) |
| D4 | Voice stage | `white_stage` | 0.83 | Jev — white stage, saturated state circle |
| D5 | Row surface rule | `alternating` | 0.96 | Jev — cream/white alternation, state shown by checkbox + meta |
| D6 | Board treatment | `white_cards_tinted_head` | 0.66 | Jev — white cards, tinted header chip + count |
| D7 | Radius scale | `rs_12_20_28` | 0.86 | Jev — 12 controls / 20 rows / 28 cards, pill actions |
| D8 | Motion budget | `functional_pulse` | 1.00 | Jev — only recording rings + processing dots |

Model `jev-1.14.0` · tokens used for the ledger: 1743 in / 396 out.

### Consequences worth stating

- **D3** is deliberately kept as a fallback. The model was torn (0.10 confidence), so the
  build does not depend on its opinion: Tasks opens on the grouped list, with Board one
  tap away in the header segmented control. If the team later prefers board-first, only
  `TasksScreen`'s initial mode changes — no token changes.
- **D5** overrides an earlier instinct to tint rows by state. Consequence: status must be
  legible through the checkbox, the trailing time and the priority dot, not through row
  fill. This is why `TaskRow` keeps a priority dot and never a coloured background.
- **D2** forbids per-project theming even though the reference does it. Consequence:
  `Project.color` data stays available but is used only for a 8dp dot in the meta line,
  never for headers.

## Palette roles

`python3 .agents/design/tools/jev_judge.py palette` classified the sampled reference
colours:

| Question | Answer | Confidence |
|---|---|---|
| brand primary | `#0067FF` | 1.00 |
| warm surface tint | `#FFF2DB` | 1.00 |
| success | `#01C459` | 1.00 |
| danger | `#FF3E32` | 1.00 |
| attention/warning | `#FF9901` | 0.97 |
| expressive accent | `#F549C8` | 0.65 |
| display ink | `#000000` | 1.00 |
| body ink | `#2F2F2F` | 0.97 |
| muted | `#7F7F7F` | 1.00 |

`#7F7F7F` was accepted as the *intended* muted role but is not usable as text at AA
(4.00:1), so `tokens.json` carries `#6B6B70` for text and keeps `#8A8A90` as a
decorative-only soft variant. The correction is mechanical, not a change of intent.

## Candidate shortlist (historical)

Asked before the pick, same model, per-dimension Score 0–2 with weights
voice .30 / workspace .30 / system .15 / transfer .25:

| Candidate | Voice | Workspace | System | Transfer | Weighted |
|---|---|---|---|---|---|
| **Numo** (chosen) | 1.99 | 1.20 | 0.93 | 0.59 | **1.244** |
| Superlist | 0.99 | 1.69 | 1.16 | 0.55 | 1.115 |
| Asana | 0.52 | 1.64 | 1.10 | 0.39 | 0.910 |

The `transfer` dimension scores low for every candidate because a text description
cannot prove a complete extractable system; coverage was instead verified manually by
screenshot count (33 screens for Numo). That is why it carries the smallest weight.

## What Jev is *not* deciding

Colour contrast, spacing arithmetic, touch-target sizes and type tracking are computed
or specified outright. A model opinion adds nothing to arithmetic — those are enforced
by `check_contrast.py` and the token file.

---

## Feature audit (v1 scope)

`python3 .agents/design/tools/jev_judge.py audit` — include / defer / reject per feature,
with the same 0.40 confidence floor.

| Feature | Jev | Confidence | Applied |
|---|---|---|---|
| Voice capture from any tab | include | 0.70 | include |
| Pull-to-refresh | include | 0.96 | include |
| Haptics on capture/apply | include | 0.72 | include |
| Header search | include | 0.55 | include |
| Project colour dot | include | 0.37 | **fallback** → include |
| Offline banner | include | 0.86 | include |
| Keyboard-aware composer | include | 0.70 | include |
| Skeleton loaders | include | 0.68 | include |
| Per-filter empty states | include | 0.50 | include |
| Swipe-to-complete | include | 0.28 | **fallback** → defer |
| Manual theme toggle | defer | 0.55 | defer |
| Completion celebration | defer | 0.70 | defer |
| Reduced-motion preference | defer | 0.35 | **fallback** → defer |
| Board status change | defer | 0.48 | **overridden** → include |
| 409 conflict notice | defer | 0.50 | **overridden** → include |

### Override policy

Two answers were overridden by an engineering constraint, and the constraint is recorded
rather than the preference: **a rebuild may change how an existing capability looks, but
must not remove it.** Board status changes and the 409 conflict notice already ship in
v0.1, so both are kept (status changes move to a long-press sheet; conflicts surface
through the shell toast plus retry).

This is the one place where a model judgment is deliberately outranked. Everything else in
this ledger is applied as answered, with fallbacks where confidence is below the floor.

## Design tournament (pairwise, Copeland)

Single-pick `Choice` questions were badly discriminated for design alternatives (several
answered at 0.19–0.49 confidence), which means all options were viable. Those questions
were re-asked as pairwise `Noul` comparisons and aggregated deterministically:

```bash
python3 .agents/design/tools/jev_judge.py tournament   # writes jev/tournament.json
```

| Question | Winner | Copeland | Notes |
|---|---|---|---|
| X1 Tasks header | `compact_band` | 2.0 | beat both the full band and the white header |
| X2 Board column width | `col_232` | 2.0 | 232dp keeps a neighbour visible |
| X3 Voice result | `below_orb` | 1.5 | tied with a sheet; declaration order breaks the tie |
| X4 Composer | `full_canvas` | 2.0 | reverses the single pick (`bottom_sheet`) |
| X5 System stats | `list_rows` | 2.0 | reverses the single pick (`grid_2x2`) |
| X6 Login | `split` | 1.5 | brand block + white credential panel |

16 pairwise comparisons, 6 decided winners, 2 reversals of the earlier answers — evidence
that the tournament was worth running rather than trusting a single low-confidence pick.
