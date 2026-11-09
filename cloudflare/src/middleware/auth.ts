/**
 * NEXORA Cloudflare Worker — Authentication-Ready & Client Verification Middleware
 * Never exposes administrative endpoints publicly and validates optional Bearer/Client tokens.
 */
import { WorkerApiError } from '../utils/errors';

export interface AuthenticatedContext {
  userId: string;
  deviceId: string;
  authenticated: boolean;
}

export function verifyRequestAuth(
  request: Request,
  env: { NEXORA_CLIENT_SECRET?: string }
): AuthenticatedContext {
  const authHeader = request.headers.get('Authorization') || '';
  const clientKey = request.headers.get('X-Nexora-Client-Key') || '';
  const deviceId = request.headers.get('X-Nexora-Device-Id') || 'nexora-android-client';

  // If NEXORA_CLIENT_SECRET is configured as a Worker Secret, enforce token check
  const requiredSecret = (env.NEXORA_CLIENT_SECRET || '').trim();
  if (requiredSecret.length > 0) {
    const bearerToken = authHeader.startsWith('Bearer ')
      ? authHeader.slice('Bearer '.length).trim()
      : '';
    const providedToken = bearerToken || clientKey.trim();

    if (!providedToken || providedToken !== requiredSecret) {
      throw new WorkerApiError(
        401,
        'Unauthorized request: Valid Authorization Bearer token or X-Nexora-Client-Key is required.',
        'UNAUTHORIZED'
      );
    }
    return {
      userId: `user-${deviceId.slice(0, 24)}`,
      deviceId,
      authenticated: true,
    };
  }

  const bearerToken = authHeader.startsWith('Bearer ')
    ? authHeader.slice('Bearer '.length).trim()
    : '';

  return {
    userId: bearerToken ? `user-${bearerToken.slice(0, 12)}` : 'anonymous-nexora-user',
    deviceId,
    authenticated: Boolean(bearerToken),
  };
}
