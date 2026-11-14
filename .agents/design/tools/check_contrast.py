#!/usr/bin/env python3
"""Contrast gate for .agents/design/tokens.json.

Fails (exit 1) if any declared foreground/background pair is below its WCAG 2.2
target. Run it after touching a colour token; it is the reason the token set can
be trusted without re-checking by eye.

    python3 .agents/design/tools/check_contrast.py
"""

from __future__ import annotations

import json
import pathlib
import sys

TOKENS = pathlib.Path(__file__).resolve().parents[1] / "tokens.json"


def channel(value: float) -> float:
    value /= 255
    return value / 12.92 if value <= 0.03928 else ((value + 0.055) / 1.055) ** 2.4


def luminance(hex_color: str) -> float:
    raw = hex_color.lstrip("#")
    r, g, b = (int(raw[i : i + 2], 16) for i in (0, 2, 4))
    return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)


def ratio(fg: str, bg: str) -> float:
    a, b = luminance(fg), luminance(bg)
    hi, lo = max(a, b), min(a, b)
    return (hi + 0.05) / (lo + 0.05)


def main() -> int:
    data = json.loads(TOKENS.read_text())
    failures = []
    print(f"{'mode':<6}{'foreground':<16}{'background':<16}{'ratio':>7}{'min':>7}  result")
    for mode, fg_key, bg_key, minimum in data["contrast_pairs"]:
        palette = data["color"][mode]
        try:
            fg, bg = palette[fg_key], palette[bg_key]
        except KeyError as exc:
            failures.append(f"{mode}: unknown token {exc}")
            continue
        value = ratio(fg, bg)
        ok = value >= minimum
        if not ok:
            failures.append(f"{mode} {fg_key} on {bg_key}: {value:.2f} < {minimum}")
        print(f"{mode:<6}{fg_key:<16}{bg_key:<16}{value:>7.2f}{minimum:>7.1f}  {'ok' if ok else 'FAIL'}")

    if failures:
        print("\nCONTRAST FAILURES")
        for line in failures:
            print(" -", line)
        return 1
    print(f"\nAll {len(data['contrast_pairs'])} pairs pass WCAG AA.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
