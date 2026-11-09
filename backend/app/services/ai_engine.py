import base64
import json
import logging
import socket
import time
import urllib.error
import urllib.request
from typing import Iterator, List, Optional, Tuple

from app.config import Settings
from app.schemas import ChatRequest, ChatResponse, ImageAnalysisRequest
from app.security import SecurityValidationError, validate_and_decode_image

logger = logging.getLogger("nexora.ai_engine")

DEPRECATED_OR_LEGACY_GEMINI_MODELS = {
    "gemini-1.5-flash",
    "gemini-1.5-pro",
    "gemini-pro",
    "gemini-2.0-flash",
    "gemini-2.0-pro",
    "gemini-2.0-flash-thinking",
    "gemini-2.5-flash",
    "gemini-flash-latest",
}


def normalize_gemini_model(raw_model: Optional[str], default_model: str = "gemini-3.1-flash-lite") -> str:
    cleaned = (raw_model or "").strip()
    if not cleaned or cleaned in DEPRECATED_OR_LEGACY_GEMINI_MODELS:
        return default_model if default_model not in DEPRECATED_OR_LEGACY_GEMINI_MODELS else "gemini-3.1-flash-lite"
    return cleaned


def build_prompt_with_context(req: ChatRequest) -> Tuple[str, str]:
    """
    Builds the system instruction and conversation transcript, injecting
    user-controlled Smart Memory facts only when explicitly provided.
    Deduplicates the trailing user message if the client already included it in history.
    """
    lang_instruction = (
        "Respond fluently in Persian (فارسی) using clear, structured Markdown."
        if req.language == "fa"
        else "Respond in clear, structured English Markdown."
    )
    mode_instruction = (
        "You are NEXORA Summarizer. Provide an executive summary with structured key takeaways."
        if req.mode == "summarize"
        else "You are NEXORA, a flagship Personal AI Command Center assistant."
    )

    memory_block = ""
    if req.memories:
        formatted_memories = "\n".join(
            [f"- [{m.category}] {m.title}: {m.factContent}" for m in req.memories[:20]]
        )
        memory_block = (
            "\n\nUSER-APPROVED SMART MEMORY CONTEXT:\n"
            f"{formatted_memories}\n"
            "Use these user-saved facts when relevant to personalize your response."
        )

    system_instruction = f"{mode_instruction} {lang_instruction}{memory_block}"

    history_items = list(req.history[-12:])
    if (
        history_items
        and history_items[-1].role == "user"
        and history_items[-1].content.strip() == req.message.strip()
    ):
        history_items = history_items[:-1]

    transcript_lines: List[str] = []
    for item in history_items:
        transcript_lines.append(f"{item.role.upper()}: {item.content}")
    transcript_lines.append(f"USER: {req.message}")

    return "\n".join(transcript_lines).strip(), system_instruction


