from __future__ import annotations

import json
import re
from typing import Any

from .schemas import Interpretation, parse_interpretation


class ParseError(ValueError):
    pass


def extract_json(text: str) -> Any:
    if not text or not text.strip():
        raise ParseError("empty response")
    value = text.strip()
    if value.startswith("```"):
        value = value.strip("`")
        value = re.sub(r"^json\s*", "", value, flags=re.I).strip()
    try:
        return json.loads(value)
    except json.JSONDecodeError:
        pass
    start, end = value.find("{"), value.rfind("}")
    if start < 0 or end <= start:
        raise ParseError("no JSON object in response")
    try:
        return json.loads(value[start : end + 1])
    except json.JSONDecodeError as exc:
        raise ParseError(str(exc)) from exc


def parse_envelope_or_text(text: str) -> dict:
    data = extract_json(text)
    if not isinstance(data, dict):
        raise ParseError("JSON response is not an object")
    return data


def parse_interpretation_text(text: str) -> Interpretation:
    return parse_interpretation(parse_envelope_or_text(text))
