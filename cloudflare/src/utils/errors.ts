/**
 * NEXORA Cloudflare Worker — Structured Error Handling & CORS Utilities
 */

export class WorkerApiError extends Error {
  public readonly statusCode: number;
  public readonly errorCode: string;
  public readonly details?: Record<string, unknown>;

  constructor(
    statusCode: number,
    message: string,
    errorCode = 'NEXORA_WORKER_ERROR',
    details?: Record<string, unknown>
  ) {
    super(message);
    this.name = 'WorkerApiError';
    this.statusCode = statusCode;
    this.errorCode = errorCode;
    this.details = details;
  }
}

export function buildCorsHeaders(origin?: string | null, allowedOriginsEnv?: string): Record<string, string> {
  const allowedList = (allowedOriginsEnv || '*')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);

  let allowOrigin = '*';
  if (!allowedList.includes('*') && origin) {
    allowOrigin = allowedList.includes(origin) ? origin : allowedList[0] || '*';
  }

  return {
    'Access-Control-Allow-Origin': allowOrigin,
    'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization, X-Nexora-Client-Key, X-Nexora-Device-Id',
    'Access-Control-Max-Age': '86400',
    'X-Content-Type-Options': 'nosniff',
    'X-Frame-Options': 'DENY',
    'Strict-Transport-Security': 'max-age=31536000; includeSubDomains; preload',
  };
}

export function jsonResponse(
  data: unknown,
  status = 200,
  corsHeaders: Record<string, string> = {}
): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      ...corsHeaders,
    },
  });
}

export function handleErrorResponse(
  error: unknown,
  corsHeaders: Record<string, string> = {}
): Response {
  if (error instanceof WorkerApiError) {
    return jsonResponse(
      {
        error: error.message,
        detail: error.message,
        code: error.errorCode,
        ...(error.details ? { details: error.details } : {}),
        timestampMs: Date.now(),
      },
      error.statusCode,
      corsHeaders
    );
  }

  const message =
    error instanceof Error && error.message
      ? error.message
      : 'Internal Cloudflare Worker execution error.';

  return jsonResponse(
    {
      error: message,
      detail: message,
      code: 'INTERNAL_WORKER_ERROR',
      timestampMs: Date.now(),
    },
    500,
    corsHeaders
  );
}
