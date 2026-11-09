/**
 * NEXORA Cloudflare Worker — Request Validation Utilities
 * Validates chat, vision, and conversation payloads before hitting Workers AI or D1.
 */
import { WorkerApiError } from './errors';

export interface ValidatedChatMessage {
  role: 'system' | 'user' | 'assistant';
  content: string;
}

export interface ValidatedSmartMemory {
  title: string;
  factContent: string;
  category: string;
}

export interface ValidatedChatPayload {
  message: string;
  conversationId?: string;
  history: ValidatedChatMessage[];
  memories: ValidatedSmartMemory[];
  language: 'en' | 'fa';
  mode: 'chat' | 'summarize';
  model?: string;
  temperature: number;
  maxTokens: number;
  topP: number;
  stream: boolean;
}

export interface ValidatedVisionPayload {
  prompt: string;
  imageBase64: string;
  imageBytes: Uint8Array;
  mimeType: 'image/jpeg' | 'image/png' | 'image/webp';
  language: 'en' | 'fa';
  model?: string;
  maxTokens: number;
  storeInR2: boolean;
}

export interface ValidatedConversationCreatePayload {
  id?: string;
  title: string;
  language: 'en' | 'fa';
  model?: string;
}

const ALLOWED_VISION_MIME_TYPES = new Set(['image/jpeg', 'image/jpg', 'image/png', 'image/webp']);
export const MAX_VISION_IMAGE_BYTES = 4 * 1024 * 1024; // 4 MB limit

export function validateChatRequest(rawBody: unknown): ValidatedChatPayload {
  if (!rawBody || typeof rawBody !== 'object') {
    throw new WorkerApiError(400, 'Request body must be a valid JSON object.', 'INVALID_JSON');
  }

  const body = rawBody as Record<string, unknown>;
  const message = typeof body.message === 'string' ? body.message.trim() : '';
  if (!message) {
    throw new WorkerApiError(400, 'Field "message" is required and cannot be empty.', 'EMPTY_MESSAGE');
  }
  if (message.length > 16000) {
    throw new WorkerApiError(
      413,
      'Field "message" exceeds maximum length of 16,000 characters.',
      'MESSAGE_TOO_LARGE'
    );
  }

  const rawHistory = Array.isArray(body.history) ? body.history : [];
  const history: ValidatedChatMessage[] = rawHistory
    .slice(-16)
    .map((item) => {
      if (!item || typeof item !== 'object') return null;
      const obj = item as Record<string, unknown>;
      const roleRaw = typeof obj.role === 'string' ? obj.role.toLowerCase() : 'user';
      const role: 'system' | 'user' | 'assistant' =
        roleRaw === 'assistant' || roleRaw === 'system' ? roleRaw : 'user';
      const content = typeof obj.content === 'string' ? obj.content.trim() : '';
      if (!content) return null;
      return { role, content: content.slice(0, 8000) };
    })
    .filter((x): x is ValidatedChatMessage => x !== null);

  const rawMemories = Array.isArray(body.memories) ? body.memories : [];
  const memories: ValidatedSmartMemory[] = rawMemories
    .slice(0, 20)
    .map((m) => {
      if (!m || typeof m !== 'object') return null;
      const obj = m as Record<string, unknown>;
      const title = typeof obj.title === 'string' ? obj.title.trim() : '';
      const factContent = typeof obj.factContent === 'string' ? obj.factContent.trim() : '';
      const category = typeof obj.category === 'string' ? obj.category.trim() : 'General';
      if (!factContent) return null;
      return { title: title || 'Memory', factContent: factContent.slice(0, 600), category };
    })
    .filter((x): x is ValidatedSmartMemory => x !== null);

  const language: 'en' | 'fa' = body.language === 'fa' ? 'fa' : 'en';
  const mode: 'chat' | 'summarize' = body.mode === 'summarize' ? 'summarize' : 'chat';
  const model = typeof body.model === 'string' && body.model.trim() ? body.model.trim() : undefined;
  const conversationId =
    typeof body.conversationId === 'string' && body.conversationId.trim()
      ? body.conversationId.trim()
      : undefined;

  const temperature =
    typeof body.temperature === 'number'
      ? Math.max(0.0, Math.min(1.5, body.temperature))
      : 0.4;
  const maxTokens =
    typeof body.max_tokens === 'number'
      ? Math.max(64, Math.min(2048, Math.floor(body.max_tokens)))
      : typeof body.maxTokens === 'number'
      ? Math.max(64, Math.min(2048, Math.floor(body.maxTokens)))
      : 1024;
  const topP =
    typeof body.top_p === 'number' ? Math.max(0.1, Math.min(1.0, body.top_p)) : 0.9;
  const stream = Boolean(body.stream);

  return {
    message,
    conversationId,
    history,
    memories,
    language,
    mode,
    model,
    temperature,
    maxTokens,
    topP,
    stream,
  };
}

