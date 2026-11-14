#!/usr/bin/env python3
"""Jev (TypeSafe System One) decision runner for the AntaraTask UI rebuild.

Every visual rule in `design.md` that could otherwise be argued about is settled
here by an explicit, re-runnable judgment instead of taste. This script is the
source of the ledger in `.agents/design/jev/`.

Usage:
    python jev_judge.py palette     # sampled Numo colours -> semantic token roles
    python jev_judge.py decide      # the design decision ledger (D1..D8)
    python jev_judge.py verify      # re-run and diff against the stored ledger
    python jev_judge.py candidates  # Mobbin candidate ranking (historical)

Key: $TYPESAFE_API_KEY, else ~/.config/typesafe/api_key. Never commit the key.
"""

from __future__ import annotations

import hashlib
import json
import os
import pathlib
import sys
import urllib.error
import urllib.request

ENDPOINT = "https://api.typesafe.ai/v1/systemone"
MODEL = "jev-latest"
ROOT = pathlib.Path(__file__).resolve().parents[1]  # -> .agents/design
OUT = ROOT / "jev"
# Any answer below this confidence is not applied; the code-side default wins.
CONFIDENCE_FLOOR = 0.40


# --------------------------------------------------------------------------- #
# API
# --------------------------------------------------------------------------- #
def api_key() -> str:
    key = os.environ.get("TYPESAFE_API_KEY")
    if key:
        return key.strip()
    path = pathlib.Path.home() / ".config" / "typesafe" / "api_key"
    if path.exists():
        return path.read_text().strip()
    sys.exit("No TypeSafe API key: set TYPESAFE_API_KEY or ~/.config/typesafe/api_key")


