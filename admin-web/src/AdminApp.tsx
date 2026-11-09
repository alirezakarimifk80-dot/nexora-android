import React, { useEffect, useState } from 'react';

export interface AdminUserItem {
  id: string;
  email: string;
  fullName: string;
  role: 'user' | 'admin';
  emailVerified: boolean;
  accountStatus: 'active' | 'suspended';
  subscriptionPlan: 'free' | 'premium_monthly' | 'premium_yearly';
  aiRequestsToday: number;
  dailyAiLimit: number;
  createdAt: number;
}

export interface AdminAuditLog {
  id: string;
  level: string;
  category: string;
  message: string;
  createdAt: number;
}

export interface AdminOverviewPayload {
  metrics: {
    totalUsers: number;
    premiumSubscribers: number;
    freeUsers: number;
    activeSessions: number;
    synchronizedCloudRecords: number;
    aiRequestsToday: number;
    freeDailyAiLimit: number;
    premiumDailyAiLimit: number;
  };
  users: AdminUserItem[];
  auditLogs: AdminAuditLog[];
}

export const NexoraAdminWebApp: React.FC<{
  apiBaseUrl?: string;
  adminSessionToken: string;
}> = ({ apiBaseUrl = '', adminSessionToken }) => {
  const [overview, setOverview] = useState<AdminOverviewPayload | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const loadOverview = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetch(`${apiBaseUrl}/api/v1/admin/overview`, {
        headers: {
          Authorization: `Bearer ${adminSessionToken}`,
        },
      });
      const data = await res.json();
      if (!res.ok) {
        throw new Error(data.detail || data.error || 'Admin authorization failed');
      }
      setOverview(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load admin overview');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (adminSessionToken) {
      loadOverview();
    }
  }, [adminSessionToken]);

  const handleUpdateUser = async (
    userId: string,
    patch: { accountStatus?: string; role?: string; subscriptionPlan?: string }
  ) => {
    await fetch(`${apiBaseUrl}/api/v1/admin/users/${userId}`, {
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${adminSessionToken}`,
      },
      body: JSON.stringify(patch),
    });
    await loadOverview();
  };

  if (loading && !overview) {
    return <div className="p-6 font-mono text-xs text-cyan-400">Loading NEXORA Admin Console...</div>;
  }

  if (error) {
    return (
      <div className="p-6 rounded-xl bg-rose-500/10 border border-rose-500/30 text-xs font-mono text-rose-400">
        Admin Security Gate: {error}
      </div>
    );
  }

  if (!overview) return null;

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <div className="p-4 rounded-xl bg-[#0D111E] border border-[#1E2640]">
          <div className="text-[11px] font-mono text-slate-400">Total Registered Users</div>
          <div className="text-2xl font-bold font-mono text-white mt-1">
            {overview.metrics.totalUsers}
          </div>
        </div>
        <div className="p-4 rounded-xl bg-[#0D111E] border border-[#1E2640]">
          <div className="text-[11px] font-mono text-slate-400">Premium Subscribers</div>
          <div className="text-2xl font-bold font-mono text-cyan-400 mt-1">
            {overview.metrics.premiumSubscribers}
          </div>
        </div>
        <div className="p-4 rounded-xl bg-[#0D111E] border border-[#1E2640]">
          <div className="text-[11px] font-mono text-slate-400">Cloud Synced Entities</div>
          <div className="text-2xl font-bold font-mono text-violet-400 mt-1">
            {overview.metrics.synchronizedCloudRecords}
          </div>
        </div>
        <div className="p-4 rounded-xl bg-[#0D111E] border border-[#1E2640]">
          <div className="text-[11px] font-mono text-slate-400">Active Sessions</div>
          <div className="text-2xl font-bold font-mono text-emerald-400 mt-1">
            {overview.metrics.activeSessions}
          </div>
        </div>
      </div>

      <div className="p-5 rounded-xl bg-[#0D111E] border border-[#1E2640] space-y-3">
        <h3 className="text-sm font-bold text-white">User & Subscription Management</h3>
        <div className="divide-y divide-[#1A2138]">
          {overview.users.map((u) => (
            <div key={u.id} className="py-3 flex flex-wrap items-center justify-between gap-3">
              <div>
                <div className="text-xs font-bold text-white">
                  {u.fullName} ({u.email})
                </div>
                <div className="text-[11px] font-mono text-slate-400">
                  Role: {u.role.toUpperCase()} · Status: {u.accountStatus.toUpperCase()} · Plan:{' '}
                  {u.subscriptionPlan}
                </div>
              </div>
              <div className="flex items-center gap-2">
                <button
                  onClick={() =>
                    handleUpdateUser(u.id, {
                      accountStatus: u.accountStatus === 'active' ? 'suspended' : 'active',
                    })
                  }
                  className="px-2.5 py-1 rounded bg-slate-800 text-[11px] font-mono text-slate-200 cursor-pointer"
                >
                  {u.accountStatus === 'active' ? 'Suspend' : 'Activate'}
                </button>
                <button
                  onClick={() =>
                    handleUpdateUser(u.id, {
                      subscriptionPlan:
                        u.subscriptionPlan === 'free' ? 'premium_monthly' : 'free',
                    })
                  }
                  className="px-2.5 py-1 rounded bg-cyan-500/20 text-[11px] font-mono text-cyan-300 cursor-pointer"
                >
                  {u.subscriptionPlan === 'free' ? 'Grant Premium' : 'Set Free'}
                </button>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};
