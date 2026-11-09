import os
from dataclasses import dataclass
from typing import List


@dataclass(frozen=True)
class Settings:
    """
    Centralized environment configuration for the NEXORA FastAPI Backend.
    All secrets are loaded strictly from server-side environment variables.
    """

    app_name: str = "NEXORA AI Command Center Backend"
    app_version: str = "3.0.0"
    environment: str = os.getenv("NEXORA_ENV", "production")
    debug: bool = os.getenv("NEXORA_DEBUG", "false").lower() == "true"

    # AI Provider & Model Configuration ("gemini", "openai", "ollama")
    ai_provider: str = os.getenv("AI_PROVIDER", "gemini").lower()
    gemini_api_key: str = os.getenv("GEMINI_API_KEY", "").strip()
    gemini_model: str = os.getenv("GEMINI_MODEL", "gemini-3.1-flash-lite").strip()
    gemini_api_base_url: str = os.getenv(
        "GEMINI_API_BASE_URL", "https://generativelanguage.googleapis.com/v1beta"
    ).strip()

    openai_api_key: str = os.getenv("OPENAI_API_KEY", "").strip()
    openai_base_url: str = os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1").strip()
    openai_model: str = os.getenv("OPENAI_MODEL", "gpt-4o-mini").strip()

    ollama_base_url: str = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434").strip()
    ollama_model: str = os.getenv("OLLAMA_MODEL", "llama3.2").strip()

    # Security, Rate Limiting & Payload Limits
    bearer_token: str = os.getenv("NEXORA_BACKEND_BEARER_TOKEN", "").strip()
    rate_limit_per_minute: int = int(os.getenv("RATE_LIMIT_PER_MINUTE", "60"))
    max_image_size_bytes: int = int(os.getenv("MAX_IMAGE_SIZE_BYTES", str(4 * 1024 * 1024)))
    request_timeout_seconds: float = float(os.getenv("REQUEST_TIMEOUT_SECONDS", "25.0"))

    @property
    def gemini_fallback_models(self) -> List[str]:
        primary = self.gemini_model or "gemini-3.1-flash-lite"
        candidates = [primary, "gemini-3.1-flash-lite", "gemini-3.8-flash"]
        unique: List[str] = []
        for m in candidates:
            if m and m not in unique:
                unique.append(m)
        return unique

    @property
    def allowed_origins(self) -> List[str]:
        raw = os.getenv("ALLOWED_ORIGINS", "*")
        return [origin.strip() for origin in raw.split(",") if origin.strip()]

    @property
    def supported_image_mime_types(self) -> List[str]:
        return ["image/jpeg", "image/jpg", "image/png", "image/webp"]


def get_settings() -> Settings:
    return Settings()
