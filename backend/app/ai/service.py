from __future__ import annotations

import shutil

from ..config import Settings
from .clients import AgyClient, AIError, AIResult, GeminiClient, OpenCodeClient
from .schemas import SCHEMA_PATH


class AIService:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.agy = AgyClient(settings)
        self.opencode = OpenCodeClient(settings)
        self.gemini = GeminiClient(settings)

    def status(self) -> dict:
        return {
            "enabled": self.settings.ai_enabled,
            "provider": self.settings.ai_provider,
            "agy_installed": bool(shutil.which(self.settings.agy_bin)),
            "agy_model": self.settings.agy_model,
            "opencode_configured": bool(self.settings.opencode_api_key),
            "opencode_model": self.settings.opencode_model,
            "gemini_configured": bool(self.settings.gemini_api_key),
        }

    async def interpret(
        self,
        prompt: str,
        *,
        provider: str | None = None,
        system: str | None = None,
    ) -> AIResult:
        if not self.settings.ai_enabled:
            raise AIError("AI_DISABLED", "AI is disabled")
        preferred = provider or self.settings.ai_provider
        if preferred == "auto":
            order = ["agy", "opencode", "gemini"]
        else:
            order = [preferred]
        errors: list[str] = []
        for name in order:
            try:
                if name == "agy":
                    return await self.agy.run(prompt, SCHEMA_PATH)
                if name == "opencode":
                    return await self.opencode.run(prompt, system=system)
                if name == "gemini":
                    return await self.gemini.run(prompt)
            except AIError as exc:
                errors.append(f"{name}:{exc.code}")
                continue
            except Exception as exc:
                errors.append(f"{name}:{type(exc).__name__}")
                continue
        raise AIError("AI_UNAVAILABLE", "; ".join(errors) or "no provider available")
