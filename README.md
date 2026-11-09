# NEXORA — Personal AI Command Center (`v4.0.0` · Cloudflare Serverless Edition)

**NEXORA** is a production-grade Android & Serverless AI Command Center engineered with **Kotlin 2.0.21**, **Jetpack Compose (Material 3)**, **Hilt Dependency Injection**, **Room SQLite (v3)**, **Preferences DataStore**, **CameraX + MLKit + ZXing**, **WorkManager**, and a 100% serverless **Cloudflare Workers + Workers AI + D1 + R2** TypeScript backend (`cloudflare/`).

No Railway, Render, VPS, or traditional FastAPI server is used.

---

## 1. Architecture Overview

- **Android Client (`app/src/main/java/com/nexora/commandcenter/`)**:
  - **Custom Design System (`NexoraDesignSystem.kt`)**: Nothing OS + Linear + Raycast aesthetic with `NexoraAiOrb`, `NexoraSplineAreaChart`, `NexoraRadialGauge`, `NexoraChart`, and 19 custom Canvas vector glyphs.
  - **AI Core (`AssistantFeature.kt`, `NexoraRepositories.kt`, `NexoraNetwork.kt`)**: Connected directly to the **Cloudflare Worker REST & SSE Streaming API** (`/api/ai/chat`, `/api/ai/vision`, `/api/health`, `/api/ai/status`, `/api/models`, `/api/conversations`), with Stop Generation, Regenerate Response, Conversation Search, Vision upload with MIME/4MB validation, User-Controlled Smart Memory Vault, and Honest Offline Request Queue.
- **Cloudflare Serverless AI Backend (`cloudflare/`)**:
  - **Cloudflare Workers (`src/index.ts`)**: Main TypeScript router with CORS, sliding-window rate limiting, and authentication middleware.
  - **Cloudflare Workers AI (`src/services/ai-service.ts`, `src/services/model-service.ts`)**:
    - Text Generation (`POST /api/ai/chat`): `@cf/meta/llama-3.3-70b-instruct-fp8-fast`, `@cf/google/gemma-3-12b-it`, `@cf/meta/llama-3.1-8b-instruct`, `@cf/qwen/qwen2.5-coder-32b-instruct` with full Persian (فارسی) & English support.
    - Vision Analysis (`POST /api/ai/vision`): `@cf/meta/llama-3.2-11b-vision-instruct`, `@cf/llava-hf/llava-1.5-7b-hf`, `@cf/unum/uform-gen2-qwen-500m`.
  - **Cloudflare D1 (`migrations/0001_initial_nexora_d1.sql`)**: SQLite tables for `users`, `conversations`, `messages`, `ai_usage_logs`, and `app_config`.
  - **Cloudflare R2 (`env.NEXORA_ASSETS`)**: Optional object storage for vision image attachments.

---

## 2. Automated GitHub Actions CI/CD Workflows

1. **Android APK Build (`.github/workflows/android.yml` & `release.yml`)**:
   - Automatically runs JUnit tests and builds `app-debug.apk` on every push to `main`.
2. **Cloudflare Worker Deployment (`.github/workflows/cloudflare-deploy.yml`)**:
   - Automatically typechecks (`tsc --noEmit`) and deploys `cloudflare/` to Cloudflare Workers on push to `main` using `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID` GitHub Secrets.

See [`cloudflare/README.md`](./cloudflare/README.md) for complete setup, D1 migration, and `curl` testing instructions.
