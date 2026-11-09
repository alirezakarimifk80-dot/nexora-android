/**
 * NEXORA Cloudflare Worker — Native Workers AI Service & D1 Usage Telemetry
 * Executes real inference on Cloudflare Workers AI (env.AI.run) with Persian & English support.
 */
import { WorkerApiError } from '../utils/errors';
import { ValidatedChatPayload, ValidatedVisionPayload } from '../utils/validation';
import {
  resolveTextModelCandidates,
  resolveVisionModelCandidates,
} from './model-service';

export interface CloudflareWorkerEnv {
  AI: {
    run: (
      model: string,
      inputs: Record<string, unknown>,
      options?: Record<string, unknown>
    ) => Promise<unknown>;
  };
  DB?: {
    prepare: (query: string) => {
      bind: (...values: unknown[]) => {
        run: () => Promise<unknown>;
        all: <T = Record<string, unknown>>() => Promise<{ results: T[] }>;
        first: <T = Record<string, unknown>>() => Promise<T | null>;
      };
      all: <T = Record<string, unknown>>() => Promise<{ results: T[] }>;
      first: <T = Record<string, unknown>>() => Promise<T | null>;
    };
  };
  NEXORA_ASSETS?: {
    put: (
      key: string,
      value: Uint8Array | ArrayBuffer | string,
      options?: Record<string, unknown>
    ) => Promise<unknown>;
  };
  DEFAULT_AI_MODEL?: string;
  DEFAULT_VISION_MODEL?: string;
  AI_GATEWAY_ID?: string;
  ALLOWED_ORIGINS?: string;
  NEXORA_CLIENT_SECRET?: string;
  RATE_LIMIT_PER_MINUTE?: string;
}

export interface AiChatExecutionResult {
  reply: string;
  provider: 'cloudflare-workers-ai';
  model: string;
  latencyMs: number;
  usage: {
    prompt_tokens: number;
    completion_tokens: number;
    total_tokens: number;
  };
  usedMemoriesCount: number;
  conversationId: string;
  d1Persisted: boolean;
}

export interface AiVisionExecutionResult {
  reply: string;
  provider: 'cloudflare-workers-ai';
  model: string;
  latencyMs: number;
  r2ObjectKey: string | null;
  d1Persisted: boolean;
}

function buildSystemPrompt(payload: ValidatedChatPayload): string {
  const baseRole =
    payload.mode === 'summarize'
      ? 'You are NEXORA Executive Summarizer running on Cloudflare Workers AI. Provide a structured summary, key insights, and actionable items.'
      : 'You are NEXORA — Your Personal AI Command Center, powered by Cloudflare Workers AI. You are an expert software architect, systems engineer, and bilingual assistant.';

  const languageDirective =
    payload.language === 'fa'
      ? 'Respond fluently, naturally, and accurately in Persian (فارسی) using clean Markdown formatting.'
      : 'Respond clearly and accurately in English using structured Markdown formatting.';

  let memoryContext = '';
  if (payload.memories.length > 0) {
    const items = payload.memories
      .map((m) => `- [${m.category}] ${m.title}: ${m.factContent}`)
      .join('\n');
    memoryContext = `\n\nUSER-APPROVED SMART MEMORY FACTS:\n${items}`;
  }

  return `${baseRole} ${languageDirective}${memoryContext}`;
}

function buildGatewayOptions(env: CloudflareWorkerEnv): Record<string, unknown> | undefined {
  const gatewayId = (env.AI_GATEWAY_ID || '').trim();
  if (!gatewayId) return undefined;
  return {
    gateway: {
      id: gatewayId,
      skipCache: false,
    },
  };
}

function extractWorkersAiText(rawOutput: unknown): string {
  if (!rawOutput) return '';
  if (typeof rawOutput === 'string') return rawOutput.trim();
  if (typeof rawOutput === 'object') {
    const obj = rawOutput as Record<string, unknown>;
    if (typeof obj.response === 'string') return obj.response.trim();
    if (typeof obj.description === 'string') return obj.description.trim();
    if (typeof obj.result === 'object' && obj.result !== null) {
      const inner = obj.result as Record<string, unknown>;
      if (typeof inner.response === 'string') return inner.response.trim();
      if (typeof inner.description === 'string') return inner.description.trim();
    }
    if (Array.isArray(obj.choices) && obj.choices.length > 0) {
      const firstChoice = obj.choices[0] as Record<string, unknown>;
      const msg = firstChoice.message as Record<string, unknown> | undefined;
      if (msg && typeof msg.content === 'string') return msg.content.trim();
    }
  }
  return '';
}

