from __future__ import annotations

from ..config import Settings
from .clients import AIError, AIResult, SelfProviderClient


class AIService:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.provider = SelfProviderClient(settings)

    def status(self) -> dict:
        return {
            "enabled": self.settings.ai_enabled,
            "provider": "self",
            "configured": bool(self.settings.ai_api_key),
            "model": self.settings.ai_model,
            "base_url": self.settings.ai_base_url,
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
        return await self.provider.run(prompt, system=system)
