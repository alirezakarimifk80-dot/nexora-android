import base64
import time
from collections import defaultdict, deque
from typing import Deque, Dict, List, Optional, Tuple

from app.config import Settings


class SecurityValidationError(Exception):
    """Raised when auth, rate limit, or payload validation fails."""

    def __init__(self, status_code: int, detail: str) -> None:
        super().__init__(detail)
        self.status_code = status_code
        self.detail = detail


class SlidingWindowRateLimiter:
    """
    In-memory sliding-window rate limiter per client IP.
    Protects AI endpoints from abuse and excessive token consumption.
    """

    def __init__(self, max_requests_per_minute: int = 60) -> None:
        self.max_requests = max(1, max_requests_per_minute)
        self.window_seconds = 60.0
        self._hits: Dict[str, Deque[float]] = defaultdict(deque)

    def check_and_record(self, client_key: str) -> Tuple[bool, int]:
        now = time.monotonic()
        bucket = self._hits[client_key]
        while bucket and (now - bucket[0]) > self.window_seconds:
            bucket.popleft()

        if len(bucket) >= self.max_requests:
            retry_after = int(max(1.0, self.window_seconds - (now - bucket[0])))
            return False, retry_after

        bucket.append(now)
        remaining = max(0, self.max_requests - len(bucket))
        return True, remaining


def verify_bearer_token(authorization_header: Optional[str], settings: Settings) -> None:
    """
    Validates the Authorization: Bearer <token> header when NEXORA_BACKEND_BEARER_TOKEN
    is configured in the server environment.
    """
    expected = settings.bearer_token
    if not expected:
        return
    if not authorization_header or not authorization_header.startswith("Bearer "):
        raise SecurityValidationError(
            status_code=401,
            detail="Missing or malformed Authorization Bearer header.",
        )
    provided = authorization_header[len("Bearer ") :].strip()
    if provided != expected:
        raise SecurityValidationError(
            status_code=403,
            detail="Invalid NEXORA API client token.",
        )


def validate_and_decode_image(
    image_base64: str,
    mime_type: str,
    supported_mimes: List[str],
    max_bytes: int,
) -> bytes:
    """
    Validates image MIME type and decoded byte size before invoking vision AI.
    """
    normalized_mime = mime_type.strip().lower()
    if normalized_mime not in supported_mimes:
        raise SecurityValidationError(
            status_code=415,
            detail=(
                f"Unsupported image MIME type '{mime_type}'. "
                f"Allowed formats: {', '.join(supported_mimes)}"
            ),
        )

    clean_b64 = image_base64.split(",")[-1].strip()
    try:
        decoded = base64.b64decode(clean_b64, validate=True)
    except Exception as exc:
        raise SecurityValidationError(
            status_code=400,
            detail="Invalid base64 image encoding.",
        ) from exc

    if len(decoded) == 0:
        raise SecurityValidationError(
            status_code=400,
            detail="Uploaded image payload is empty.",
        )

    if len(decoded) > max_bytes:
        raise SecurityValidationError(
            status_code=413,
            detail=f"Image size ({len(decoded)} bytes) exceeds limit of {max_bytes} bytes.",
        )

    return decoded