function extractUsageMetrics(
  rawOutput: unknown,
  promptText: string,
  replyText: string
): { prompt_tokens: number; completion_tokens: number; total_tokens: number } {
  if (rawOutput && typeof rawOutput === 'object') {
    const obj = rawOutput as Record<string, unknown>;
    const usage = (obj.usage || (obj.result as Record<string, unknown> | undefined)?.usage) as
      | Record<string, unknown>
      | undefined;
    if (usage) {
      const promptTokens = Number(usage.prompt_tokens) || 0;
      const completionTokens = Number(usage.completion_tokens) || 0;
      const totalTokens = Number(usage.total_tokens) || promptTokens + completionTokens;
      if (totalTokens > 0) {
        return {
          prompt_tokens: promptTokens,
          completion_tokens: completionTokens,
          total_tokens: totalTokens,
        };
      }
    }
  }
  const estPrompt = Math.max(1, Math.ceil(promptText.length / 4));
  const estCompletion = Math.max(1, Math.ceil(replyText.length / 4));
  return {
    prompt_tokens: estPrompt,
    completion_tokens: estCompletion,
    total_tokens: estPrompt + estCompletion,
  };
}

export async function runWorkersAiChat(
  env: CloudflareWorkerEnv,
  payload: ValidatedChatPayload,
  userId: string
): Promise<AiChatExecutionResult> {
  if (!env.AI || typeof env.AI.run !== 'function') {
    throw new WorkerApiError(
      503,
      'Cloudflare Workers AI binding (env.AI) is not configured. Ensure `"ai": { "binding": "AI" }` is present in wrangler.jsonc and Workers AI is enabled in your Cloudflare account.',
      'WORKERS_AI_BINDING_MISSING'
    );
  }

  const startMs = Date.now();
  const systemPrompt = buildSystemPrompt(payload);

  // Deduplicate trailing user message if the client already included it in history
  let priorHistory = payload.history;
  if (
    priorHistory.length > 0 &&
    priorHistory[priorHistory.length - 1].role === 'user' &&
    priorHistory[priorHistory.length - 1].content.trim() === payload.message
  ) {
    priorHistory = priorHistory.slice(0, -1);
  }

  // If conversationId is provided and D1 has prior messages not passed in history, load them from D1
  if (priorHistory.length === 0 && payload.conversationId && env.DB) {
    try {
      const dbHistory = await env.DB.prepare(
        `SELECT role, content FROM messages WHERE conversation_id = ? ORDER BY created_at ASC LIMIT 14`
      )
        .bind(payload.conversationId)
        .all<{ role: string; content: string }>();
      if (dbHistory?.results?.length) {
        priorHistory = dbHistory.results
          .filter((r) => !(r.role === 'user' && r.content.trim() === payload.message))
          .map((r) => ({
            role: r.role === 'assistant' ? 'assistant' : 'user',
            content: r.content,
          }));
      }
    } catch {
      // Proceed with request history if D1 table isn't migrated yet
    }
  }

  const messages = [
    { role: 'system', content: systemPrompt },
    ...priorHistory.map((m) => ({ role: m.role, content: m.content })),
    { role: 'user', content: payload.message },
  ];

  const candidateModels = resolveTextModelCandidates(payload.model, env.DEFAULT_AI_MODEL);
  const gatewayOpts = buildGatewayOptions(env);
  let lastError: unknown = null;

  for (const modelId of candidateModels) {
    try {
      const rawOutput = await env.AI.run(
        modelId,
        {
          messages,
          temperature: payload.temperature,
          max_tokens: payload.maxTokens,
          top_p: payload.topP,
        },
        gatewayOpts
      );

      const reply = extractWorkersAiText(rawOutput);
      if (!reply) {
        throw new Error(`Model ${modelId} returned an empty response payload.`);
      }

      const latencyMs = Math.max(1, Date.now() - startMs);
      const usage = extractUsageMetrics(rawOutput, payload.message, reply);
      const conversationId = payload.conversationId || `conv-${Date.now()}`;
      let d1Persisted = false;

      if (env.DB) {
        try {
          const nowMs = Date.now();
          await env.DB.prepare(
            `INSERT INTO conversations (id, user_id, title, language, model, created_at, updated_at)
             VALUES (?, ?, ?, ?, ?, ?, ?)
             ON CONFLICT(id) DO UPDATE SET updated_at = excluded.updated_at, model = excluded.model`
          )
            .bind(
              conversationId,
              userId,
              payload.message.slice(0, 60),
              payload.language,
              modelId,
              nowMs,
              nowMs
            )
            .run();

          await env.DB.prepare(
            `INSERT INTO messages (id, conversation_id, role, content, model, latency_ms, created_at)
             VALUES (?, ?, 'user', ?, ?, 0, ?)`
          )
            .bind(`msg-u-${nowMs}`, conversationId, payload.message, modelId, nowMs)
            .run();

          await env.DB.prepare(
            `INSERT INTO messages (id, conversation_id, role, content, model, latency_ms, created_at)
             VALUES (?, ?, 'assistant', ?, ?, ?, ?)`
          )
            .bind(`msg-a-${nowMs + 1}`, conversationId, reply, modelId, latencyMs, nowMs + 1)
            .run();

          await env.DB.prepare(
            `INSERT INTO ai_usage_logs (id, user_id, endpoint, model, prompt_tokens, completion_tokens, total_tokens, latency_ms, status, created_at)
             VALUES (?, ?, '/api/ai/chat', ?, ?, ?, ?, ?, 'success', ?)`
          )
            .bind(
              `usage-${nowMs}`,
              userId,
              modelId,
              usage.prompt_tokens,
              usage.completion_tokens,
              usage.total_tokens,
              latencyMs,
              nowMs
            )
            .run();

          d1Persisted = true;
        } catch {
          d1Persisted = false;
        }
      }

      return {
        reply,
        provider: 'cloudflare-workers-ai',
        model: modelId,
        latencyMs,
        usage,
        usedMemoriesCount: payload.memories.length,
        conversationId,
        d1Persisted,
      };
    } catch (err) {
      lastError = err;
    }
  }

  const errMsg =
    lastError instanceof Error ? lastError.message : 'All Cloudflare Workers AI text models failed.';
  throw new WorkerApiError(502, `Workers AI inference error: ${errMsg}`, 'WORKERS_AI_INFERENCE_FAILED');
}

