from __future__ import annotations

import json
import time
import uuid
from dataclasses import dataclass, field

import httpx

from ..config import Settings
from .parser import parse_interpretation_text
from .schemas import Interpretation

DEFAULT_SYSTEM = (
    "You are a strict JSON task parser. Do not reason step by step. "
    "Return only the requested JSON object, with no prose or markdown."
)


def _consume_event(event: str, raw: str, text_parts: list[str], state: dict) -> None:
    if not raw:
        return
    try:
        data = json.loads(raw)
    except json.JSONDecodeError:
        return
    if event == "delta":
        piece = data.get("text")
        if isinstance(piece, str) and piece:
            text_parts.append(piece)
    elif event == "usage":
        state["usage"] = data
    elif event == "done":
        state["finish_reason"] = str(data.get("finishReason") or "")
    elif event == "error":
        raise AIError(
            "AI_STREAM_ERROR",
            str(data.get("message") or data.get("code") or "gateway stream error"),
        )


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


def _gateway_error(status: int, body: str) -> AIError:
    """Turn a gateway HTTP error into a stable, user-facing AI error."""
    code = ""
    message = ""
    request_id = ""
    try:
        data = json.loads(body)
        if isinstance(data, dict):
            code = str(data.get("code") or "")
            message = str(data.get("message") or "")
            request_id = str(data.get("requestId") or "")
    except json.JSONDecodeError, TypeError:
        pass
    reference = f" (request {request_id})" if request_id else ""
    if code == "UNAUTHENTICATED" and "firebase" in message.lower():
        return AIError(
            "AI_AUTH_FIREBASE",
            "AI is unavailable because the gateway requires a Firebase UID. "
            "GTM could not assert a UID for this request. Check the account UID configuration."
            + reference,
        )
    if status in (401, 403):
        return AIError(
            "AI_AUTH",
            "AI gateway authentication failed. Check the app client key and capability mapping."
            + reference,
        )
    if status == 402:
        return AIError(
            "AI_QUOTA",
            "AI credits are exhausted. Check the gateway balance." + reference,
        )
    if status == 429:
        return AIError(
            "AI_RATE_LIMIT",
            "AI is rate-limited. Try again in a moment." + reference,
        )
    if status >= 500:
        return AIError(
            "AI_UNAVAILABLE",
            "The AI gateway is temporarily unavailable. Try again." + reference,
        )
    return AIError(
        "AI_REQUEST",
        (message or "The AI gateway rejected the request.") + reference,
    )


class GatewayChatClient:
    """App-client chat stream against the MemeChat Gateway.

    The gateway authenticates the app with X-App-Client-Key, authorizes the
    requested capability, and streams SSE events: meta, delta, usage, done.
    A reasoning model occasionally emits only reasoning and no content; the
    request is retried once with a strict system instruction.
    """

    def __init__(self, settings: Settings):
        self.settings = settings

    async def run(
        self,
        prompt: str,
        system: str | None = None,
        firebase_uid: str | None = None,
    ) -> AIResult:
        if not self.settings.ai_api_key:
            raise AIError("AI_NOT_CONFIGURED", "GTM_AI_API_KEY is empty")
        if not self.settings.ai_base_url:
            raise AIError("AI_NOT_CONFIGURED", "GTM_AI_BASE_URL is empty")

        messages = [
            {"role": "system", "text": system or DEFAULT_SYSTEM},
            {"role": "user", "text": prompt},
        ]
        headers = {
            "X-App-Client-Key": self.settings.ai_api_key,
            "Content-Type": "application/json",
            "Accept": "text/event-stream",
            "User-Agent": "gtm-backend/0.1",
        }
        asserted_uid = (firebase_uid or self.settings.ai_firebase_uid).strip()
        if asserted_uid:
            headers["X-Firebase-Uid"] = asserted_uid
        timeout = httpx.Timeout(self.settings.ai_timeout_seconds, connect=20.0)
        started = time.monotonic()
        text = ""
        usage: dict = {}
        finish_reason = ""

        for _attempt in range(2):
            payload = {
                "requestId": str(uuid.uuid4()),
                "capability": self.settings.ai_capability,
                "messages": messages,
                "maxOutputTokens": self.settings.ai_max_output_tokens,
            }
            text_parts: list[str] = []
            state = {"usage": {}, "finish_reason": ""}
            event_name: str | None = None
            data_lines: list[str] = []

            async with httpx.AsyncClient(timeout=timeout) as client:
                try:
                    async with client.stream(
                        "POST", self.settings.ai_base_url, headers=headers, json=payload
                    ) as response:
                        if response.status_code != 200:
                            body = (await response.aread()).decode("utf-8", "replace")
                            raise _gateway_error(response.status_code, body)
                        async for line in response.aiter_lines():
                            if line.startswith(":"):
                                continue
                            if line.startswith("event:"):
                                event_name = line[6:].strip()
                                data_lines = []
                            elif line.startswith("data:"):
                                data_lines.append(line[5:].strip())
                            elif line == "":
                                if event_name and data_lines:
                                    _consume_event(
                                        event_name,
                                        "\n".join(data_lines),
                                        text_parts,
                                        state,
                                    )
                                event_name = None
                                data_lines = []
                        if event_name and data_lines:
                            _consume_event(
                                event_name,
                                "\n".join(data_lines),
                                text_parts,
                                state,
                            )
                except AIError:
                    raise
                except Exception as exc:
                    raise AIError("AI_NETWORK", f"{type(exc).__name__}: {exc}") from exc

            usage = state["usage"]
            finish_reason = state["finish_reason"]
            text = "".join(text_parts).strip()
            if text:
                break

        if not text:
            raise AIError("AI_EMPTY", "gateway returned no text")
        try:
            interpretation = parse_interpretation_text(text)
        except Exception as exc:
            raise AIError("AI_SCHEMA_MISMATCH", str(exc)) from exc

        input_tokens = usage.get("inputTokens")
        output_tokens = usage.get("outputTokens")
        total_tokens = None
        if isinstance(input_tokens, int) and isinstance(output_tokens, int):
            total_tokens = input_tokens + output_tokens
        return AIResult(
            interpretation=interpretation,
            provider="gateway",
            model=self.settings.ai_model,
            raw={"usage": usage, "finish_reason": finish_reason},
            duration_ms=int((time.monotonic() - started) * 1000),
            usage={
                "input_tokens": input_tokens,
                "output_tokens": output_tokens,
                "total_tokens": total_tokens,
                "thinking_tokens": None,
            },
        )
