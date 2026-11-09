/**
 * NEXORA Cloudflare Worker — Chat Route Handler
 * Implements POST /api/ai/chat and POST /api/ai/chat/stream
 */
import { jsonResponse } from '../utils/errors';
import { validateChatRequest } from '../utils/validation';
import { CloudflareWorkerEnv, runWorkersAiChat } from '../services/ai-service';

export async function handleChatRoute(
  request: Request,
  env: CloudflareWorkerEnv,
  userId: string,
  corsHeaders: Record<string, string>
): Promise<Response> {
  const rawBody = await request.json().catch(() => null);
  const payload = validateChatRequest(rawBody);

  const result = await runWorkersAiChat(env, payload, userId);

  return jsonResponse(
    {
      reply: result.reply,
      provider: result.provider,
      model: result.model,
      latencyMs: result.latencyMs,
      usage: result.usage,
      usedMemoriesCount: result.usedMemoriesCount,
      conversationId: result.conversationId,
      d1Persisted: result.d1Persisted,
    },
    200,
    corsHeaders
  );
}

export async function handleChatStreamRoute(
  request: Request,
  env: CloudflareWorkerEnv,
  userId: string,
  corsHeaders: Record<string, string>
): Promise<Response> {
  const rawBody = await request.json().catch(() => null);
  const payload = validateChatRequest(rawBody);

  const encoder = new TextEncoder();
  const stream = new ReadableStream({
    async start(controller) {
      try {
        const result = await runWorkersAiChat(env, payload, userId);
        const tokens = result.reply.match(/.{1,28}(\s|$)|\S+/g) || [result.reply];
        for (const token of tokens) {
          const chunk = JSON.stringify({
            text: token,
            chunk: token,
            done: false,
            provider: result.provider,
            model: result.model,
          });
          controller.enqueue(encoder.encode(`data: ${chunk}\n\n`));
        }
        const finalChunk = JSON.stringify({
          text: '',
          chunk: '',
          done: true,
          provider: result.provider,
          model: result.model,
          usage: result.usage,
        });
        controller.enqueue(encoder.encode(`data: ${finalChunk}\n\n`));
        controller.enqueue(encoder.encode('data: [DONE]\n\n'));
      } catch (err) {
        const errMsg = err instanceof Error ? err.message : 'Workers AI stream failed';
        controller.enqueue(
          encoder.encode(
            `data: ${JSON.stringify({ text: '', chunk: '', done: true, error: errMsg })}\n\n`
          )
        );
      } finally {
        controller.close();
      }
    },
  });

  return new Response(stream, {
    status: 200,
    headers: {
      'Content-Type': 'text/event-stream; charset=utf-8',
      'Cache-Control': 'no-cache, no-transform',
      Connection: 'keep-alive',
      ...corsHeaders,
    },
  });
}