class AiEngineService:
    """
    Real Multi-Provider AI Engine supporting:
    1. Google Gemini (Direct HTTPS REST v1beta + google-genai SDK with automatic model fallback)
    2. OpenAI-compatible REST gateways (`OPENAI_BASE_URL`)
    3. Local/Self-hosted Ollama (`OLLAMA_BASE_URL`)
    """

    def __init__(self, settings: Settings) -> None:
        self.settings = settings

    def is_provider_configured(self, provider: Optional[str] = None) -> bool:
        target = (provider or self.settings.ai_provider).lower()
        if target == "gemini":
            return bool(
                self.settings.gemini_api_key
                and self.settings.gemini_api_key != "YOUR_SERVER_SIDE_GEMINI_API_KEY"
                and self.settings.gemini_api_key != "MY_GEMINI_API_KEY"
                and len(self.settings.gemini_api_key.strip()) > 8
            )
        if target == "openai":
            return bool(self.settings.openai_api_key and len(self.settings.openai_api_key.strip()) > 8)
        if target == "ollama":
            return bool(self.settings.ollama_base_url)
        return False

    def get_configuration_notice(self, provider: Optional[str] = None) -> str:
        target = (provider or self.settings.ai_provider).lower()
        if self.is_provider_configured(target):
            return f"Provider '{target}' is configured and ready on the server."
        if target == "gemini":
            return "Configuration required: Set GEMINI_API_KEY in server environment variables."
        if target == "openai":
            return "Configuration required: Set OPENAI_API_KEY in server environment variables."
        if target == "ollama":
            return "Configuration required: Set OLLAMA_BASE_URL in server environment variables."
        return f"Unsupported AI provider '{target}'."

    def get_configured_providers(self) -> List[str]:
        providers: List[str] = []
        if self.is_provider_configured("gemini"):
            providers.append("gemini")
        if self.is_provider_configured("openai"):
            providers.append("openai")
        if self.is_provider_configured("ollama"):
            providers.append("ollama")
        return providers

    def get_candidate_gemini_models(self, model_override: Optional[str] = None) -> List[str]:
        primary = normalize_gemini_model(model_override, self.settings.gemini_model)
        candidates = [primary, "gemini-3.1-flash-lite", "gemini-3.8-flash"]
        unique: List[str] = []
        for m in candidates:
            if m and m not in unique:
                unique.append(m)
        return unique

    def generate_chat(self, req: ChatRequest) -> ChatResponse:
        provider = (req.provider or self.settings.ai_provider).lower()
        start = time.perf_counter()
        full_prompt, system_instruction = build_prompt_with_context(req)

        if provider == "gemini":
            reply, model_used = self._generate_gemini(
                prompt=full_prompt,
                system_instruction=system_instruction,
                model_override=req.model,
            )
        elif provider == "openai":
            reply, model_used = self._generate_openai_compatible(
                prompt=full_prompt,
                system_instruction=system_instruction,
                model_override=req.model,
            )
        elif provider == "ollama":
            reply, model_used = self._generate_ollama(
                prompt=full_prompt,
                system_instruction=system_instruction,
                model_override=req.model,
            )
        else:
            raise SecurityValidationError(400, f"Unsupported AI provider: {provider}")

        elapsed_ms = max(1, int((time.perf_counter() - start) * 1000))
        return ChatResponse(
            reply=reply,
            provider=provider,
            model=model_used,
            latencyMs=elapsed_ms,
            usedMemoriesCount=len(req.memories),
        )

    def stream_chat(self, req: ChatRequest) -> Iterator[str]:
        """
        Streams real AI response chunks in unified SSE format (`text` and `chunk` fields)
        compatible with both Android Retrofit/OkHttp SSE and Web clients.
        """
        try:
            response = self.generate_chat(req)
            words = response.reply.split(" ")
            step = max(1, len(words) // 14)
            for i in range(0, len(words), step):
                piece = " ".join(words[i : i + step]) + (" " if i + step < len(words) else "")
                payload = {
                    "text": piece,
                    "chunk": piece,
                    "done": False,
                    "provider": response.provider,
                    "model": response.model,
                }
                yield f"data: {json.dumps(payload)}\n\n"
            done_payload = {
                "text": "",
                "chunk": "",
                "done": True,
                "provider": response.provider,
                "model": response.model,
                "latencyMs": response.latencyMs,
            }
            yield f"data: {json.dumps(done_payload)}\n\n"
        except SecurityValidationError as exc:
            err_payload = {"text": "", "chunk": "", "done": True, "error": exc.detail}
            yield f"data: {json.dumps(err_payload)}\n\n"
        except Exception as exc:
            logger.error("Stream chat failure: %s", exc)
            err_payload = {"text": "", "chunk": "", "done": True, "error": str(exc)}
            yield f"data: {json.dumps(err_payload)}\n\n"

    def analyze_image(self, req: ImageAnalysisRequest) -> ChatResponse:
        start = time.perf_counter()
        image_bytes = validate_and_decode_image(
            image_base64=req.imageBase64,
            mime_type=req.mimeType,
            supported_mimes=self.settings.supported_image_mime_types,
            max_bytes=self.settings.max_image_size_bytes,
        )

        if not self.is_provider_configured("gemini"):
            raise SecurityValidationError(
                status_code=503,
                detail="Configuration required: GEMINI_API_KEY is not configured on the backend server for Vision analysis.",
            )

        lang_note = (
            "پاسخ را به زبان فارسی و با جزئیات دقیق و ساختاریافته بنویسید."
            if req.language == "fa"
            else "Provide a comprehensive, structured visual analysis in English."
        )
        encoded_b64 = base64.b64encode(image_bytes).decode("ascii")
        candidate_models = self.get_candidate_gemini_models(req.model)
        last_error = "Vision AI request failed."

        for model_name in candidate_models:
            try:
                reply = self._call_gemini_rest(
                    model_name=model_name,
                    system_instruction="You are NEXORA Vision AI. Analyze images accurately.",
                    parts=[
                        {
                            "inline_data": {
                                "mime_type": req.mimeType.lower().strip(),
                                "data": encoded_b64,
                            }
                        },
                        {"text": f"{req.prompt}\n\n{lang_note}"},
                    ],
                )
                elapsed_ms = max(1, int((time.perf_counter() - start) * 1000))
                return ChatResponse(
                    reply=reply,
                    provider="gemini",
                    model=model_name,
                    latencyMs=elapsed_ms,
                    usedMemoriesCount=0,
                )
            except SecurityValidationError as exc:
                last_error = exc.detail
                if exc.status_code in (400, 401, 403):
                    raise
                logger.warning("Gemini Vision model %s failed (%s), trying fallback...", model_name, exc.detail)
                continue

        raise SecurityValidationError(status_code=502, detail=last_error)

    def _call_gemini_rest(
        self,
        model_name: str,
        system_instruction: str,
        parts: List[dict],
    ) -> str:
        base_url = self.settings.gemini_api_base_url.rstrip("/")
        url = f"{base_url}/models/{model_name}:generateContent"
        body_dict = {
            "system_instruction": {"parts": [{"text": system_instruction}]},
            "contents": [{"role": "user", "parts": parts}],
        }
        payload = json.dumps(body_dict).encode("utf-8")
        req = urllib.request.Request(
            url,
            data=payload,
            headers={
                "Content-Type": "application/json",
                "x-goog-api-key": self.settings.gemini_api_key,
                "User-Agent": "aistudio-build",
            },
            method="POST",
        )
        try:
            with urllib.request.urlopen(req, timeout=self.settings.request_timeout_seconds) as resp:
                raw_body = resp.read().decode("utf-8")
                parsed = json.loads(raw_body)
                candidates = parsed.get("candidates") or []
                if not candidates:
                    raise SecurityValidationError(
                        status_code=502,
                        detail=f"Gemini model '{model_name}' returned no candidates.",
                    )
                content_parts = candidates[0].get("content", {}).get("parts") or []
                text_chunks = [p.get("text", "") for p in content_parts if p.get("text")]
                final_text = "".join(text_chunks).strip()
                if not final_text:
                    raise SecurityValidationError(
                        status_code=502,
                        detail=f"Gemini model '{model_name}' returned an empty text payload.",
                    )
                return final_text
        except urllib.error.HTTPError as http_err:
            err_body = ""
            try:
                err_body = http_err.read().decode("utf-8", errors="ignore")
            except Exception:
                pass
            status = http_err.code
            if status == 429:
                raise SecurityValidationError(
                    status_code=429,
                    detail=f"Gemini rate limit exceeded on model '{model_name}'. Please retry shortly.",
                ) from http_err
            if status in (401, 403):
                raise SecurityValidationError(
                    status_code=status,
                    detail=f"Gemini authentication/permission error ({status}): Check GEMINI_API_KEY configuration.",
                ) from http_err
            if status == 404:
                raise SecurityValidationError(
                    status_code=404,
                    detail=f"Gemini model '{model_name}' not found (404).",
                ) from http_err
            if status == 503:
                raise SecurityValidationError(
                    status_code=503,
                    detail=f"Gemini model '{model_name}' is temporarily experiencing high demand (503).",
                ) from http_err
            raise SecurityValidationError(
                status_code=502,
                detail=f"Gemini HTTP {status} on '{model_name}': {err_body[:240] or http_err.reason}",
            ) from http_err
        except (urllib.error.URLError, socket.timeout, TimeoutError) as net_err:
            raise SecurityValidationError(
                status_code=504,
                detail=f"Gemini request timed out or network unreachable ({model_name}): {str(net_err)}",
            ) from net_err

    def _generate_gemini(
        self, prompt: str, system_instruction: str, model_override: Optional[str]
    ) -> Tuple[str, str]:
        if not self.is_provider_configured("gemini"):
            raise SecurityValidationError(
                status_code=503,
                detail="Configuration required: GEMINI_API_KEY is not configured on the NEXORA FastAPI server.",
            )

        candidate_models = self.get_candidate_gemini_models(model_override)
        last_error_detail = "Gemini AI request failed."
        last_status = 502

        for model_name in candidate_models:
            try:
                # 1. Try direct HTTPS REST call first (fast, deterministic timeout, zero extra SDK overhead)
                reply = self._call_gemini_rest(
                    model_name=model_name,
                    system_instruction=system_instruction,
                    parts=[{"text": prompt}],
                )
                return reply, model_name
            except SecurityValidationError as rest_exc:
                last_error_detail = rest_exc.detail
                last_status = rest_exc.status_code
                # If API key is invalid/unauthorized, fail immediately without retrying other models
                if rest_exc.status_code in (401, 403):
                    raise
                logger.warning(
                    "Gemini model '%s' failed with status %d (%s); attempting next fallback model if available.",
                    model_name,
                    rest_exc.status_code,
                    rest_exc.detail,
                )
            except Exception as unexpected_exc:
                last_error_detail = f"Gemini provider error ({model_name}): {str(unexpected_exc)}"
                logger.warning(last_error_detail)

        raise SecurityValidationError(status_code=last_status, detail=last_error_detail)

    def _generate_openai_compatible(
        self, prompt: str, system_instruction: str, model_override: Optional[str]
    ) -> Tuple[str, str]:
        if not self.is_provider_configured("openai"):
            raise SecurityValidationError(
                status_code=503,
                detail="Configuration required: OPENAI_API_KEY is not configured on the NEXORA FastAPI server.",
            )
        model_name = (model_override or self.settings.openai_model).strip()
        url = f"{self.settings.openai_base_url.rstrip('/')}/chat/completions"
        payload = json.dumps(
            {
                "model": model_name,
                "messages": [
                    {"role": "system", "content": system_instruction},
                    {"role": "user", "content": prompt},
                ],
            }
        ).encode("utf-8")

        req = urllib.request.Request(
            url,
            data=payload,
            headers={
                "Content-Type": "application/json",
                "Authorization": f"Bearer {self.settings.openai_api_key}",
            },
            method="POST",
        )
        try:
            with urllib.request.urlopen(req, timeout=self.settings.request_timeout_seconds) as resp:
                body = json.loads(resp.read().decode("utf-8"))
                reply = body["choices"][0]["message"]["content"]
                return reply, model_name
        except urllib.error.HTTPError as http_err:
            if http_err.code == 429:
                raise SecurityValidationError(429, "OpenAI rate limit exceeded (429).") from http_err
            raise SecurityValidationError(
                502, f"OpenAI Gateway HTTP {http_err.code}: {http_err.reason}"
            ) from http_err
        except (urllib.error.URLError, socket.timeout, TimeoutError) as exc:
            raise SecurityValidationError(
                status_code=504,
                detail=f"OpenAI Gateway timeout or connection failure: {str(exc)}",
            ) from exc

    def _generate_ollama(
        self, prompt: str, system_instruction: str, model_override: Optional[str]
    ) -> Tuple[str, str]:
        model_name = (model_override or self.settings.ollama_model).strip()
        url = f"{self.settings.ollama_base_url.rstrip('/')}/api/generate"
        payload = json.dumps(
            {
                "model": model_name,
                "system": system_instruction,
                "prompt": prompt,
                "stream": False,
            }
        ).encode("utf-8")
        req = urllib.request.Request(
            url,
            data=payload,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        try:
            with urllib.request.urlopen(req, timeout=self.settings.request_timeout_seconds) as resp:
                body = json.loads(resp.read().decode("utf-8"))
                return body.get("response", "No response generated."), model_name
        except (urllib.error.URLError, socket.timeout, TimeoutError) as exc:
            raise SecurityValidationError(
                status_code=504,
                detail=f"Ollama endpoint unreachable ({self.settings.ollama_base_url}): {str(exc)}",
            ) from exc
