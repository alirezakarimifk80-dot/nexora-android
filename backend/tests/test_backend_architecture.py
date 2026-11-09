import base64
import os
import sys
import unittest

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from app.config import Settings
from app.security import (
    SecurityValidationError,
    SlidingWindowRateLimiter,
    validate_and_decode_image,
    verify_bearer_token,
)


class TestNexoraBackendSecurityAndValidation(unittest.TestCase):
    def test_bearer_token_enforcement(self) -> None:
        settings_no_auth = Settings(bearer_token="")
        # Should not raise when token is not required
        verify_bearer_token(None, settings_no_auth)

        settings_with_auth = Settings(bearer_token="secret-nexora-token")
        with self.assertRaises(SecurityValidationError) as ctx_missing:
            verify_bearer_token(None, settings_with_auth)
        self.assertEqual(ctx_missing.exception.status_code, 401)

        with self.assertRaises(SecurityValidationError) as ctx_wrong:
            verify_bearer_token("Bearer wrong-token", settings_with_auth)
        self.assertEqual(ctx_wrong.exception.status_code, 403)

        # Valid token succeeds
        verify_bearer_token("Bearer secret-nexora-token", settings_with_auth)

    def test_sliding_window_rate_limiter(self) -> None:
        limiter = SlidingWindowRateLimiter(max_requests_per_minute=3)
        self.assertTrue(limiter.check_and_record("192.168.1.10")[0])
        self.assertTrue(limiter.check_and_record("192.168.1.10")[0])
        self.assertTrue(limiter.check_and_record("192.168.1.10")[0])
        # 4th request within window must be rate-limited
        allowed, retry_after = limiter.check_and_record("192.168.1.10")
        self.assertFalse(allowed)
        self.assertGreaterEqual(retry_after, 1)

    def test_image_validation_mime_and_size(self) -> None:
        supported = ["image/jpeg", "image/png", "image/webp"]
        valid_bytes = b"\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR"
        valid_b64 = base64.b64encode(valid_bytes).decode("ascii")

        decoded = validate_and_decode_image(valid_b64, "image/png", supported, 1024)
        self.assertEqual(decoded, valid_bytes)

        # Unsupported format -> 415
        with self.assertRaises(SecurityValidationError) as ctx_mime:
            validate_and_decode_image(valid_b64, "application/pdf", supported, 1024)
        self.assertEqual(ctx_mime.exception.status_code, 415)

        # Oversized payload -> 413
        with self.assertRaises(SecurityValidationError) as ctx_size:
            validate_and_decode_image(valid_b64, "image/png", supported, 4)
        self.assertEqual(ctx_size.exception.status_code, 413)

    def test_prompt_and_smart_memory_context_assembly(self) -> None:
        from app.schemas import ChatHistoryItem, ChatRequest, MemoryContextItem
        from app.services.ai_engine import build_prompt_with_context

        req = ChatRequest(
            message="How should I structure my ViewModel?",
            history=[
                ChatHistoryItem(role="user", content="We are using Kotlin 2.0.21."),
                ChatHistoryItem(role="assistant", content="Understood. Hilt + StateFlow is recommended."),
            ],
            memories=[
                MemoryContextItem(
                    title="Architecture Standard",
                    factContent="Always use Hilt @HiltViewModel with StateFlow.",
                    category="Android",
                )
            ],
            language="en",
            mode="chat",
        )
        transcript, sys_instruction = build_prompt_with_context(req)
        self.assertIn("USER: We are using Kotlin 2.0.21.", transcript)
        self.assertIn("ASSISTANT: Understood. Hilt + StateFlow is recommended.", transcript)
        self.assertIn("USER: How should I structure my ViewModel?", transcript)
        self.assertIn("USER-APPROVED SMART MEMORY CONTEXT:", sys_instruction)
        self.assertIn("Always use Hilt @HiltViewModel with StateFlow.", sys_instruction)

    def test_ai_engine_error_and_timeout_handling(self) -> None:
        from app.schemas import ChatHistoryItem, ChatRequest
        from app.services.ai_engine import AiEngineService, build_prompt_with_context, normalize_gemini_model

        # 0. Verify legacy/404 model normalization -> gemini-3.1-flash-lite
        self.assertEqual(normalize_gemini_model("gemini-2.5-flash"), "gemini-3.1-flash-lite")
        self.assertEqual(normalize_gemini_model("gemini-flash-latest"), "gemini-3.1-flash-lite")
        self.assertEqual(normalize_gemini_model("gemini-3.1-flash-lite"), "gemini-3.1-flash-lite")

        # Verify trailing duplicate user message is deduplicated in build_prompt_with_context
        dup_req = ChatRequest(
            message="Hello NEXORA. Explain what you can do in three sentences.",
            history=[
                ChatHistoryItem(
                    role="user",
                    content="Hello NEXORA. Explain what you can do in three sentences.",
                )
            ],
            memories=[],
            language="en",
            mode="chat",
        )
        dedup_transcript, _ = build_prompt_with_context(dup_req)
        self.assertEqual(
            dedup_transcript.count("Hello NEXORA. Explain what you can do in three sentences."),
            1,
        )

        # 1. Unconfigured Gemini API key -> raises 503 with clear configuration required message
        unconfigured_engine = AiEngineService(Settings(gemini_api_key="", openai_api_key=""))
        self.assertFalse(unconfigured_engine.is_provider_configured("gemini"))
        self.assertIn("Configuration required", unconfigured_engine.get_configuration_notice("gemini"))
        req_gemini = ChatRequest(
            message="Hello", history=[], memories=[], language="en", mode="chat", provider="gemini"
        )
        with self.assertRaises(SecurityValidationError) as ctx_503:
            unconfigured_engine.generate_chat(req_gemini)
        self.assertEqual(ctx_503.exception.status_code, 503)

        # 2. Unsupported provider -> raises 400
        req_bad = ChatRequest(
            message="Hello", history=[], memories=[], language="en", mode="chat", provider="unknown_llm"  # type: ignore[arg-type]
        )
        with self.assertRaises(SecurityValidationError) as ctx_400:
            unconfigured_engine.generate_chat(req_bad)
        self.assertEqual(ctx_400.exception.status_code, 400)

        # 3. Unreachable Ollama endpoint -> raises 504 Gateway Timeout
        timeout_engine = AiEngineService(
            Settings(ollama_base_url="http://127.0.0.1:59999", request_timeout_seconds=1)
        )
        req_ollama = ChatRequest(
            message="Test timeout", history=[], memories=[], language="en", mode="chat", provider="ollama"
        )
        with self.assertRaises(SecurityValidationError) as ctx_504:
            timeout_engine.generate_chat(req_ollama)
        self.assertEqual(ctx_504.exception.status_code, 504)


