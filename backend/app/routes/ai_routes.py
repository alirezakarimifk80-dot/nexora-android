from typing import Optional
from fastapi import APIRouter, Depends, Header, HTTPException, Request
from fastapi.responses import StreamingResponse

from app.config import Settings, get_settings
from app.schemas import (
    ChatRequest,
    ChatResponse,
    ImageAnalysisRequest,
    ProviderInfoResponse,
)
from app.security import (
    SecurityValidationError,
    SlidingWindowRateLimiter,
    verify_bearer_token,
)
from app.services.ai_engine import AiEngineService

router = APIRouter(prefix="/api/v1/ai", tags=["NEXORA AI Engine"])

_rate_limiter = SlidingWindowRateLimiter(
    max_requests_per_minute=get_settings().rate_limit_per_minute
)


async def enforce_security_and_rate_limit(
    request: Request,
    authorization: Optional[str] = Header(default=None),
    settings: Settings = Depends(get_settings),
) -> Settings:
    try:
        verify_bearer_token(authorization, settings)
    except SecurityValidationError as exc:
        raise HTTPException(status_code=exc.status_code, detail=exc.detail) from exc

    client_ip = request.client.host if request.client else "127.0.0.1"
    allowed, meta = _rate_limiter.check_and_record(client_ip)
    if not allowed:
        raise HTTPException(
            status_code=429,
            detail=f"Rate limit exceeded. Retry after {meta} seconds.",
            headers={"Retry-After": str(meta)},
        )
    return settings


@router.get("/health", response_model=ProviderInfoResponse)
@router.get("/providers", response_model=ProviderInfoResponse)
async def get_providers_metadata(
    settings: Settings = Depends(enforce_security_and_rate_limit),
) -> ProviderInfoResponse:
    engine = AiEngineService(settings)
    active_model = {
        "gemini": settings.gemini_model,
        "openai": settings.openai_model,
        "ollama": settings.ollama_model,
    }.get(settings.ai_provider, settings.gemini_model)
    is_configured = engine.is_provider_configured(settings.ai_provider)

    return ProviderInfoResponse(
        activeProvider=settings.ai_provider,
        activeModel=active_model,
        fallbackModels=engine.get_candidate_gemini_models(active_model),
        configured=is_configured,
        apiKeyPresent=is_configured,
        configuredProviders=engine.get_configured_providers(),
        streamingSupported=True,
        visionSupported=True,
        maxImageSizeBytes=settings.max_image_size_bytes,
        supportedImageMimeTypes=settings.supported_image_mime_types,
        rateLimitPerMinute=settings.rate_limit_per_minute,
        timeoutSeconds=settings.request_timeout_seconds,
        configurationNotice=engine.get_configuration_notice(settings.ai_provider),
    )


@router.post("/chat", response_model=ChatResponse)
async def ai_chat_endpoint(
    payload: ChatRequest,
    settings: Settings = Depends(enforce_security_and_rate_limit),
) -> ChatResponse:
    engine = AiEngineService(settings)
    try:
        return engine.generate_chat(payload)
    except SecurityValidationError as exc:
        raise HTTPException(status_code=exc.status_code, detail=exc.detail) from exc


@router.post("/chat/stream")
async def ai_chat_stream_endpoint(
    payload: ChatRequest,
    settings: Settings = Depends(enforce_security_and_rate_limit),
) -> StreamingResponse:
    engine = AiEngineService(settings)
    return StreamingResponse(
        engine.stream_chat(payload),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


@router.post("/summarize", response_model=ChatResponse)
async def ai_summarize_endpoint(
    payload: ChatRequest,
    settings: Settings = Depends(enforce_security_and_rate_limit),
) -> ChatResponse:
    payload.mode = "summarize"
    engine = AiEngineService(settings)
    try:
        return engine.generate_chat(payload)
    except SecurityValidationError as exc:
        raise HTTPException(status_code=exc.status_code, detail=exc.detail) from exc


@router.post("/vision", response_model=ChatResponse)
async def ai_vision_endpoint(
    payload: ImageAnalysisRequest,
    settings: Settings = Depends(enforce_security_and_rate_limit),
) -> ChatResponse:
    engine = AiEngineService(settings)
    try:
        return engine.analyze_image(payload)
    except SecurityValidationError as exc:
        raise HTTPException(status_code=exc.status_code, detail=exc.detail) from exc
