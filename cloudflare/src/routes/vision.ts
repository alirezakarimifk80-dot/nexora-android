/**
 * NEXORA Cloudflare Worker — Vision Route Handler
 * Implements POST /api/ai/vision using Workers AI multimodal vision models
 */
import { jsonResponse } from '../utils/errors';
import { validateVisionRequest } from '../utils/validation';
import { CloudflareWorkerEnv, runWorkersAiVision } from '../services/ai-service';

export async function handleVisionRoute(
  request: Request,
  env: CloudflareWorkerEnv,
  userId: string,
  corsHeaders: Record<string, string>
): Promise<Response> {
  const rawBody = await request.json().catch(() => null);
  const payload = validateVisionRequest(rawBody);

  const result = await runWorkersAiVision(env, payload, userId);

  return jsonResponse(
    {
      reply: result.reply,
      provider: result.provider,
      model: result.model,
      latencyMs: result.latencyMs,
      r2ObjectKey: result.r2ObjectKey,
      d1Persisted: result.d1Persisted,
    },
    200,
    corsHeaders
  );
}
