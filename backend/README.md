# NEXORA AI Command Center — Python FastAPI Backend (`v3.0.0`)

A modular, authentication-ready Python **FastAPI** gateway engineered for the **NEXORA Android Application**. It keeps all AI provider API keys strictly on the server while providing real-time SSE token streaming, multimodal Vision analysis, user-controlled Smart Memory context injection, rate limiting, and multi-provider routing.

---

## 1. Architecture Overview

```text
backend/
├── main.py                          # FastAPI entry point, CORS, telemetry logging & health checks
├── requirements.txt                 # Pinned production Python dependencies
├── .env.example                     # Server-side environment variable template
├── app/
│   ├── config.py                    # Centralized Settings dataclass from env vars
│   ├── schemas.py                   # Pydantic v2 request/response validation schemas
│   ├── security.py                  # Bearer token guard, sliding-window rate limiter, MIME/size validator
│   ├── routes/
│   │   └── ai_routes.py             # Modular APIRouter (/api/v1/ai/*)
│   └── services/
│       └── ai_engine.py             # Extensible Multi-Provider AI Engine (Gemini, OpenAI, Ollama)
└── tests/
    └── test_backend_architecture.py # Automated unit & security validation test suite
```

---

## 2. API Reference

### Health & Provider Capabilities
- **`GET /health`** & **`GET /api/v1/health`**
  - Returns server status (`"ok"` or `"degraded"`), active provider, model name, and configuration readiness.
- **`GET /api/v1/ai/providers`**
  - Lists active provider, configured providers (`["gemini", "openai", "ollama"]`), streaming/vision support, `maxImageSizeBytes` (4 MB default), and `supportedImageMimeTypes` (`image/jpeg`, `image/png`, `image/webp`).

### AI Chat, Streaming & Summarization
- **`POST /api/v1/ai/chat`**
  - Synchronous AI response with conversation history (`history`) and optional user-approved `memories`.
- **`POST /api/v1/ai/chat/stream`**
  - Server-Sent Events (`text/event-stream`) streaming tokens progressively (`data: {"text": "...", "done": false}`).
- **`POST /api/v1/ai/summarize`**
  - Executive summarization endpoint producing structured bullet takeaways in English (`en`) or Persian (`fa`).

### AI Vision (Multimodal Image Analysis)
- **`POST /api/v1/ai/vision`**
  - Validates MIME type (`image/jpeg`, `image/png`, `image/webp`) and byte size (`<= 4 MB`), returning HTTP `415` on unsupported formats or `413` on oversized payloads.

---

## 3. Local Backend Setup

```bash
cd nexora-android/backend
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt

cp .env.example .env
# Edit .env and set GEMINI_API_KEY="your_real_key"

uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

Interactive OpenAPI documentation is automatically served at:
- Swagger UI: `http://localhost:8000/docs`
- ReDoc: `http://localhost:8000/redoc`

---

## 4. Running Automated Tests

```bash
cd nexora-android/backend
python3 -m unittest discover -s tests -v
```

---

## 5. Android Configuration Instructions

1. **Android Emulator (`10.0.2.2`)**:
   - By default, `app/build.gradle.kts` sets `BuildConfig.NEXORA_BACKEND_BASE_URL` to `"http://10.0.2.2:8000/"`, which routes directly from the Android Emulator to `localhost:8000` on your development machine.
2. **Runtime Override in NEXORA Settings**:
   - Open the NEXORA Android app → **Settings** → **AI Backend Service Endpoint**.
   - Enter your deployed HTTPS URL (e.g. `https://nexora-api.yourdomain.com/`) and tap **Save Backend Endpoint to DataStore**.
3. **Production Deployment (Cloud Run / Docker / Railway)**:
   - Set `GEMINI_API_KEY` and optional `NEXORA_BACKEND_BEARER_TOKEN` in your cloud provider's secret manager.
   - Run `uvicorn main:app --host 0.0.0.0 --port $PORT`.
