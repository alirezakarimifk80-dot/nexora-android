/**
 * NEXORA Personal AI Command Center — Cloudflare Worker Main Entrypoint
 * Serverless AI Backend powered by Cloudflare Workers, Workers AI, D1, and R2.
 */
import { verifyRequestAuth } from './middleware/auth';
import { enforceRateLimit } from './middleware/rate-limit';
import { handleChatRoute, handleChatStreamRoute } from './routes/chat';
import {
  handleCreateConversation,
  handleDeleteConversation,
  handleListConversations,
} from './routes/conversations';
import { handleAiStatus, handleHealthCheck, handleModelsCatalog } from './routes/health';
import { handleVisionRoute } from './routes/vision';
import { CloudflareWorkerEnv } from './services/ai-service';
import {
  WorkerApiError,
  buildCorsHeaders,
  handleErrorResponse,
} from './utils/errors';

export async function handleWorkerFetch(
  request: Request,
  env: CloudflareWorkerEnv
): Promise<Response> {
  const origin = request.headers.get('Origin');
  const corsHeaders = buildCorsHeaders(origin, env.ALLOWED_ORIGINS);

  if (request.method === 'OPTIONS') {
    return new Response(null, { status: 204, headers: corsHeaders });
  }

  try {
    const url = new URL(request.url);
    const pathname = url.pathname.replace(/\/+$/, '') || '/';
    const method = request.method.toUpperCase();

    // Enforce sliding-window rate limiting
    const rateLimitPerMin = Number(env.RATE_LIMIT_PER_MINUTE) || 40;
    enforceRateLimit(request, rateLimitPerMin);

    // Public Health, AI Status & Model Catalog Endpoints
    if (
      method === 'GET' &&
      (pathname === '/' || pathname === '/api/health' || pathname === '/api/v1/health' || pathname === '/health')
    ) {
      return await handleHealthCheck(env, corsHeaders);
    }

    if (
      method === 'GET' &&
      (pathname === '/api/ai/status' || pathname === '/api/v1/ai/providers' || pathname === '/api/v1/ai/health')
    ) {
      return await handleAiStatus(env, corsHeaders);
    }

    if (method === 'GET' && (pathname === '/api/models' || pathname === '/api/v1/models')) {
      return await handleModelsCatalog(env, corsHeaders);
    }

    // Authenticate client for AI inference & D1 conversation routes
    const authCtx = verifyRequestAuth(request, env);

    // AI Chat & Streaming Routes
    if (
      method === 'POST' &&
      (pathname === '/api/ai/chat' || pathname === '/api/v1/ai/chat' || pathname === '/api/v1/ai/summarize')
    ) {
      return await handleChatRoute(request, env, authCtx.userId, corsHeaders);
    }

    if (
      method === 'POST' &&
      (pathname === '/api/ai/chat/stream' || pathname === '/api/v1/ai/chat/stream')
    ) {
      return await handleChatStreamRoute(request, env, authCtx.userId, corsHeaders);
    }

    // AI Vision Route
    if (
      method === 'POST' &&
      (pathname === '/api/ai/vision' || pathname === '/api/v1/ai/vision')
    ) {
      return await handleVisionRoute(request, env, authCtx.userId, corsHeaders);
    }

    // Cloudflare D1 Conversation Routes
    if (
      method === 'POST' &&
      (pathname === '/api/conversations' || pathname === '/api/v1/conversations')
    ) {
      return await handleCreateConversation(request, env, authCtx.userId, corsHeaders);
    }

    if (
      method === 'GET' &&
      (pathname === '/api/conversations' || pathname === '/api/v1/conversations')
    ) {
      return await handleListConversations(env, authCtx.userId, corsHeaders);
    }

    if (
      method === 'DELETE' &&
      (pathname.startsWith('/api/conversations/') || pathname.startsWith('/api/v1/conversations/'))
    ) {
      const parts = pathname.split('/').filter(Boolean);
      const convId = decodeURIComponent(parts[parts.length - 1] || '');
      return await handleDeleteConversation(convId, env, corsHeaders);
    }

    throw new WorkerApiError(
      404,
      `Endpoint ${method} ${pathname} not found on NEXORA Cloudflare Worker.`,
      'ENDPOINT_NOT_FOUND'
    );
  } catch (error) {
    return handleErrorResponse(error, corsHeaders);
  }
}

export default {
  fetch: handleWorkerFetch,
};
