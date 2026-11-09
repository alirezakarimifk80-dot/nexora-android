# NEXORA Cloudflare Serverless AI Infrastructure

Complete serverless backend for **NEXORA — Your Personal AI Command Center**, built on **Cloudflare Workers**, **Cloudflare Workers AI**, **Cloudflare D1**, **Cloudflare R2**, and **Cloudflare AI Gateway**.

No Railway, Render, VPS, or traditional Python/FastAPI server is required.

---

## 1. Architecture & Cloudflare Bindings

| Component | Binding | Purpose |
| :--- | :--- | :--- |
| **Cloudflare Workers** | `src/index.ts` | Main TypeScript REST & SSE Streaming API |
| **Cloudflare Workers AI** | `env.AI` | Native GPU inference for Persian (`fa`) & English (`en`) chat & vision |
| **Cloudflare D1** | `env.DB` | Serverless SQLite database for users, conversations, messages & token usage |
| **Cloudflare R2** | `env.NEXORA_ASSETS` | Optional object storage for vision image attachments |
| **Cloudflare AI Gateway** | `env.AI_GATEWAY_ID` | Optional request caching, analytics & rate limiting |

---

## 2. Official Workers AI Models Used

### Text Generation (`POST /api/ai/chat`)
- **Primary Model**: `@cf/meta/llama-3.3-70b-instruct-fp8-fast` — High-fluency Persian (فارسی) & English reasoning and conversation.
- **Fallbacks**:
  - `@cf/google/gemma-3-12b-it` (Multilingual 140+ languages including Persian)
  - `@cf/meta/llama-3.1-8b-instruct` (Fast low-latency conversational fallback)
  - `@cf/qwen/qwen2.5-coder-32b-instruct` (Software engineering & code generation)

### Vision Analysis (`POST /api/ai/vision`)
- **Primary Vision Model**: `@cf/meta/llama-3.2-11b-vision-instruct` — Multimodal image understanding with Persian & English explanations.
- **Fallbacks**:
  - `@cf/llava-hf/llava-1.5-7b-hf`
  - `@cf/unum/uform-gen2-qwen-500m`

---

## 3. Step-by-Step Cloudflare Account Setup

### Step 1 — Cloudflare Account & Workers AI Activation
1. Log in to the [Cloudflare Dashboard](https://dash.cloudflare.com).
2. In the left sidebar, open **AI → Workers AI** to accept the Workers AI terms and activate GPU inference on your account.
3. Copy your **Account ID** from the right sidebar of the Workers overview page.

### Step 2 — Create Cloudflare D1 Database
Inside the `cloudflare/` directory, run:
```bash
npm install
npx wrangler login
npx wrangler d1 create nexora-db
```
Copy the `database_id` printed in the terminal and replace `"00000000-0000-0000-0000-000000000000"` inside `cloudflare/wrangler.jsonc`.

Apply the D1 database migration (`migrations/0001_initial_nexora_d1.sql`):
```bash
# Local development SQLite
npm run d1:migrate:local

# Production Cloudflare D1
npm run d1:migrate:remote
```

### Step 3 — Optional Cloudflare R2 Bucket Setup
If you want persistent storage for uploaded vision images:
```bash
npx wrangler r2 bucket create nexora-assets
```
(If you do not need R2 storage, you can remove the `"r2_buckets"` block from `wrangler.jsonc`; the Worker automatically runs without R2 if the binding is absent.)

### Step 4 — Secret Configuration
Never commit secrets to GitHub. Set optional client authentication secrets using Wrangler:
```bash
npx wrangler secret put NEXORA_CLIENT_SECRET
```

### Step 5 — Deploy the Worker
```bash
npm run deploy
```
Your Worker will be published to:
`https://nexora-ai-worker.<YOUR_SUBDOMAIN>.workers.dev`

---

## 4. Automatic GitHub Actions Deployment

The workflow `.github/workflows/cloudflare-deploy.yml` automatically deploys the Cloudflare Worker whenever changes are pushed to `main`.

Configure these two **GitHub Repository Secrets** (`Settings → Secrets and variables → Actions`):
1. `CLOUDFLARE_API_TOKEN`: Create an API Token in Cloudflare Dashboard (`My Profile → API Tokens → Create Token`) using the **Edit Cloudflare Workers** template plus **Workers AI: Read/Edit** and **D1: Edit** permissions.
2. `CLOUDFLARE_ACCOUNT_ID`: Your 32-character Cloudflare Account ID.

---

## 5. REST API Endpoints & Testing (`curl`)

### 1. Health Check (`GET /api/health`)
```bash
curl -s https://nexora-ai-worker.<YOUR_SUBDOMAIN>.workers.dev/api/health | jq .
```

### 2. AI Status & Token Telemetry (`GET /api/ai/status`)
```bash
curl -s https://nexora-ai-worker.<YOUR_SUBDOMAIN>.workers.dev/api/ai/status | jq .
```

### 3. Available Models Catalog (`GET /api/models`)
```bash
curl -s https://nexora-ai-worker.<YOUR_SUBDOMAIN>.workers.dev/api/models | jq .
```

### 4. AI Chat in English & Persian (`POST /api/ai/chat`)
```bash
# Test 1: Hello NEXORA
curl -s -X POST https://nexora-ai-worker.<YOUR_SUBDOMAIN>.workers.dev/api/ai/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"Hello NEXORA.","language":"en"}' | jq .

# Test 2: Explain artificial intelligence in Persian
curl -s -X POST https://nexora-ai-worker.<YOUR_SUBDOMAIN>.workers.dev/api/ai/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"Explain artificial intelligence in Persian.","language":"fa"}' | jq .
```

### 5. Conversations CRUD (`POST / GET / DELETE /api/conversations`)
```bash
curl -s -X POST https://nexora-ai-worker.<YOUR_SUBDOMAIN>.workers.dev/api/conversations \
  -H "Content-Type: application/json" \
  -d '{"title":"Cloudflare Architecture Session","language":"en"}' | jq .

curl -s https://nexora-ai-worker.<YOUR_SUBDOMAIN>.workers.dev/api/conversations | jq .
```