export async function runWorkersAiVision(
  env: CloudflareWorkerEnv,
  payload: ValidatedVisionPayload,
  userId: string
): Promise<AiVisionExecutionResult> {
  if (!env.AI || typeof env.AI.run !== 'function') {
    throw new WorkerApiError(
      503,
      'Cloudflare Workers AI binding (env.AI) is not configured. Ensure `"ai": { "binding": "AI" }` is present in wrangler.jsonc.',
      'WORKERS_AI_BINDING_MISSING'
    );
  }

  const startMs = Date.now();
  const candidateVisionModels = resolveVisionModelCandidates(
    payload.model,
    env.DEFAULT_VISION_MODEL
  );
  const gatewayOpts = buildGatewayOptions(env);
  const imageNumberArray = Array.from(payload.imageBytes);

  let r2ObjectKey: string | null = null;
  if (payload.storeInR2 && env.NEXORA_ASSETS) {
    try {
      const ext = payload.mimeType === 'image/png' ? 'png' : payload.mimeType === 'image/webp' ? 'webp' : 'jpg';
      const key = `vision/${userId}/${Date.now()}.${ext}`;
      await env.NEXORA_ASSETS.put(key, payload.imageBytes, {
        httpMetadata: { contentType: payload.mimeType },
      });
      r2ObjectKey = key;
    } catch {
      r2ObjectKey = null;
    }
  }

  const localizedPrompt =
    payload.language === 'fa'
      ? `${payload.prompt}\n(لطفاً تحلیل کامل و دقیق تصویر را به زبان فارسی ارائه بده.)`
      : payload.prompt;

  let lastError: unknown = null;

  for (const visionModel of candidateVisionModels) {
    try {
      const rawOutput = await env.AI.run(
        visionModel,
        {
          image: imageNumberArray,
          prompt: localizedPrompt,
          max_tokens: payload.maxTokens,
        },
        gatewayOpts
      );

      let reply = extractWorkersAiText(rawOutput);
      if (!reply) {
        throw new Error(`Vision model ${visionModel} returned an empty analysis.`);
      }

      // If the compact fallback vision model returned English while Persian was requested, translate via Workers AI text model
      if (
        payload.language === 'fa' &&
        visionModel !== '@cf/meta/llama-3.2-11b-vision-instruct' &&
        !/[\u0600-\u06FF]/.test(reply)
      ) {
        try {
          const translated = await env.AI.run(
            env.DEFAULT_AI_MODEL || '@cf/meta/llama-3.3-70b-instruct-fp8-fast',
            {
              messages: [
                {
                  role: 'system',
                  content:
                    'Translate and expand the following image analysis into fluent, natural Persian (فارسی).',
                },
                { role: 'user', content: reply },
              ],
              max_tokens: 600,
            },
            gatewayOpts
          );
          const faText = extractWorkersAiText(translated);
          if (faText) reply = faText;
        } catch {
          // Keep original vision description if translation fails
        }
      }

      const latencyMs = Math.max(1, Date.now() - startMs);
      let d1Persisted = false;

      if (env.DB) {
        try {
          const nowMs = Date.now();
          await env.DB.prepare(
            `INSERT INTO ai_usage_logs (id, user_id, endpoint, model, prompt_tokens, completion_tokens, total_tokens, latency_ms, status, created_at)
             VALUES (?, ?, '/api/ai/vision', ?, ?, ?, ?, ?, 'success', ?)`
          )
            .bind(
              `vision-${nowMs}`,
              userId,
              visionModel,
              256,
              Math.ceil(reply.length / 4),
              256 + Math.ceil(reply.length / 4),
              latencyMs,
              nowMs
            )
            .run();
          d1Persisted = true;
        } catch {
          d1Persisted = false;
        }
      }

      return {
        reply,
        provider: 'cloudflare-workers-ai',
        model: visionModel,
        latencyMs,
        r2ObjectKey,
        d1Persisted,
      };
    } catch (err) {
      lastError = err;
    }
  }

  const errMsg =
    lastError instanceof Error ? lastError.message : 'All Cloudflare Workers AI vision models failed.';
  throw new WorkerApiError(
    502,
    `Workers AI vision inference error: ${errMsg}`,
    'WORKERS_AI_VISION_FAILED'
  );
}
