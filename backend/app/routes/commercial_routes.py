from typing import Any, Dict, List, Optional
from fastapi import APIRouter, Header

from app.database import CommercialDatabase
from app.services.commercial_service import CommercialPlatformService

router = APIRouter(prefix="/api/v1", tags=["Commercial Platform (Phase 4)"])

_db = CommercialDatabase()
_platform = CommercialPlatformService(_db)


@router.post("/auth/register", summary="Register a new NEXORA user account")
async def register_endpoint(payload: Dict[str, Any]) -> Dict[str, Any]:
    return _platform.register_user(
        email=str(payload.get("email", "")),
        password=str(payload.get("password", "")),
        full_name=str(payload.get("fullName", "")),
        device_info=str(payload.get("deviceInfo", "NEXORA Android")),
    )


@router.post("/auth/login", summary="Authenticate user and issue session token")
async def login_endpoint(payload: Dict[str, Any]) -> Dict[str, Any]:
    return _platform.login_user(
        email=str(payload.get("email", "")),
        password=str(payload.get("password", "")),
        device_info=str(payload.get("deviceInfo", "NEXORA Android")),
    )


@router.post("/auth/logout", summary="Revoke active session token")
async def logout_endpoint(
    authorization: Optional[str] = Header(default=None),
) -> Dict[str, Any]:
    return _platform.logout_session(authorization)


@router.get("/auth/me", summary="Get current authenticated user profile")
async def me_endpoint(
    authorization: Optional[str] = Header(default=None),
) -> Dict[str, Any]:
    return _platform.authenticate_session(authorization)


@router.put("/auth/profile", summary="Update user full name and profile avatar")
async def update_profile_endpoint(
    payload: Dict[str, Any],
    authorization: Optional[str] = Header(default=None),
) -> Dict[str, Any]:
    user = _platform.authenticate_session(authorization)
    return _platform.update_profile(
        user_id=user["id"],
        full_name=payload.get("fullName"),
        avatar_url=payload.get("avatarUrl"),
    )


@router.post("/auth/verify-email", summary="Verify account email using verification token")
async def verify_email_endpoint(
    payload: Dict[str, Any],
    authorization: Optional[str] = Header(default=None),
) -> Dict[str, Any]:
    user = _platform.authenticate_session(authorization)
    return _platform.verify_email_token(
        user_id=user["id"],
        verification_token=str(payload.get("verificationToken", "")),
    )


@router.post("/auth/password-reset/request", summary="Request password reset token")
async def password_reset_request_endpoint(payload: Dict[str, Any]) -> Dict[str, Any]:
    return _platform.request_password_reset(str(payload.get("email", "")))


@router.post("/auth/password-reset/confirm", summary="Confirm password reset with token")
async def password_reset_confirm_endpoint(payload: Dict[str, Any]) -> Dict[str, Any]:
    return _platform.confirm_password_reset(
        email=str(payload.get("email", "")),
        reset_token=str(payload.get("resetToken", "")),
        new_password=str(payload.get("newPassword", "")),
    )


@router.post("/sync/delta", summary="Bidirectional Cloud Sync with Conflict Resolution")
async def sync_delta_endpoint(
    payload: Dict[str, Any],
    authorization: Optional[str] = Header(default=None),
) -> Dict[str, Any]:
    user = _platform.authenticate_session(authorization)
    items: List[Dict[str, Any]] = payload.get("items", [])
    return _platform.synchronize_delta(user["id"], items)


@router.get("/subscriptions/plans", summary="Get Free and NEXORA Premium subscription tiers")
async def subscription_plans_endpoint() -> Dict[str, Any]:
    return _platform.get_subscription_plans()


@router.post("/subscriptions/verify", summary="Verify Google Play Billing or Sandbox purchase")
async def verify_subscription_endpoint(
    payload: Dict[str, Any],
    authorization: Optional[str] = Header(default=None),
) -> Dict[str, Any]:
    user = _platform.authenticate_session(authorization)
    return _platform.verify_and_activate_subscription(
        user_id=user["id"],
        plan_id=str(payload.get("planId", "premium_monthly")),
        purchase_token=str(payload.get("purchaseToken", "")),
    )


@router.post("/subscriptions/restore", summary="Restore previous subscription purchases")
async def restore_subscription_endpoint(
    authorization: Optional[str] = Header(default=None),
) -> Dict[str, Any]:
    user = _platform.authenticate_session(authorization)
    return _platform.restore_purchases(user["id"])


@router.post("/backup/cloud", summary="Create encrypted cloud backup snapshot")
async def create_cloud_backup_endpoint(
    payload: Dict[str, Any],
    authorization: Optional[str] = Header(default=None),
) -> Dict[str, Any]:
    user = _platform.authenticate_session(authorization)
    return _platform.create_cloud_backup(
        user_id=user["id"],
        label=str(payload.get("label", "NEXORA Cloud Snapshot")),
        snapshot=payload.get("snapshot", {}),
    )


@router.get("/backup/cloud", summary="List user cloud backup snapshots")
async def list_cloud_backups_endpoint(
    authorization: Optional[str] = Header(default=None),
) -> List[Dict[str, Any]]:
    user = _platform.authenticate_session(authorization)
    return _platform.list_cloud_backups(user["id"])


@router.post("/backup/restore/{backup_id}", summary="Restore cloud backup snapshot by ID")
async def restore_cloud_backup_endpoint(
    backup_id: str,
    authorization: Optional[str] = Header(default=None),
) -> Dict[str, Any]:
    user = _platform.authenticate_session(authorization)
    return _platform.restore_cloud_backup(user["id"], backup_id)


@router.get("/privacy/export", summary="Export all personal user data as JSON")
async def export_personal_data_endpoint(
    authorization: Optional[str] = Header(default=None),
) -> Dict[str, Any]:
    user = _platform.authenticate_session(authorization)
    return _platform.export_personal_data(user["id"])


@router.delete("/privacy/account", summary="Delete account and all associated cloud data")
async def delete_account_endpoint(
    authorization: Optional[str] = Header(default=None),
) -> Dict[str, Any]:
    user = _platform.authenticate_session(authorization)
    return _platform.delete_account_and_data(user["id"])


@router.get("/admin/overview", summary="Admin Dashboard overview, users, metrics & audit logs")
async def admin_overview_endpoint(
    authorization: Optional[str] = Header(default=None),
) -> Dict[str, Any]:
    caller = _platform.authenticate_session(authorization)
    return _platform.get_admin_dashboard_overview(caller)


@router.patch("/admin/users/{target_user_id}", summary="Admin user status, role & tier update")
async def admin_update_user_endpoint(
    target_user_id: str,
    payload: Dict[str, Any],
    authorization: Optional[str] = Header(default=None),
) -> Dict[str, Any]:
    caller = _platform.authenticate_session(authorization)
    return _platform.admin_update_user_status(
        caller_user=caller,
        target_user_id=target_user_id,
        account_status=payload.get("accountStatus"),
        role=payload.get("role"),
        subscription_plan=payload.get("subscriptionPlan"),
    )
