from typing import Any, List, Literal, Optional

try:
    from pydantic import BaseModel, Field
except ImportError:
    class BaseModel:  # type: ignore[no-redef]
        def __init__(self, **kwargs: Any) -> None:
            for cls_attr, cls_val in self.__class__.__dict__.items():
                if not cls_attr.startswith("_") and not callable(cls_val):
                    setattr(self, cls_attr, kwargs.get(cls_attr, cls_val))
            for k, v in kwargs.items():
                setattr(self, k, v)

    def Field(default: Any = None, **kwargs: Any) -> Any:  # type: ignore[misc]
        if "default_factory" in kwargs and default is None:
            return kwargs["default_factory"]()
        return None if default is Ellipsis else default


class ChatHistoryItem(BaseModel):
    role: Literal["user", "assistant"] = "user"
    content: str = Field(..., min_length=1, max_length=16000)


class MemoryContextItem(BaseModel):
    title: str = Field(..., min_length=1, max_length=200)
    factContent: str = Field(..., min_length=1, max_length=2000)
    category: str = Field(default="General", max_length=64)


class ChatRequest(BaseModel):
    message: str = Field(..., min_length=1, max_length=16000)
    history: List[ChatHistoryItem] = Field(default_factory=list)
    memories: List[MemoryContextItem] = Field(default_factory=list)
    language: Literal["en", "fa"] = "en"
    mode: Literal["chat", "summarize"] = "chat"
    provider: Optional[Literal["gemini", "openai", "ollama"]] = None
    model: Optional[str] = Field(default=None, max_length=120)


class ImageAnalysisRequest(BaseModel):
    prompt: str = Field(default="Analyze this image in detail.", min_length=1, max_length=4000)
    imageBase64: str = Field(..., min_length=16)
    mimeType: str = Field(default="image/jpeg", max_length=64)
    language: Literal["en", "fa"] = "en"
    provider: Optional[Literal["gemini", "openai", "ollama"]] = None
    model: Optional[str] = Field(default=None, max_length=120)


class ChatResponse(BaseModel):
    reply: str
    provider: str = "gemini"
    model: str
    latencyMs: int
    usedMemoriesCount: int = 0


class ProviderInfoResponse(BaseModel):
    activeProvider: str
    activeModel: str
    fallbackModels: List[str] = Field(default_factory=list)
    configured: bool = True
    apiKeyPresent: bool = True
    configuredProviders: List[str] = Field(default_factory=list)
    streamingSupported: bool = True
    visionSupported: bool = True
    maxImageSizeBytes: int = 4194304
    supportedImageMimeTypes: List[str] = Field(default_factory=list)
    rateLimitPerMinute: int = 60
    timeoutSeconds: float = 25.0
    configurationNotice: str = "Ready"


class HealthResponse(BaseModel):
    status: Literal["ok", "degraded"]
    service: str
    version: str
    provider: str
    activeProvider: str = "gemini"
    model: str
    activeModel: str = "gemini-3.1-flash-lite"
    fallbackModels: List[str] = Field(default_factory=list)
    configured: bool
    apiKeyPresent: bool = False
    configuredProviders: List[str] = Field(default_factory=list)
    streamingSupported: bool = True
    visionSupported: bool = True
    rateLimitPerMinute: int = 60
    timeoutSeconds: float = 25.0
    configurationNotice: str = ""
    timestampMs: int