class TestNexoraPhase4CommercialPlatform(unittest.TestCase):
    def setUp(self) -> None:
        from app.database import CommercialDatabase
        from app.services.commercial_service import CommercialPlatformService

        self.db = CommercialDatabase(":memory:")
        self.service = CommercialPlatformService(self.db)

    def test_registration_login_logout_and_session_expiration(self) -> None:
        # 1. Register user (PBKDF2-HMAC-SHA256 password hash, never plaintext)
        reg = self.service.register_user(
            email="commander@nexora.ai",
            password="SecurePassword#2026",
            full_name="Commander Alireza",
        )
        token = reg["sessionToken"]
        user_id = reg["user"]["id"]
        self.assertTrue(token.startswith("nx_sess_"))
        self.assertEqual(reg["user"]["email"], "commander@nexora.ai")
        self.assertFalse(reg["user"]["emailVerified"])

        # Verify database does NOT store plaintext password
        row = self.db.connect().execute("SELECT password_hash, password_salt FROM users WHERE id = ?", (user_id,)).fetchone()
        self.assertNotEqual(row["password_hash"], "SecurePassword#2026")
        self.assertEqual(len(row["password_hash"]), 64)
        self.assertEqual(len(row["password_salt"]), 32)

        # 2. Authenticate active session
        profile = self.service.authenticate_session(f"Bearer {token}")
        self.assertEqual(profile["id"], user_id)

        # 3. Verify email token
        verified_profile = self.service.verify_email_token(user_id, reg["verificationTokenPreview"])
        self.assertTrue(verified_profile["emailVerified"])

        # 4. Session expiration enforcement (simulate time 8 days in future)
        future_ms = reg["expiresAt"] + 10_000
        with self.assertRaises(SecurityValidationError) as ctx_exp:
            self.service.authenticate_session(token, now_override_ms=future_ms)
        self.assertEqual(ctx_exp.exception.status_code, 401)

        # 5. Logout revokes session immediately
        self.service.logout_session(token)
        with self.assertRaises(SecurityValidationError) as ctx_revoked:
            self.service.authenticate_session(token)
        self.assertEqual(ctx_revoked.exception.status_code, 401)

        # 6. Login issues fresh valid session
        login_res = self.service.login_user("commander@nexora.ai", "SecurePassword#2026")
        self.assertTrue(login_res["sessionToken"].startswith("nx_sess_"))

        # 7. Password reset flow revokes old sessions and updates hash
        rst = self.service.request_password_reset("commander@nexora.ai")
        self.service.confirm_password_reset(
            "commander@nexora.ai", rst["resetTokenPreview"], "NewQuantumPass#99"
        )
        with self.assertRaises(SecurityValidationError):
            self.service.login_user("commander@nexora.ai", "SecurePassword#2026")
        new_login = self.service.login_user("commander@nexora.ai", "NewQuantumPass#99")
        self.assertEqual(new_login["user"]["id"], user_id)

    def test_cloud_synchronization_and_conflict_resolution(self) -> None:
        reg = self.service.register_user("sync@nexora.ai", "SyncPassword#123", "Sync Tester")
        uid = reg["user"]["id"]

        # Push initial note at t=2000
        sync1 = self.service.synchronize_delta(
            uid,
            [
                {
                    "entityType": "note",
                    "entityId": "note-alpha",
                    "payload": {"title": "Server Newer Version", "content": "v1"},
                    "updatedAt": 2000,
                }
            ],
        )
        self.assertEqual(sync1["appliedCount"], 1)
        self.assertEqual(sync1["conflictCount"], 0)

        # Push stale offline mutation at t=1500 (< 2000) -> must trigger conflict resolution
        sync2 = self.service.synchronize_delta(
            uid,
            [
                {
                    "entityType": "note",
                    "entityId": "note-alpha",
                    "payload": {"title": "Stale Client Edit", "content": "v0"},
                    "updatedAt": 1500,
                }
            ],
        )
        self.assertEqual(sync2["syncStatus"], "completed_with_conflicts")
        self.assertEqual(sync2["conflictCount"], 1)
        self.assertEqual(sync2["conflicts"][0]["resolution"], "server_newer_preserved")
        self.assertEqual(
            sync2["conflicts"][0]["winningPayload"]["title"], "Server Newer Version"
        )

        # Push newer mutation at t=3000 (> 2000) -> updates record and increments version to 2
        sync3 = self.service.synchronize_delta(
            uid,
            [
                {
                    "entityType": "note",
                    "entityId": "note-alpha",
                    "payload": {"title": "Updated Version 2", "content": "v2"},
                    "updatedAt": 3000,
                }
            ],
        )
        self.assertEqual(sync3["conflictCount"], 0)
        self.assertEqual(sync3["items"][0]["version"], 2)

    def test_subscriptions_admin_authorization_and_backup_privacy(self) -> None:
        user_reg = self.service.register_user("user@nexora.ai", "UserPass#2026", "Standard User", role="user")
        admin_reg = self.service.register_user("admin@nexora.ai", "AdminPass#2026", "Chief Admin", role="admin")
        uid = user_reg["user"]["id"]

        # 1. Subscription activation & restore (Sandbox mode clearly marked)
        sub = self.service.verify_and_activate_subscription(uid, "premium_yearly", "play_sandbox_tok_1")
        self.assertTrue(sub["verified"])
        self.assertEqual(sub["environment"], "PLAY_BILLING_TEST_SANDBOX")
        self.assertEqual(sub["user"]["subscriptionPlan"], "premium_yearly")
        self.assertEqual(sub["user"]["dailyAiLimit"], 1000)

        restored = self.service.restore_purchases(uid)
        self.assertTrue(restored["restored"])
        self.assertEqual(restored["activePlan"], "premium_yearly")

        # 2. Non-admin user MUST be denied admin access (403 Forbidden)
        with self.assertRaises(SecurityValidationError) as ctx_403:
            self.service.get_admin_dashboard_overview(user_reg["user"])
        self.assertEqual(ctx_403.exception.status_code, 403)

        # 3. Admin user can view overview and manage user status
        overview = self.service.get_admin_dashboard_overview(admin_reg["user"])
        self.assertEqual(overview["metrics"]["totalUsers"], 2)
        self.assertEqual(overview["metrics"]["premiumSubscribers"], 1)

        # 4. Cloud Backup, Restore, Personal Data Export & Account Deletion
        bkp = self.service.create_cloud_backup(uid, "Pre-Release Snapshot", {"notes": ["n1", "n2"]})
        restored_bkp = self.service.restore_cloud_backup(uid, bkp["id"])
        self.assertEqual(restored_bkp["snapshot"]["notes"], ["n1", "n2"])

        exported = self.service.export_personal_data(uid)
        self.assertEqual(exported["userProfile"]["email"], "user@nexora.ai")

        del_res = self.service.delete_account_and_data(uid)
        self.assertTrue(del_res["deleted"])
        with self.assertRaises(SecurityValidationError) as ctx_404:
            self.service.get_user_by_id(uid)
        self.assertEqual(ctx_404.exception.status_code, 404)


if __name__ == "__main__":
    unittest.main()
