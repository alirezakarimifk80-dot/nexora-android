# NEXORA — Commercial Personal AI Command Center (`v4.0.0`)

**NEXORA** is a full-stack commercial Android & Cloud AI platform engineered with **Kotlin 2.0.21**, **Jetpack Compose (Material 3)**, **Hilt Dependency Injection**, **Room SQLite (v3)**, **Preferences DataStore**, **CameraX + MLKit + ZXing**, **WorkManager**, a modular **Python FastAPI Commercial Backend (`backend/`)**, and a role-protected **React + TypeScript Admin Web Panel (`admin-web/`)**.

---

## 1. Phase 1–4 Complete Architecture

- **Android Client (`app/src/main/java/com/nexora/commandcenter/`)**:
  - **Custom Design System (`NexoraDesignSystem.kt`)**: Nothing OS + Linear + Raycast aesthetic with `NexoraAiOrb`, `NexoraSplineAreaChart`, `NexoraRadialGauge`, `NexoraChart`, and 19 custom Canvas vector glyphs.
  - **AI Core (`AssistantFeature.kt`, `NexoraRepositories.kt`)**: Real SSE token streaming, Stop Generation, Regenerate Response, Conversation Search, Vision upload with MIME/4MB validation & progress bar, User-Controlled Smart Memory Vault, and Honest Offline Request Queue.
  - **Commercial & Account Hub (`NexoraCommercialFeature.kt`)**:
    1. **Profile & Auth**: Registration, Login, Logout, Email Verification, Profile Picture & Name update.
    2. **Account Security**: PBKDF2-HMAC-SHA256 backend password hashing (200,000 iterations) & SHA-256 session digest management.
    3. **Subscription Plans**: Free vs. NEXORA Quantum Pro Monthly ($9.99) & Yearly ($79.99) comparison, Google Play Billing verification (`PLAY_BILLING_TEST_SANDBOX` when merchant credentials are unconfigured), and Restore Purchases.
    4. **Premium Features**: Higher AI usage limits (1,000/day vs 25/day), Multi-Device Cloud Sync, Encrypted Cloud Backups, and Advanced Telemetry.
    5. **Cloud Synchronization**: Offline-first delta sync for Notes, Tasks, Preferences, Selected AI Conversations, and Personal Settings with deterministic Server-Authoritative Conflict Resolution.
    6. **Notification Settings**: Configurable channels for Task Reminders, Account Alerts, Cloud Sync, and Subscriptions.
    7. **Privacy Center & Cloud Backup**: Encrypted Cloud Backup & Restore, GDPR Personal Data JSON Export, and Cascading Account Deletion.
- **Python FastAPI Backend (`backend/`)**:
  - SQL Migrations (`migrations/001_initial_commercial_schema.sql`) automatically applied by `app/database.py`.
  - Multi-provider AI Engine (`app/services/ai_engine.py`) + Commercial Platform Service (`app/services/commercial_service.py`).
- **Admin Web Panel (`admin-web/src/AdminApp.tsx`)**:
  - Role-protected administration dashboard (`role === "admin"` required) with user management, account suspension/activation, subscription tier overrides, and audit logs.

---

## 2. Running Automated Tests

```bash
cd nexora-android/backend
python3 -m unittest discover -s tests -v
```

---

## 3. External Configuration Checklist for Production Deployment

1. **AI Provider Keys (`backend/.env`)**:
   - Set `GEMINI_API_KEY` (or `OPENAI_API_KEY` / `OLLAMA_BASE_URL`).
2. **Google Play Billing Live Verification (Optional for Live Merchant Charging)**:
   - Set `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` on the FastAPI backend to switch `/api/v1/subscriptions/verify` from `PLAY_BILLING_TEST_SANDBOX` to `GOOGLE_PLAY_VERIFIED`.
3. **Firebase Cloud Messaging Push Delivery (Optional)**:
   - Place `google-services.json` in `nexora-android/app/` if remote FCM push delivery is desired alongside the built-in local `WorkManager` notifications.