export function decodeBase64ToBytes(base64Input: string): Uint8Array {
  const cleaned = base64Input.includes(',')
    ? base64Input.slice(base64Input.indexOf(',') + 1).trim()
    : base64Input.trim();

  try {
    const binaryString = atob(cleaned);
    const bytes = new Uint8Array(binaryString.length);
    for (let i = 0; i < binaryString.length; i++) {
      bytes[i] = binaryString.charCodeAt(i);
    }
    return bytes;
  } catch {
    throw new WorkerApiError(400, 'Invalid Base64 image encoding.', 'INVALID_IMAGE_BASE64');
  }
}

export function validateVisionRequest(rawBody: unknown): ValidatedVisionPayload {
  if (!rawBody || typeof rawBody !== 'object') {
    throw new WorkerApiError(400, 'Request body must be a valid JSON object.', 'INVALID_JSON');
  }

  const body = rawBody as Record<string, unknown>;
  const rawImage = typeof body.imageBase64 === 'string' ? body.imageBase64.trim() : '';
  if (!rawImage) {
    throw new WorkerApiError(
      400,
      'Field "imageBase64" is required for Workers AI Vision analysis.',
      'MISSING_IMAGE'
    );
  }

  let rawMime = typeof body.mimeType === 'string' ? body.mimeType.trim().toLowerCase() : 'image/jpeg';
  if (rawImage.startsWith('data:image/')) {
    const semiIdx = rawImage.indexOf(';');
    if (semiIdx > 5) {
      rawMime = rawImage.slice(5, semiIdx).toLowerCase();
    }
  }

  if (!ALLOWED_VISION_MIME_TYPES.has(rawMime)) {
    throw new WorkerApiError(
      415,
      `Unsupported image MIME type '${rawMime}'. Allowed types: image/jpeg, image/png, image/webp.`,
      'UNSUPPORTED_IMAGE_TYPE'
    );
  }

  const normalizedMime: 'image/jpeg' | 'image/png' | 'image/webp' =
    rawMime === 'image/jpg' ? 'image/jpeg' : (rawMime as 'image/jpeg' | 'image/png' | 'image/webp');

  const imageBytes = decodeBase64ToBytes(rawImage);
  if (imageBytes.byteLength === 0) {
    throw new WorkerApiError(400, 'Uploaded image payload is empty (0 bytes).', 'EMPTY_IMAGE');
  }
  if (imageBytes.byteLength > MAX_VISION_IMAGE_BYTES) {
    throw new WorkerApiError(
      413,
      `Image size (${(imageBytes.byteLength / (1024 * 1024)).toFixed(2)} MB) exceeds the 4.00 MB limit.`,
      'IMAGE_TOO_LARGE'
    );
  }

  const language: 'en' | 'fa' = body.language === 'fa' ? 'fa' : 'en';
  const defaultPrompt =
    language === 'fa'
      ? 'این تصویر را با دقت تحلیل کن و تمام جزئیات مهم، متن‌ها یا اجزای فنی آن را به زبان فارسی توضیح بده.'
      : 'Analyze this image thoroughly and describe its key visual, structural, and technical details.';
  const prompt =
    typeof body.prompt === 'string' && body.prompt.trim()
      ? body.prompt.trim().slice(0, 4000)
      : defaultPrompt;

  const model = typeof body.model === 'string' && body.model.trim() ? body.model.trim() : undefined;
  const maxTokens =
    typeof body.max_tokens === 'number'
      ? Math.max(64, Math.min(1024, Math.floor(body.max_tokens)))
      : 768;
  const storeInR2 = Boolean(body.storeInR2);

  return {
    prompt,
    imageBase64: rawImage,
    imageBytes,
    mimeType: normalizedMime,
    language,
    model,
    maxTokens,
    storeInR2,
  };
}

export function validateConversationCreate(rawBody: unknown): ValidatedConversationCreatePayload {
  if (!rawBody || typeof rawBody !== 'object') {
    throw new WorkerApiError(400, 'Request body must be a valid JSON object.', 'INVALID_JSON');
  }
  const body = rawBody as Record<string, unknown>;
  const title =
    typeof body.title === 'string' && body.title.trim()
      ? body.title.trim().slice(0, 120)
      : 'NEXORA Neural Session';
  const language: 'en' | 'fa' = body.language === 'fa' ? 'fa' : 'en';
  const id =
    typeof body.id === 'string' && body.id.trim() ? body.id.trim().slice(0, 64) : undefined;
  const model =
    typeof body.model === 'string' && body.model.trim() ? body.model.trim() : undefined;

  return { id, title, language, model };
}
