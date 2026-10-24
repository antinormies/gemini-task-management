from __future__ import annotations

import time
from dataclasses import dataclass, field

import httpx

from ..config import Settings
from .parser import parse_interpretation_text
from .schemas import SCHEMA, Interpretation


class AIError(RuntimeError):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code
        self.message = message


@dataclass
class AIResult:
    interpretation: Interpretation
    provider: str
    model: str
    raw: dict = field(default_factory=dict)
    duration_ms: int = 0
    usage: dict = field(default_factory=dict)


def _usage_from(usage: dict | None) -> dict:
    usage = usage or {}
    total = usage.get("total_tokens") or usage.get("totalTokenCount")
    return {
        "input_tokens": usage.get("prompt_tokens") or usage.get("promptTokenCount"),
        "output_tokens": usage.get("completion_tokens") or usage.get("candidatesTokenCount"),
        "total_tokens": total,
        "thinking_tokens": usage.get("completion_tokens_details", {}).get("reasoning_tokens")
        or usage.get("thoughtsTokenCount"),
    }


class SelfProviderClient:
    """The single AI provider: the self-hosted gateway completion endpoint.

    The gateway owns provider credentials and upstream routing; GTM only holds
    its own service key. Requests are JSON (not SSE) and return {text, model,
    usage}.
    """

    def __init__(self, settings: Settings):
        self.settings = settings

    async def run(self, prompt: str, system: str | None = None) -> AIResult:
        if not self.settings.ai_api_key:
            raise AIError("AI_NOT_CONFIGURED", "GTM_AI_API_KEY is empty")
        if not self.settings.ai_base_url:
            raise AIError("AI_NOT_CONFIGURED", "GTM_AI_BASE_URL is empty")

        messages = []
        if system:
            messages.append({"role": "system", "content": system})
        messages.append({"role": "user", "content": prompt})
        base_body = {
            "model": self.settings.ai_model,
            "messages": messages,
            "max_tokens": 8192,
            "temperature": 0,
        }
        variants = [
            {
                **base_body,
                "response_format": {
                    "type": "json_schema",
                    "json_schema": {"name": "gtm_interpretation", "schema": SCHEMA, "strict": True},
                },
            },
            {**base_body, "response_format": {"type": "json_object"}},
            base_body,
        ]
        headers = {
            "Authorization": f"Bearer {self.settings.ai_api_key}",
            "Content-Type": "application/json",
            "Accept": "application/json",
            "User-Agent": "gtm-backend/0.1",
        }
        started = time.monotonic()
        last_error = ""
        async with httpx.AsyncClient(timeout=self.settings.ai_timeout_seconds) as client:
            for body in variants:
                try:
                    response = await client.post(
                        self.settings.ai_base_url, headers=headers, json=body
                    )
                except Exception as exc:
                    raise AIError("AI_NETWORK", f"{type(exc).__name__}: {exc}") from exc
                if response.status_code == 200:
                    payload = response.json()
                    text = payload.get("text")
                    if not text:
                        try:
                            text = payload["choices"][0]["message"]["content"]
                        except KeyError, IndexError, TypeError:
                            text = ""
                    if not text:
                        # Reasoning models occasionally return no content;
                        # try the next response-format variant instead.
                        last_error = "empty completion"
                        continue
                    try:
                        interpretation = parse_interpretation_text(text)
                    except Exception as exc:
                        raise AIError("AI_SCHEMA_MISMATCH", str(exc)) from exc
                    return AIResult(
                        interpretation=interpretation,
                        provider="self",
                        model=payload.get("model") or self.settings.ai_model,
                        raw=payload,
                        duration_ms=int((time.monotonic() - started) * 1000),
                        usage=_usage_from(payload.get("usage")),
                    )
                last_error = response.text[:800]
                if response.status_code not in (400, 404, 422, 502):
                    break
        raise AIError("AI_ERROR", last_error or "self provider request failed")
