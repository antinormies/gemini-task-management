from __future__ import annotations

from functools import lru_cache
from pathlib import Path
from typing import Literal

from platformdirs import user_data_dir
from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=(".env", "backend/.env"),
        env_prefix="GTM_",
        extra="ignore",
    )

    host: str = "127.0.0.1"
    port: int = 8765
    data_dir: str = Field(default_factory=lambda: user_data_dir("gtm", "antinormies"))
    db_path: str = ""
    timezone: str = "Asia/Jakarta"
    work_start: str = "09:00"
    work_end: str = "17:00"
    public_origin: str = "http://127.0.0.1:8765"
    trust_proxy_headers: bool = True
    secret_key: str = ""
    pairing_ttl_seconds: int = 300
    pairing_max_attempts: int = 5
    log_level: str = "INFO"

    ai_enabled: bool = True
    ai_provider: Literal["auto", "agy", "opencode", "gemini"] = "auto"
    ai_max_concurrency: int = 1
    ai_timeout_seconds: int = 120
    ai_fake: bool = False

    agy_bin: str = "agy"
    agy_model: str = "gemini-3.8-flash-low"
    agy_effort: str = "low"
    agy_print_timeout: str = "90s"
    agy_workspace: str = ""
    agy_timeout_seconds: int = 120

    opencode_base_url: str = "https://opencode.ai/zen/go/v1"
    opencode_model: str = "deepseek-v4.1-flash"
    opencode_api_key: str = ""
    opencode_timeout_seconds: int = 90

    gemini_api_key: str = ""
    gemini_model: str = "gemini-3.8-flash"

    voice_enabled: bool = True
    voice_provider: Literal["auto", "agy", "opencode", "gemini"] = "auto"
    voice_max_seconds: int = 60
    voice_max_chars: int = 4000
    voice_timeout_seconds: int = 20

    @property
    def resolved_db_path(self) -> Path:
        if self.db_path:
            return Path(self.db_path).expanduser()
        return Path(self.data_dir).expanduser() / "gtm.db"

    @property
    def backups_dir(self) -> Path:
        return Path(self.data_dir).expanduser() / "backups"

    @property
    def logs_dir(self) -> Path:
        return Path(self.data_dir).expanduser() / "logs"

    @property
    def workspace_dir(self) -> Path:
        return (
            Path(self.agy_workspace).expanduser()
            if self.agy_workspace
            else Path(self.data_dir).expanduser() / "agy-workspace"
        )

    def prepare(self) -> None:
        Path(self.data_dir).expanduser().mkdir(parents=True, exist_ok=True)
        self.resolved_db_path.parent.mkdir(parents=True, exist_ok=True)
        self.backups_dir.mkdir(parents=True, exist_ok=True)
        self.logs_dir.mkdir(parents=True, exist_ok=True)
        self.workspace_dir.mkdir(parents=True, exist_ok=True)


@lru_cache
def get_settings() -> Settings:
    return Settings()
