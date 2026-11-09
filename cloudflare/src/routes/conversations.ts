/**
 * NEXORA Cloudflare Worker — D1 Conversations Route Handler
 * Implements:
 * - POST /api/conversations
 * - GET /api/conversations
 * - DELETE /api/conversations/:id
 */
import { WorkerApiError, jsonResponse } from '../utils/errors';
import { validateConversationCreate } from '../utils/validation';
import { CloudflareWorkerEnv } from '../services/ai-service';
import { DEFAULT_TEXT_MODEL } from '../services/model-service';

export async function handleCreateConversation(
  request: Request,
  env: CloudflareWorkerEnv,
  userId: string,
  corsHeaders: Record<string, string>
): Promise<Response> {
  const rawBody = await request.json().catch(() => ({}));
  const payload = validateConversationCreate(rawBody);
  const nowMs = Date.now();
  const convId = payload.id || `conv-${nowMs}`;
  const model = payload.model || env.DEFAULT_AI_MODEL || DEFAULT_TEXT_MODEL;

  if (env.DB) {
    await env.DB.prepare(
      `INSERT INTO conversations (id, user_id, title, language, model, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?)
       ON CONFLICT(id) DO UPDATE SET title = excluded.title, updated_at = excluded.updated_at`
    )
      .bind(convId, userId, payload.title, payload.language, model, nowMs, nowMs)
      .run();
  }

  return jsonResponse(
    {
      id: convId,
      userId,
      title: payload.title,
      language: payload.language,
      model,
      createdAt: nowMs,
      updatedAt: nowMs,
      messages: [],
    },
    201,
    corsHeaders
  );
}

export async function handleListConversations(
  env: CloudflareWorkerEnv,
  userId: string,
  corsHeaders: Record<string, string>
): Promise<Response> {
  if (!env.DB) {
    return jsonResponse(
      {
        conversations: [],
        d1Connected: false,
      },
      200,
      corsHeaders
    );
  }

  const rows = await env.DB.prepare(
    `SELECT id, user_id AS userId, title, language, model, created_at AS createdAt, updated_at AS updatedAt
     FROM conversations
     WHERE user_id = ? OR user_id = 'anonymous-nexora-user'
     ORDER BY updated_at DESC
     LIMIT 50`
  )
    .bind(userId)
    .all<{
      id: string;
      userId: string;
      title: string;
      language: string;
      model: string;
      createdAt: number;
      updatedAt: number;
    }>();

  return jsonResponse(
    {
      conversations: rows.results || [],
      d1Connected: true,
    },
    200,
    corsHeaders
  );
}

export async function handleDeleteConversation(
  conversationId: string,
  env: CloudflareWorkerEnv,
  corsHeaders: Record<string, string>
): Promise<Response> {
  const cleanId = conversationId.trim();
  if (!cleanId) {
    throw new WorkerApiError(400, 'Conversation ID is required.', 'MISSING_CONVERSATION_ID');
  }

  if (env.DB) {
    await env.DB.prepare(`DELETE FROM messages WHERE conversation_id = ?`).bind(cleanId).run();
    await env.DB.prepare(`DELETE FROM conversations WHERE id = ?`).bind(cleanId).run();
  }

  return jsonResponse(
    {
      deleted: true,
      id: cleanId,
      timestampMs: Date.now(),
    },
    200,
    corsHeaders
  );
}
