import logging
import time
from dotenv import load_dotenv
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.config import get_settings
from app.routes.ai_routes import router as ai_router
from app.routes.commercial_routes import router as commercial_router
from app.schemas import HealthResponse
from app.security import SecurityValidationError

load_dotenv()

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)s | %(name)s | %(message)s",
)
logger = logging.getLogger("nexora.backend")

settings = get_settings()

app = FastAPI(
    title=settings.app_name,
    description=(
        "Commercial FastAPI AI & Cloud Platform Gateway for the NEXORA Android "
        "Personal AI Command Center. Supports Multi-Provider AI, SSE Streaming, "
        "Vision Analysis, Smart Memory, PBKDF2 User Authentication, Cloud Sync with "
        "Conflict Resolution, Play Billing Subscriptions, and Role-Protected Admin Console."
    ),
    version=settings.app_version,
    docs_url="/docs",
    redoc_url="/redoc",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.allowed_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.middleware("http")
async def request_telemetry_middleware(request: Request, call_next):
    start = time.perf_counter()
    response = await call_next(request)
    elapsed_ms = int((time.perf_counter() - start) * 1000)
    response.headers["X-Process-Time-Ms"] = str(elapsed_ms)
    logger.info(
        "%s %s -> %d (%d ms)",
        request.method,
        request.url.path,
        response.status_code,
        elapsed_ms,
    )
    return response


@app.exception_handler(SecurityValidationError)
async def security_exception_handler(_request: Request, exc: SecurityValidationError):
    return JSONResponse(
        status_code=exc.status_code,
        content={"detail": exc.detail},
    )


def _build_health_response() -> HealthResponse:
    cfg = get_settings()
    from app.services.ai_engine import AiEngineService

    engine = AiEngineService(cfg)
    active_model = {
        "gemini": cfg.gemini_model,
        "openai": cfg.openai_model,
        "ollama": cfg.ollama_model,
    }.get(cfg.ai_provider, cfg.gemini_model)

    is_configured = engine.is_provider_configured(cfg.ai_provider)
    return HealthResponse(
        status="ok" if is_configured else "degraded",
        service="nexora-commercial-fastapi-backend",
        version=cfg.app_version,
        provider=cfg.ai_provider,
        activeProvider=cfg.ai_provider,
        model=active_model,
        activeModel=active_model,
        fallbackModels=engine.get_candidate_gemini_models(active_model),
        configured=is_configured,
        apiKeyPresent=is_configured,
        configuredProviders=engine.get_configured_providers(),
        streamingSupported=True,
        visionSupported=True,
        rateLimitPerMinute=cfg.rate_limit_per_minute,
        timeoutSeconds=cfg.request_timeout_seconds,
        configurationNotice=engine.get_configuration_notice(cfg.ai_provider),
        timestampMs=int(time.time() * 1000),
    )


@app.get("/health", response_model=HealthResponse, tags=["Health"])
async def root_health_check() -> HealthResponse:
    return _build_health_response()


@app.get("/api/v1/health", response_model=HealthResponse, tags=["Health"])
async def api_v1_health_check() -> HealthResponse:
    return _build_health_response()


app.include_router(ai_router)
app.include_router(commercial_router)
