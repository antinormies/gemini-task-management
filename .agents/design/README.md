# Design artifacts

Everything here supports `design.md` at the repo root. Nothing here is generated at
build time; the app reads `tokens.json` values only through `ui/theme/Theme.kt`.

| Path | What it is | How to refresh |
|---|---|---|
| `tokens.json` | machine-readable colour, type, spacing, radius, motion tokens + the WCAG pairs that must pass | edit by hand, then run the contrast gate |
| `numo-extraction.md` | evidence: Mobbin inventory, sampled palettes, strip scans, adopted/rejected table | re-sample with Pillow if the reference changes |
| `decisions.md` | Jev decision ledger (D1–D8 + palette roles + candidate ranking) | `python3 tools/jev_judge.py decide` |
| `jev/*.json` | raw requests/answers/confidence/usage for every judgment | `jev_judge.py decide\|palette\|candidates` |
| `tools/jev_judge.py` | runs the judgments and diffs the ledger (`verify`) | stdlib only, no venv needed |
| `tools/check_contrast.py` | WCAG gate over `tokens.json` | `python3 tools/check_contrast.py` |
| `reference/numo/` | 33 reference screenshots (webp, @3x) | downloaded from Mobbin; links in `numo-extraction.md` |

## Why both a model and a script

- **Jev** decides what is a judgement call: which typeface, how accents are allocated,
  which surface rule to use, how much motion. Answers below the confidence floor are
  discarded in favour of a recorded fallback.
- **Scripts** do what is arithmetic: contrast ratios, token serialisation, ledger drift.
  No model opinion is used where a formula settles the question.

## Verifying the whole chain

```bash
python3 .agents/design/tools/check_contrast.py     # ~0.1s, 32 pairs
python3 .agents/design/tools/jev_judge.py verify   # needs TYPESAFE_API_KEY
```

`jev_judge.py verify` re-asks the questions and reports per-question drift; a change in
an answer means the reference interpretation or the criteria need review — it does not
silently change the app, because `Theme.kt` carries the resolved values.

The API key is read from `$TYPESAFE_API_KEY` or `~/.config/typesafe/api_key` and is never
written into the repository.
