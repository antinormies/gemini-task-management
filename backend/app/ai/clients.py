from __future__ import annotations

import asyncio
import json
import os
import signal
import time
from dataclasses import dataclass, field
from pathlib import Path

import httpx

from ..config import Settings
from ..security import new_id
from .parser import ParseError, parse_envelope_or_text, parse_interpretation_text
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


class AgyClient:
    def __init__(self, settings: Settings):
        self.settings = settings

    async def run(self, prompt: str, schema_path: Path) -> AIResult:
        if not self.settings.agy_bin:
            raise AIError("AGY_NOT_FOUND", "agy is not configured")
        self.settings.workspace_dir.mkdir(parents=True, exist_ok=True)
        env = {
            "HOME": os.environ.get("HOME", ""),
            "PATH": os.environ.get("PATH", "/usr/local/bin:/usr/bin:/bin"),
            "LANG": os.environ.get("LANG", "C.UTF-8"),
            "TZ": self.settings.timezone,
        }
        cmd = [
            self.settings.agy_bin,
            "-p",
            prompt,
            "--output-format",
            "json",
            "--json-schema",
            str(schema_path),
            "--model",
            self.settings.agy_model,
            "--effort",
            self.settings.agy_effort,
            "--print-timeout",
            self.settings.agy_print_timeout,
            "--sandbox",
            "--disable-slash-commands",
        ]
        started = time.monotonic()
        try:
            proc = await asyncio.create_subprocess_exec(
                *cmd,
                cwd=str(self.settings.workspace_dir),
                env=env,
                stdin=asyncio.subprocess.DEVNULL,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE,
                start_new_session=True,
            )
        except FileNotFoundError as exc:
            raise AIError("AGY_NOT_FOUND", str(exc)) from exc
        try:
            out, err = await asyncio.wait_for(
                proc.communicate(), timeout=self.settings.agy_timeout_seconds
            )
        except TimeoutError as exc:
            try:
                os.killpg(proc.pid, signal.SIGTERM)
            except ProcessLookupError:
                pass
            raise AIError("AGY_TIMEOUT", "agy did not finish in time") from exc
        wall_ms = int((time.monotonic() - started) * 1000)
        if len(out) > 1_000_000:
            raise AIError("AGY_OUTPUT_TOO_LARGE", "agy output exceeded 1 MB")
        text = out.decode("utf-8", "replace")
        stderr = err.decode("utf-8", "replace")
        if proc.returncode != 0:
            raise AIError("AGY_EXIT", stderr[-1000:] or f"agy exited {proc.returncode}")
        try:
            envelope = json.loads(text)
        except json.JSONDecodeError as exc:
            raise AIError("AGY_INVALID_JSON", f"invalid JSON from agy: {exc}") from exc
        if envelope.get("status") != "SUCCESS":
            raise AIError("AGY_STATUS", str(envelope.get("error") or envelope.get("status")))
        if envelope.get("denied_actions"):
            raise AIError("AGY_TOOL_DENIED", str(envelope["denied_actions"]))
        data = envelope.get("structured_output")
        if data is None:
            try:
                data = parse_envelope_or_text(envelope.get("response", ""))
            except ParseError as exc:
                raise AIError("AGY_EMPTY_RESPONSE", str(exc)) from exc
        try:
            interpretation = parse_interpretation_text(json.dumps(data))
        except Exception as exc:
            raise AIError("AGY_SCHEMA_MISMATCH", str(exc)) from exc
        return AIResult(
            interpretation=interpretation,
            provider="agy",
            model=self.settings.agy_model,
            raw=envelope,
            duration_ms=wall_ms,
            usage=_usage_from(envelope.get("usage")),
        )


class OpenCodeClient:
    def __init__(self, settings: Settings):
        self.settings = settings

    async def run(self, prompt: str, system: str | None = None) -> AIResult:
        if not self.settings.opencode_api_key:
            raise AIError("OPENCODE_NOT_CONFIGURED", "GTM_OPENCODE_API_KEY is empty")
        url = f"{self.settings.opencode_base_url.rstrip('/')}/chat/completions"
        session = new_id()
        headers = {
            "Authorization": f"Bearer {self.settings.opencode_api_key}",
            "Content-Type": "application/json",
            "User-Agent": "gtm-backend/0.1",
            "x-opencode-session": session,
        }
        messages = []
        if system:
            messages.append({"role": "system", "content": system})
        messages.append({"role": "user", "content": prompt})
        base_body = {
            "model": self.settings.opencode_model,
            "messages": messages,
            "max_tokens": 2200,
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
        started = time.monotonic()
        last_error = ""
        async with httpx.AsyncClient(timeout=self.settings.opencode_timeout_seconds) as client:
            for body in variants:
                try:
                    response = await client.post(url, headers=headers, json=body)
                except Exception as exc:
                    raise AIError("OPENCODE_NETWORK", f"{type(exc).__name__}: {exc}") from exc
                if response.status_code == 200:
                    payload = response.json()
                    try:
                        content = payload["choices"][0]["message"]["content"]
                    except (KeyError, IndexError, TypeError) as exc:
                        raise AIError(
                            "OPENCODE_INVALID_RESPONSE", "missing choices content"
                        ) from exc
                    try:
                        interpretation = parse_interpretation_text(content)
                    except Exception as exc:
                        raise AIError("OPENCODE_SCHEMA_MISMATCH", str(exc)) from exc
                    return AIResult(
                        interpretation=interpretation,
                        provider="opencode",
                        model=self.settings.opencode_model,
                        raw=payload,
                        duration_ms=int((time.monotonic() - started) * 1000),
                        usage=_usage_from(payload.get("usage")),
                    )
                last_error = response.text[:800]
                if response.status_code not in (400, 404, 422):
                    break
        raise AIError("OPENCODE_ERROR", last_error or "OpenCode request failed")


class GeminiClient:
    def __init__(self, settings: Settings):
        self.settings = settings

    async def run(self, prompt: str) -> AIResult:
        if not self.settings.gemini_api_key:
            raise AIError("GEMINI_NOT_CONFIGURED", "GTM_GEMINI_API_KEY is empty")
        try:
            from google import genai
        except Exception as exc:
            raise AIError("GEMINI_SDK_MISSING", str(exc)) from exc
        started = time.monotonic()
        try:
            client = genai.Client(api_key=self.settings.gemini_api_key)
            response = client.models.generate_content(
                model=self.settings.gemini_model,
                contents=prompt,
                config={"response_mime_type": "application/json", "response_schema": SCHEMA},
            )
            text = response.text or ""
            interpretation = parse_interpretation_text(text)
            usage = getattr(response, "usage_metadata", None)
            usage_dict = {
                "promptTokenCount": getattr(usage, "prompt_token_count", None),
                "candidatesTokenCount": getattr(usage, "candidates_token_count", None),
                "totalTokenCount": getattr(usage, "total_token_count", None),
                "thoughtsTokenCount": getattr(usage, "thoughts_token_count", None),
            }
            return AIResult(
                interpretation=interpretation,
                provider="gemini",
                model=self.settings.gemini_model,
                raw={"text": text},
                duration_ms=int((time.monotonic() - started) * 1000),
                usage=_usage_from(usage_dict),
            )
        except AIError:
            raise
        except Exception as exc:
            raise AIError("GEMINI_ERROR", f"{type(exc).__name__}: {exc}") from exc
