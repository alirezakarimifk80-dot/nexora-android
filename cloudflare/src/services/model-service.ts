/**
 * NEXORA Cloudflare Worker — Official Cloudflare Workers AI Model Catalog Service
 * Uses only real, production Cloudflare Workers AI model identifiers suitable for Persian (فارسی) & English.
 */

export interface WorkersAiModelDescriptor {
  id: string;
  name: string;
  task: 'text-generation' | 'image-to-text';
  provider: string;
  persianSupport: 'excellent' | 'good';
  englishSupport: 'excellent';
  streamingSupported: boolean;
  visionSupported: boolean;
  contextWindow: number;
  recommended: boolean;
  description: string;
}

export const DEFAULT_TEXT_MODEL = '@cf/meta/llama-3.3-70b-instruct-fp8-fast';
export const FALLBACK_TEXT_MODELS: string[] = [
  '@cf/meta/llama-3.3-70b-instruct-fp8-fast',
  '@cf/google/gemma-3-12b-it',
  '@cf/meta/llama-3.1-8b-instruct',
  '@cf/qwen/qwen2.5-coder-32b-instruct',
];

export const DEFAULT_VISION_MODEL = '@cf/meta/llama-3.2-11b-vision-instruct';
export const FALLBACK_VISION_MODELS: string[] = [
  '@cf/meta/llama-3.2-11b-vision-instruct',
  '@cf/llava-hf/llava-1.5-7b-hf',
  '@cf/unum/uform-gen2-qwen-500m',
];

export const WORKERS_AI_MODEL_CATALOG: WorkersAiModelDescriptor[] = [
  {
    id: '@cf/meta/llama-3.3-70b-instruct-fp8-fast',
    name: 'Llama 3.3 70B Instruct FP8 Fast (Workers AI)',
    task: 'text-generation',
    provider: 'Meta / Cloudflare Workers AI',
    persianSupport: 'excellent',
    englishSupport: 'excellent',
    streamingSupported: true,
    visionSupported: false,
    contextWindow: 128000,
    recommended: true,
    description:
      'Flagship multilingual 70B instruction model on Cloudflare Workers AI with strong Persian (فارسی) & English fluency.',
  },
  {
    id: '@cf/google/gemma-3-12b-it',
    name: 'Gemma 3 12B Instruct (Workers AI)',
    task: 'text-generation',
    provider: 'Google / Cloudflare Workers AI',
    persianSupport: 'excellent',
    englishSupport: 'excellent',
    streamingSupported: true,
    visionSupported: false,
    contextWindow: 128000,
    recommended: true,
    description:
      'High-speed multilingual instruction model supporting over 140 languages including Persian and English.',
  },
  {
    id: '@cf/meta/llama-3.1-8b-instruct',
    name: 'Llama 3.1 8B Instruct (Workers AI)',
    task: 'text-generation',
    provider: 'Meta / Cloudflare Workers AI',
    persianSupport: 'good',
    englishSupport: 'excellent',
    streamingSupported: true,
    visionSupported: false,
    contextWindow: 128000,
    recommended: false,
    description: 'Low-latency 8B instruction model for fast conversational responses.',
  },
  {
    id: '@cf/qwen/qwen2.5-coder-32b-instruct',
    name: 'Qwen 2.5 Coder 32B Instruct (Workers AI)',
    task: 'text-generation',
    provider: 'Qwen / Cloudflare Workers AI',
    persianSupport: 'good',
    englishSupport: 'excellent',
    streamingSupported: true,
    visionSupported: false,
    contextWindow: 32768,
    recommended: false,
    description: 'Specialized software engineering, Kotlin/TypeScript code generation, and reasoning model.',
  },
  {
    id: '@cf/meta/llama-3.2-11b-vision-instruct',
    name: 'Llama 3.2 11B Vision Instruct (Workers AI)',
    task: 'image-to-text',
    provider: 'Meta / Cloudflare Workers AI',
    persianSupport: 'excellent',
    englishSupport: 'excellent',
    streamingSupported: false,
    visionSupported: true,
    contextWindow: 128000,
    recommended: true,
    description: 'Multimodal vision-language model on Workers AI for image understanding and Persian/English explanations.',
  },
  {
    id: '@cf/llava-hf/llava-1.5-7b-hf',
    name: 'LLaVA 1.5 7B HF (Workers AI Vision)',
    task: 'image-to-text',
    provider: 'LLaVA / Cloudflare Workers AI',
    persianSupport: 'good',
    englishSupport: 'excellent',
    streamingSupported: false,
    visionSupported: true,
    contextWindow: 4096,
    recommended: false,
    description: 'Reliable Workers AI vision model for image analysis and visual Q&A.',
  },
  {
    id: '@cf/unum/uform-gen2-qwen-500m',
    name: 'UForm Gen2 Qwen 500M (Workers AI Vision)',
    task: 'image-to-text',
    provider: 'Unum / Cloudflare Workers AI',
    persianSupport: 'good',
    englishSupport: 'excellent',
    streamingSupported: false,
    visionSupported: true,
    contextWindow: 4096,
    recommended: false,
    description: 'Ultra-fast compact multimodal vision model available across all Workers AI tiers.',
  },
];

export function resolveTextModelCandidates(requestedModel?: string, envDefaultModel?: string): string[] {
  const catalogTextIds = new Set(
    WORKERS_AI_MODEL_CATALOG.filter((m) => m.task === 'text-generation').map((m) => m.id)
  );
  const candidates: string[] = [];

  if (requestedModel && catalogTextIds.has(requestedModel.trim())) {
    candidates.push(requestedModel.trim());
  }
  if (envDefaultModel && catalogTextIds.has(envDefaultModel.trim())) {
    candidates.push(envDefaultModel.trim());
  }
  for (const fallback of FALLBACK_TEXT_MODELS) {
    if (!candidates.includes(fallback)) {
      candidates.push(fallback);
    }
  }
  return candidates;
}

export function resolveVisionModelCandidates(requestedModel?: string, envVisionModel?: string): string[] {
  const catalogVisionIds = new Set(
    WORKERS_AI_MODEL_CATALOG.filter((m) => m.task === 'image-to-text').map((m) => m.id)
  );
  const candidates: string[] = [];

  if (requestedModel && catalogVisionIds.has(requestedModel.trim())) {
    candidates.push(requestedModel.trim());
  }
  if (envVisionModel && catalogVisionIds.has(envVisionModel.trim())) {
    candidates.push(envVisionModel.trim());
  }
  for (const fallback of FALLBACK_VISION_MODELS) {
    if (!candidates.includes(fallback)) {
      candidates.push(fallback);
    }
  }
  return candidates;
}