def ask(state, questions, name: str) -> dict:
    body = json.dumps({"model": MODEL, "state": state, "questions": questions}).encode()
    req = urllib.request.Request(
        ENDPOINT,
        data=body,
        headers={"Authorization": f"Bearer {api_key()}", "Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(req, timeout=90) as resp:
            payload = json.load(resp)
    except urllib.error.HTTPError as exc:  # surface the API's message, not a traceback
        sys.exit(f"TypeSafe HTTP {exc.code}: {exc.read().decode()[:400]}")
    record = {
        "name": name,
        "model": payload["model"],
        "usage": payload["usage"],
        "state_sha256": hashlib.sha256(json.dumps(state, sort_keys=True).encode()).hexdigest()[:16],
        "questions_sha256": hashlib.sha256(json.dumps(questions, sort_keys=True).encode()).hexdigest()[:16],
        "answers": payload["answers"],
    }
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / f"{name}.json").write_text(json.dumps(record, indent=2) + "\n")
    return record


def show(record: dict) -> None:
    print(f"\n{record['name']}  (model {record['model']}, {record['usage']})")
    for q, ans in record["answers"].items():
        if ans["type"] == "choice":
            print(f"  {q:<22} -> {ans['choice']:<22} p={ans['confidence']:.2f}")
        elif ans["type"] == "score":
            print(f"  {q:<22} -> {ans['score']:.2f}  ({ans['legend']})")
        else:
            print(f"  {q:<22} -> {ans['noul']:.2f}")


# --------------------------------------------------------------------------- #
# palette: classify the sampled Numo colours into semantic roles
# --------------------------------------------------------------------------- #
PALETTE_STATE = {
    "app": "Numo, an iOS voice-first ADHD task manager. Rebuilding the Android app "
    "AntaraTask AI with this visual language: voice capture stage, Today task list, "
    "board, task detail sheet, diagnostics screen.",
    "samples": [
        {"hex": "#0067FF", "where": "full-bleed header, primary CTA pills, FAB, active tab, selected chips (largest chroma area)"},
        {"hex": "#FFF2DB", "where": "task row surface alternating with white on the blue Today screen"},
        {"hex": "#D7F5E4", "where": "task row surface on the green Today screen"},
        {"hex": "#01C459", "where": "'Your tasks incoming' processing orb, heatmap completed cells, check badges"},
        {"hex": "#FF3E32", "where": "recording orb + expanding rings, error copy, streak flame"},
        {"hex": "#FF9901", "where": "idle 'Hold and speak' mic orb, reward pill, celebration gradient"},
        {"hex": "#FD31C8", "where": "profile prompt cards in the social/member screens"},
        {"hex": "#2F2F2F", "where": "body text, numbers, secondary headings"},
        {"hex": "#000000", "where": "large display titles and heavy task titles"},
        {"hex": "#7F7F7F", "where": "timestamps, meta labels, placeholders"},
        {"hex": "#F3F3F3", "where": "empty heatmap cells, dividers, inert backgrounds"},
        {"hex": "#F549C8", "where": "alternate magenta card fill"},
    ],
}

PALETTE_QUESTIONS = {
    "primary": {
        "type": "choice",
        "instructions": "Which sampled colour acts as the brand primary: the one that carries the main action and identity?",
        "criteria": {c: c for c in ["#0067FF", "#01C459", "#FF3E32", "#FF9901", "#FD31C8", "#2F2F2F"]},
    },
    "warm_surface": {
        "type": "choice",
        "instructions": "Which colour is the warm task-row surface tint?",
        "criteria": {c: c for c in ["#FFF2DB", "#D7F5E4", "#F3F3F3", "#7F7F7F"]},
    },
    "success": {
        "type": "choice",
        "instructions": "Which colour communicates success/completion?",
        "criteria": {c: c for c in ["#01C459", "#0067FF", "#FF9901", "#2F2F2F"]},
    },
    "danger": {
        "type": "choice",
        "instructions": "Which colour communicates destructive action, recording, or error?",
        "criteria": {c: c for c in ["#FF3E32", "#FD31C8", "#FF9901", "#0067FF"]},
    },
    "warning": {
        "type": "choice",
        "instructions": "Which colour communicates attention / 'action needed' without being an error?",
        "criteria": {c: c for c in ["#FF9901", "#01C459", "#FD31C8", "#F3F3F3"]},
    },
    "accent_social": {
        "type": "choice",
        "instructions": "Which colour is the decorative second accent used for expressive, non-semantic surfaces?",
        "criteria": {c: c for c in ["#FD31C8", "#FF9901", "#01C459", "#F549C8"]},
    },
    "ink": {
        "type": "choice",
        "instructions": "Which colour is the primary text ink for display titles?",
        "criteria": {c: c for c in ["#000000", "#2F2F2F", "#7F7F7F", "#0067FF"]},
    },
    "ink_body": {
        "type": "choice",
        "instructions": "Which colour is the body-copy ink (task titles, values)?",
        "criteria": {c: c for c in ["#2F2F2F", "#000000", "#7F7F7F", "#0067FF"]},
    },
    "muted": {
        "type": "choice",
        "instructions": "Which colour is used for de-emphasised meta text and placeholders?",
        "criteria": {c: c for c in ["#7F7F7F", "#2F2F2F", "#F3F3F3", "#D7F5E4"]},
    },
}


# --------------------------------------------------------------------------- #
# decide: the design decision ledger
# --------------------------------------------------------------------------- #
DECIDE_STATE = {
    "project": "AntaraTask AI (formerly GTM): Android Jetpack Compose task manager. "
    "minSdk 31, targetSdk 36, single Activity. Screens: Google sign-in, Speak (voice orb "
    "with idle/connecting/listening/captured/error, live transcript, capture card), Tasks "
    "(board To do/In progress/Waiting/Done + list grouped by project, filters, quick add, "
    "search), task detail bottom sheet, System (stats, 7-day AI usage, storage, engine, "
    "destructive actions). Existing code ships Plus Jakarta Sans 400/500/600/700, an "
    "Apple-like white/blue system and a lavender dark voice stage.",
    "reference": "Numo iOS. Verified tokens: brand #0067FF, warm surface #FFF2DB, success "
    "#01C459, danger #FF3E32, warning/orange #FF9901, social accent #FD31C8, ink #2F2F2F, "
    "muted #7F7F7F, empty #F3F3F3. Verified patterns: saturated full-bleed header with huge "
    "white 'Today' title + streak pill + avatar; outlined white chips on the coloured band; "
    "list rows 20-24 radius alternating warm tint and white with a rounded-square checkbox "
    "and a trailing time; mic + '+' FAB cluster above a 4-tab bar; white voice screen with a "
    "heavy two-line title, a 150dp state circle (orange idle, red recording with expanding "
    "rings, green processing with three bouncing dots, red error) and an outlined language "
    "pill; task composer as stacked white cards on the blue canvas with S/M/L/XL effort "
    "chips, Task/Routines tiles, tag chips, private note and a circular submit FAB; detail "
    "as white stacked cards.",
    "constraints": "Keep the existing data/API layer, Google auth, and server-side proposal "
    "flow untouched. Must work in light and dark. Must honour 200% font scale, 44dp touch "
    "targets, and reduced motion. No new paid dependencies.",
}

DECIDE_QUESTIONS = {
    "D1_display_font": {
        "type": "choice",
        "instructions": "Which display typeface best reproduces Numo's heavy grotesque display tone while staying consistent with the project's existing brand asset?",
        "criteria": {
            "jakarta_extrabold": "Bundle a static Plus Jakarta Sans ExtraBold (800) instance next to the existing 400/500/600/700 weights; already the project's family, SIL OFL",
            "inter_tight_black": "Add Inter Tight 800/900 as a new family for display only; closer to the reference, but a second family to maintain",
            "roboto_black": "Use the platform Roboto Black; zero assets, but the generic system look loses the reference character",
        },
    },
    "D2_accent_model": {
        "type": "choice",
        "instructions": "How should accent colour be allocated across the app's tabs?",
        "criteria": {
            "single_brand": "One brand blue for every tab and control; predictable, calm, least risk of semantic confusion",
            "brand_plus_semantic": "Brand blue for Speak/Tasks; success green reserved for progress/completion surfaces; magenta only for expressive profile/celebration moments",
            "per_list_theming": "Let each project/list own a colour that themes its header and row tint, as Numo does per list",
        },
    },
    "D3_tasks_default_view": {
        "type": "choice",
        "instructions": "Given Numo is list-first and this project historically defaults to a board, which should the Tasks tab open on?",
        "criteria": {
            "list_first": "Open on the grouped list; keep Board as a segmented second view",
            "board_first": "Open on the status board; keep List as the second view",
        },
    },
    "D4_voice_stage": {
        "type": "choice",
        "instructions": "Which voice-stage treatment should the Speak tab use?",
        "criteria": {
            "white_stage": "White stage, heavy black two-line title, single saturated state circle that changes orange/red/green (reference behaviour)",
            "dark_stage": "Keep a dark stage with the current glowing orb and waveform; more theatre, less consistent with the rest of the rebuilt app",
            "tinted_stage": "Soft brand-tinted stage with a white state card and saturated circle",
        },
    },
    "D5_row_surface": {
        "type": "choice",
        "instructions": "What rule decides a task row's surface fill?",
        "criteria": {
            "warm_default": "Warm cream row by default, white for completed/neutral rows (reference look)",
            "state_tint": "Soft accent tint when a task is due today or high priority, white otherwise",
            "alternating": "Alternate cream and white purely for rhythm, independent of state",
        },
    },
    "D6_board_treatment": {
        "type": "choice",
        "instructions": "How should the status board columns be rendered in this system?",
        "criteria": {
            "white_cards_tinted_head": "White cards on the app background with a tinted status header chip and count pill per column",
            "cream_canvas_cards": "Cream cards on white columns; colour only in the header chips",
            "filled_columns": "Fully filled tinted column backgrounds with white cards",
        },
    },
    "D7_radius_scale": {
        "type": "choice",
        "instructions": "Which corner-radius scale should the system use?",
        "criteria": {
            "rs_12_20_28": "12 chips/inputs, 20 list rows, 28 cards and sheets, pill for actions",
            "rs_16_24_32": "16 / 24 / 32 with pill actions; chunkier, further from the reference",
            "rs_8_16_24": "8 / 16 / 24; tighter, more utilitarian",
        },
    },
    "D8_motion": {
        "type": "choice",
        "instructions": "Which motion budget fits a voice-first capture app that must respect reduced-motion settings?",
        "criteria": {
            "functional_pulse": "150ms/220ms transitions, a pulse only while actually recording, three-dot bounce only while processing, everything static under reduced motion",
            "expressive": "Add ambient orb breathing, staggered list entrance and parallax headers on top of the functional set",
            "minimal": "Colour and state swaps only, no pulses or loops at all",
        },
    },
}

# Code-side defaults: applied when Jev is below CONFIDENCE_FLOOR, so the build is
# never blocked by an uncertain answer.
DECIDE_FALLBACK = {
    "D1_display_font": "jakarta_extrabold",
    "D2_accent_model": "brand_plus_semantic",
    "D3_tasks_default_view": "list_first",
    "D4_voice_stage": "white_stage",
    "D5_row_surface": "warm_default",
    "D6_board_treatment": "white_cards_tinted_head",
    "D7_radius_scale": "rs_12_20_28",
    "D8_motion": "functional_pulse",
}


def resolve(record: dict, fallback: dict) -> dict:
    """Apply the confidence floor deterministically and record why."""
    out = {}
    for q, ans in record["answers"].items():
        confident = ans.get("confidence", 1.0) >= CONFIDENCE_FLOOR
        out[q] = {
            "choice": ans["choice"] if confident else fallback[q],
            "jev_choice": ans["choice"],
            "confidence": round(ans.get("confidence", 1.0), 3),
            "applied": "jev" if confident else "fallback",
        }
    return out


# --------------------------------------------------------------------------- #
# candidates: historical record of the Mobbin shortlist ranking
# --------------------------------------------------------------------------- #
CANDIDATES_STATE = {
    "app": "AntaraTask AI - Android task manager, voice-first. Tabs: Speak (voice orb, 5 states, "
    "transcript, capture card, proposal review sheet), Tasks (Board + List, filters, quick add, "
    "search), System (stats, 7-day AI usage chart, storage, engine facts). Google sign-in.",
    "candidates": {
        "Numo": "Hold-to-speak mic hero with explicit states: orange idle mic, red stop while "
        "recording, green processing 'Your tasks incoming', AI sparkle badge, language pill. Bold "
        "colour-blocked Today rows, routine chips, mic + '+' FAB cluster, heavy display type.",
        "Superlist": "Warm off-white surfaces, heavy ink display type, task rows with checkbox + "
        "metadata lines, detail page with Due date/Assignee/Label chips, subtasks with red "
        "strikethrough, 'New task' pill + voice-note waveform icon, overflow bottom sheet.",
        "Asana": "Indigo gradient header, status board columns with per-column coloured header "
        "chips, floating 'New task' pill, avatar chips, bottom tab bar, white cards on tinted canvas.",
    },
}
CANDIDATE_WEIGHTS = {"voice": 0.30, "workspace": 0.30, "system": 0.15, "transfer": 0.25}


def candidate_questions() -> dict:
    q = {}
    for c in CANDIDATES_STATE["candidates"]:
        k = c.lower()
        q[f"{k}_voice"] = {
            "type": "score",
            "instructions": f"How well does {c}'s design language support a voice-first capture screen with explicit recording states and an AI result?",
            "criteria": ["Unrelated to voice", "Voice exists but secondary", "Voice is the hero of the product"],
        }
        q[f"{k}_workspace"] = {
            "type": "score",
            "instructions": f"How directly does {c}'s task-workspace UI map onto a task manager with projects, estimates, priorities and status transitions?",
            "criteria": ["No task workspace", "Partial task UI", "Directly a task-workspace reference"],
        }
        q[f"{k}_system"] = {
            "type": "score",
            "instructions": f"How suitable is {c}'s visual system for a diagnostics screen with stat tiles, a 7-day usage chart and destructive actions?",
            "criteria": ["Unsuitable", "Workable with adaptation", "Directly suited"],
        }
        q[f"{k}_transfer"] = {
            "type": "score",
            "instructions": f"How completely can {c}'s design system (tokens, type, spacing, radii, components) be extracted and rebuilt in Jetpack Compose?",
            "criteria": ["Sparse / hard to extract", "Usable but incomplete", "Complete, self-consistent system"],
        }
    return q


# --------------------------------------------------------------------------- #
def cmd_palette() -> None:
    rec = ask(PALETTE_STATE, PALETTE_QUESTIONS, "palette")
    show(rec)


def cmd_decide() -> dict:
    rec = ask(DECIDE_STATE, DECIDE_QUESTIONS, "decide")
    show(rec)
    resolved = resolve(rec, DECIDE_FALLBACK)
    (OUT / "decide.resolved.json").write_text(json.dumps(resolved, indent=2) + "\n")
    print("\nresolved (confidence floor %.2f):" % CONFIDENCE_FLOOR)
    for q, v in resolved.items():
        print(f"  {q:<24} {v['choice']:<26} conf={v['confidence']:.2f} via {v['applied']}")
    return resolved


def cmd_verify() -> int:
    path = OUT / "decide.json"
    if not path.exists():
        print("No ledger at", path, "- run `decide` first")
        return 2
    old = json.loads(path.read_text())
    rec = ask(DECIDE_STATE, DECIDE_QUESTIONS, "decide.verify")
    drift = 0
    for q in DECIDE_QUESTIONS:
        a, b = old["answers"][q]["choice"], rec["answers"][q]["choice"]
        if a != b:
            drift += 1
            print(f"DRIFT {q}: {a} -> {b}")
        else:
            print(f"same  {q}: {a}")
    print(f"\n{drift} drifted of {len(DECIDE_QUESTIONS)}")
    return 1 if drift else 0


def cmd_candidates() -> None:
    rec = ask(CANDIDATES_STATE, candidate_questions(), "candidates")
    rankings = []
    for c in CANDIDATES_STATE["candidates"]:
        k = c.lower()
        scores = {d: rec["answers"][f"{k}_{d}"]["score"] for d in CANDIDATE_WEIGHTS}
        total = round(sum(scores[d] * w for d, w in CANDIDATE_WEIGHTS.items()), 3)
        rankings.append({"candidate": c, "dimensions": scores, "weighted": total})
    rankings.sort(key=lambda r: -r["weighted"])
    (OUT / "candidates.ranking.json").write_text(json.dumps(rankings, indent=2) + "\n")
    for r in rankings:
        d = r["dimensions"]
        print(f"{r['candidate']:<11} voice={d['voice']:.2f} work={d['workspace']:.2f} "
              f"system={d['system']:.2f} transfer={d['transfer']:.2f} -> {r['weighted']:.3f}")


# --------------------------------------------------------------------------- #
# audit: every remaining design and feature possibility, include or defer
# --------------------------------------------------------------------------- #
AUDIT_STATE = {
    "product": "AntaraTask AI Android app, rebuild of the mobile UI onto the Numo-derived "
    "system described below. Stack: Kotlin + Jetpack Compose, single Activity, minSdk 31. "
    "Data: FastAPI backend over HTTPS bearer auth; tasks have status "
    "(inbox/todo/in_progress/waiting/done), priority (urgent/high/medium/low/none), "
    "estimate_minutes, due_at, project, tags, subtasks, notes, version. Voice: on-device "
    "SpeechRecognizer produces a transcript; the server returns a validated proposal the "
    "user must confirm. Tabs: Speak / Tasks / System.",
    "design_system": "Numo-derived: brand #0067FF fill, cream #FFF2DB rows alternating "
    "with white, 800-weight display type, radius 12/20/28 + pill, saturating one region "
    "per screen, functional-only motion (recording rings, processing dots), white voice "
    "stage with a 150dp state circle, composer as stacked white cards on the blue canvas, "
    "FAB cluster (48dp mic + 60dp plus), 3-tab bottom bar.",
    "constraints": "v1 must stay thin-client and single-user. No offline writes, no teams, "
    "no push, no gamification. Every AI action requires explicit confirmation. Screens "
    "must be buildable in Compose within this workstream and verifiable on an emulator.",
}

FEATURES = [
    ("F1_voice_from_any_tab", "Starting voice capture from the Tasks or System tab via the mic FAB, instead of only from the Speak tab"),
    ("F2_swipe_to_complete", "Swipe a task row horizontally to complete or reopen it"),
    ("F3_pull_to_refresh", "Pull-to-refresh gesture on the Tasks list"),
    ("F4_manual_theme_toggle", "An in-app light/dark switch, in addition to following the system setting"),
    ("F5_haptics", "Haptic feedback on capture completion, apply, and destructive confirm"),
    ("F6_board_drag", "Long-press drag between board columns with edge auto-scroll"),
    ("F7_header_search", "A search icon in the Tasks header opening a full-screen search sheet"),
    ("F8_completion_celebration", "A completion-progress card after finishing tasks (streak-like, adapted, no gamification)"),
    ("F9_project_color_dot", "An 8dp project colour dot in the task meta line"),
    ("F10_offline_banner", "A warning banner with Retry when the backend is unreachable"),
    ("F11_composer_ime", "Keyboard-aware composer where the submit FAB floats above the IME"),
    ("F12_skeleton_loaders", "Warm-tinted skeleton rows while the first page loads"),
    ("F13_empty_states", "Per-filter empty states with a primary action"),
    ("F14_conflict_notice", "A 409 version-conflict notice inside the detail sheet that keeps edits on screen"),
    ("F15_reduced_motion_toggle", "An explicit in-app 'reduce motion' preference in System"),
]

DESIGNS = [
    ("X1_tasks_header", "Tasks header composition", {
        "saturated_band": "Full-bleed brand band with heavy white 'Today' title, streak pill, chips underneath",
        "white_header": "White header with brand-coloured heavy title and chips below; colour only on the FAB",
        "compact_band": "Shorter brand band, title inline with chips, more list visible",
    }),
    ("X2_board_column", "Board column width", {
        "col_260": "260dp fixed columns with 12dp gaps",
        "col_82pct": "82% of screen width so the next column peeks",
        "col_232": "232dp columns, more adjacent context",
    }),
    ("X3_speak_result", "Where the voice result appears", {
        "below_orb": "Result card replaces the idle tip area below the orb, orb stays visible",
        "in_place": "Result card replaces the orb entirely once captured",
        "sheet": "Result opens as a bottom sheet over the voice stage",
    }),
    ("X4_composer", "Quick-add surface", {
        "full_canvas": "Full-screen blue canvas with stacked white cards",
        "bottom_sheet": "Bottom sheet with the same cards on a white sheet",
        "inline_page": "Pushed page with a white background and card sections",
    }),
    ("X5_system_stats", "System stat presentation", {
        "grid_2x2": "2x2 grid of warm stat cards with 44sp numbers",
        "row_scroll": "Horizontally scrolling stat cards",
        "list_rows": "Plain settings-style rows with values right-aligned",
    }),
    ("X6_login", "Login layout", {
        "brand_hero": "Brand mark + display hero + warm explainer card + Google button",
        "minimal": "Wordmark, one line of copy, Google button, lots of white",
        "split": "Coloured top half with mark, white card bottom with the button",
    }),
]


def audit_questions() -> dict:
    q = {}
    for key, desc in FEATURES:
        q[key] = {
            "type": "choice",
            "instructions": f"Should v1 of this rebuild include this feature: {desc}?",
            "criteria": {
                "include_v1": "Belongs in this v1 rebuild; the design system and existing data support it without new backend work",
                "defer_v1_1": "Valuable but out of scope for this rebuild",
                "reject": "Should not be built at all for this product",
            },
        }
    for key, label, options in DESIGNS:
        q[key] = {
            "type": "choice",
            "instructions": f"Which option is the strongest design for {label}, judged against the token system, the reference, and the product's voice-first identity?",
            "criteria": options,
        }
    return q


AUDIT_FALLBACK = {
    "F1_voice_from_any_tab": "include_v1",
    "F2_swipe_to_complete": "defer_v1_1",
    "F3_pull_to_refresh": "defer_v1_1",
    "F4_manual_theme_toggle": "defer_v1_1",
    "F5_haptics": "include_v1",
    "F6_board_drag": "include_v1",
    "F7_header_search": "include_v1",
    "F8_completion_celebration": "defer_v1_1",
    "F9_project_color_dot": "include_v1",
    "F10_offline_banner": "include_v1",
    "F11_composer_ime": "include_v1",
    "F12_skeleton_loaders": "include_v1",
    "F13_empty_states": "include_v1",
    "F14_conflict_notice": "include_v1",
    "F15_reduced_motion_toggle": "defer_v1_1",
    "X1_tasks_header": "saturated_band",
    "X2_board_column": "col_82pct",
    "X3_speak_result": "below_orb",
    "X4_composer": "full_canvas",
    "X5_system_stats": "grid_2x2",
    "X6_login": "brand_hero",
}


def cmd_audit() -> None:
    rec = ask(AUDIT_STATE, audit_questions(), "audit")
    show(rec)
    resolved = resolve(rec, AUDIT_FALLBACK)
    (OUT / "audit.resolved.json").write_text(json.dumps(resolved, indent=2) + "\n")
    print("\nresolved:")
    for q, v in resolved.items():
        print(f"  {q:<28} {v['choice']:<16} conf={v['confidence']:.2f} via {v['applied']}")



# --------------------------------------------------------------------------- #
# tournament: pairwise Noul comparisons + Copeland aggregation for design options
# --------------------------------------------------------------------------- #
TOURNAMENT_PROMPT = (
    "Judged against the design system and product context, is {a} the stronger design "
    "choice than {b} for {label}? Answer yes only if it is clearly better on usability, "
    "consistency with the token system, and reference fidelity; answer no when they are "
    "close enough that the difference does not justify a preference."
)


def cmd_tournament() -> None:
    state = {"context": AUDIT_STATE}
    pairs = []
    for label_key, label, options in DESIGNS:
        names = list(options)
        for i, a in enumerate(names):
            for b in names[i + 1:]:
                pairs.append((label_key, label, options, a, b))

    questions = {}
    for idx, (label_key, label, options, a, b) in enumerate(pairs):
        questions[f"p{idx}"] = {
            "type": "noul",
            "instructions": TOURNAMENT_PROMPT.format(
                a=f"{label_key} option '{a}' ({options[a]})",
                b=f"option '{b}' ({options[b]})",
                label=label,
            ),
        }
    rec = ask(state, questions, "tournament")

    # Copeland: +1 win, +0.5 tie (0.4..0.6 band is treated as no decision), 0 loss.
    score = {}
    results = {}
    for idx, (label_key, label, options, a, b) in enumerate(pairs):
        p_yes = rec["answers"][f"p{idx}"]["noul"]
        if p_yes >= 0.6:
            winner, loser = a, b
        elif p_yes <= 0.4:
            winner, loser = b, a
        else:
            winner = loser = None
        results[f"{a} vs {b}"] = {"p": round(p_yes, 3), "winner": winner or "tie"}
        score.setdefault(label_key, {}).setdefault(a, 0.0)
        score.setdefault(label_key, {}).setdefault(b, 0.0)
        if winner:
            score[label_key][winner] += 1.0
        else:
            score[label_key][a] += 0.5
            score[label_key][b] += 0.5

    winners = {}
    print(f"\n{'question':<28}{'option':<18}{'copeland':>9}")
    for label_key, label, options in DESIGNS:
        table = score.get(label_key, {})
        ordered = sorted(table.items(), key=lambda kv: (-kv[1], list(options).index(kv[0])))
        winners[label_key] = ordered[0][0]
        for name, value in ordered:
            print(f"{label_key:<28}{name:<18}{value:>9.1f}")
        print()

    for pair, r in results.items():
        print(f"  {pair:<48} p={r['p']:.2f} -> {r['winner']}")

    payload = {"model": rec["model"], "usage": rec["usage"], "pairs": results, "copeland": score, "winners": winners}
    (OUT / "tournament.json").write_text(json.dumps(payload, indent=2) + "\n")
    print("\nwinners:")
    for k, v in winners.items():
        print(f"  {k:<28} {v}")


def main() -> int:
    cmd = sys.argv[1] if len(sys.argv) > 1 else "decide"
    if cmd == "palette":
        cmd_palette()
    elif cmd == "decide":
        cmd_decide()
    elif cmd == "audit":
        cmd_audit()
    elif cmd == "tournament":
        cmd_tournament()
    elif cmd == "verify":
        return cmd_verify()
    elif cmd == "candidates":
        cmd_candidates()
    else:
        print(__doc__)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
