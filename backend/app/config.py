from __future__ import annotations

from functools import lru_cache
from pathlib import Path

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
    google_client_id: str = ""
    google_android_client_id: str = ""
    session_max_age_seconds: int = 60 * 60 * 24 * 30
    pairing_ttl_seconds: int = 300
    pairing_max_attempts: int = 5
    log_level: str = "INFO"

    ai_enabled: bool = True
    ai_base_url: str = "https://gateway.dream-team.works/v1/chat/stream"
    ai_api_key: str = ""
    ai_model: str = "deepseek-v4.1-flash"
    ai_capability: str = "chat.standard"
    ai_max_output_tokens: int = 1024
    ai_timeout_seconds: int = 120

    voice_enabled: bool = True
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
        return Path(self.data_dir).expanduser() / "workspace"

    def prepare(self) -> None:
        Path(self.data_dir).expanduser().mkdir(parents=True, exist_ok=True)
        self.resolved_db_path.parent.mkdir(parents=True, exist_ok=True)
        self.backups_dir.mkdir(parents=True, exist_ok=True)
        self.logs_dir.mkdir(parents=True, exist_ok=True)
        self.workspace_dir.mkdir(parents=True, exist_ok=True)


@lru_cache
def get_settings() -> Settings:
    return Settings()
