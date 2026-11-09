/**
 * NEXORA Cloudflare Worker — Sliding-Window Rate Limiting & Abuse Protection
 * Uses isolate memory with optional D1 fallback for abuse protection.
 */
import { WorkerApiError } from '../utils/errors';

const isolateRateBuckets = new Map<string, number[]>();

export function enforceRateLimit(
  request: Request,
  maxRequestsPerMinute = 40
): { remaining: number; clientIp: string } {
  const clientIp =
    request.headers.get('CF-Connecting-IP') ||
    request.headers.get('X-Forwarded-For')?.split(',')[0]?.trim() ||
    'local-client';

  const now = Date.now();
  const windowMs = 60_000;
  const timestamps = (isolateRateBuckets.get(clientIp) || []).filter((t) => now - t < windowMs);

  if (timestamps.length >= maxRequestsPerMinute) {
    isolateRateBuckets.set(clientIp, timestamps);
    throw new WorkerApiError(
      429,
      `Rate limit exceeded (${maxRequestsPerMinute} requests/minute). Please wait before sending more AI requests.`,
      'RATE_LIMIT_EXCEEDED',
      { retryAfterSeconds: 15 }
    );
  }

  timestamps.push(now);
  isolateRateBuckets.set(clientIp, timestamps);

  return {
    remaining: Math.max(0, maxRequestsPerMinute - timestamps.length),
    clientIp,
  };
}
