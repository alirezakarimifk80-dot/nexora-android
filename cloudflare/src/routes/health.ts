/**
 * NEXORA Cloudflare Worker — Health, AI Status & Model Catalog Routes
 * Implements GET /api/health, GET /api/ai/status, and GET /api/models
 */
import { jsonResponse } from '../utils/errors';
import { MAX_VISION_IMAGE_BYTES } from '../utils/validation';
import { CloudflareWorkerEnv } from '../services/ai-service';
import {
  DEFAULT_TEXT_MODEL,
  DEFAULT_VISION_MODEL,
  FALLBACK_TEXT_MODELS,
  FALLBACK_VISION_MODELS,
  WORKERS_AI_MODEL_CATALOG,
} from '../services/model-service';

export async function handleHealthCheck(
  env: CloudflareWorkerEnv,
  corsHeaders: Record<string, string>
): Promise<Response> {
  const aiBindingReady = Boolean(env.AI && typeof env.AI.run === 'function');
  const d1BindingReady = Boolean(env.DB && typeof env.DB.prepare === 'function');
  const r2BindingReady = Boolean(env.NEXORA_ASSETS && typeof env.NEXORA_ASSETS.put === 'function');
  const activeTextModel = env.DEFAULT_AI_MODEL || DEFAULT_TEXT_MODEL;
  const activeVisionModel = env.DEFAULT_VISION_MODEL || DEFAULT_VISION_MODEL;

  return jsonResponse(
    {
      status: aiBindingReady ? 'ok' : 'degraded',
      service: 'nexora-cloudflare-worker-ai',
      version: '4.0.0',
      infrastructure: 'Cloudflare Workers + Workers AI + D1 + R2',
      configured: aiBindingReady,
      apiKeyPresent: aiBindingReady,
      provider: 'cloudflare-workers-ai',
      activeProvider: 'cloudflare-workers-ai',
      model: activeTextModel,
      activeModel: activeTextModel,
      visionModel: activeVisionModel,
      fallbackModels: FALLBACK_TEXT_MODELS,
      fallbackVisionModels: FALLBACK_VISION_MODELS,
      configuredProviders: aiBindingReady ? ['cloudflare-workers-ai'] : [],
      bindings: {
        workersAi: aiBindingReady,
        d1Database: d1BindingReady,
        r2Storage: r2BindingReady,
        aiGateway: Boolean(env.AI_GATEWAY_ID),
      },
      streamingSupported: true,
      visionSupported: true,
      maxImageSizeBytes: MAX_VISION_IMAGE_BYTES,
      supportedImageMimeTypes: ['image/jpeg', 'image/png', 'image/webp'],
      rateLimitPerMinute: Number(env.RATE_LIMIT_PER_MINUTE) || 40,
      timeoutSeconds: 30.0,
      configurationNotice: aiBindingReady
        ? 'Cloudflare Workers AI native binding (env.AI) and D1 database are active.'
        : 'Workers AI binding (env.AI) is not attached. Configure wrangler.jsonc and enable Workers AI in Cloudflare dashboard.',
      timestampMs: Date.now(),
    },
    200,
    corsHeaders
  );
}

export async function handleAiStatus(
  env: CloudflareWorkerEnv,
  corsHeaders: Record<string, string>
): Promise<Response> {
  const aiBindingReady = Boolean(env.AI && typeof env.AI.run === 'function');
  let totalRequestsLogged = 0;
  let totalTokensUsed = 0;

  if (env.DB) {
    try {
      const stats = await env.DB.prepare(
        `SELECT COUNT(*) AS count, COALESCE(SUM(total_tokens), 0) AS tokens FROM ai_usage_logs`
      ).first<{ count: number; tokens: number }>();
      if (stats) {
        totalRequestsLogged = Number(stats.count) || 0;
        totalTokensUsed = Number(stats.tokens) || 0;
      }
    } catch {
      // Ignore if migration hasn't run yet
    }
  }

  return jsonResponse(
    {
      status: aiBindingReady ? 'ok' : 'degraded',
      provider: 'cloudflare-workers-ai',
      activeProvider: 'cloudflare-workers-ai',
      activeModel: env.DEFAULT_AI_MODEL || DEFAULT_TEXT_MODEL,
      activeVisionModel: env.DEFAULT_VISION_MODEL || DEFAULT_VISION_MODEL,
      fallbackModels: FALLBACK_TEXT_MODELS,
      configured: aiBindingReady,
      apiKeyPresent: aiBindingReady,
      configuredProviders: aiBindingReady ? ['cloudflare-workers-ai'] : [],
      streamingSupported: true,
      visionSupported: true,
      maxImageSizeBytes: MAX_VISION_IMAGE_BYTES,
      supportedImageMimeTypes: ['image/jpeg', 'image/png', 'image/webp'],
      rateLimitPerMinute: Number(env.RATE_LIMIT_PER_MINUTE) || 40,
      timeoutSeconds: 30.0,
      usageTelemetry: {
        totalRequestsLogged,
        totalTokensUsed,
      },
      configurationNotice: aiBindingReady
        ? 'Cloudflare Workers AI native binding verified.'
        : 'Enable Workers AI binding in wrangler.jsonc.',
      timestampMs: Date.now(),
    },
    200,
    corsHeaders
  );
}

export async function handleModelsCatalog(
  env: CloudflareWorkerEnv,
  corsHeaders: Record<string, string>
): Promise<Response> {
  return jsonResponse(
    {
      provider: 'cloudflare-workers-ai',
      defaultTextModel: env.DEFAULT_AI_MODEL || DEFAULT_TEXT_MODEL,
      defaultVisionModel: env.DEFAULT_VISION_MODEL || DEFAULT_VISION_MODEL,
      models: WORKERS_AI_MODEL_CATALOG,
      timestampMs: Date.now(),
    },
    200,
    corsHeaders
  );
}
