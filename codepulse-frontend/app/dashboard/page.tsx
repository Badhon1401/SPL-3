"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { useEffect } from "react";
import {
  CheckCircle, Percent, RefreshCw, Zap, ExternalLink,
  Trophy, Flame, BrainCircuit, Clock, Link2,
} from "lucide-react";
import { analyticsApi, submissionsApi, userApi } from "@/lib/api";
import { useAuthStore } from "@/lib/store";
import { StatCard } from "@/components/dashboard/StatCard";
import { StreakWidget } from "@/components/dashboard/StreakWidget";
import toast from "react-hot-toast";
import Link from "next/link";

const PLATFORM_META = {
  CODEFORCES: { color: "#ff6b35", label: "Codeforces", short: "CF", profileUrl: (h: string) => `https://codeforces.com/profile/${h}` },
  LEETCODE:   { color: "#ffd700", label: "LeetCode",   short: "LC", profileUrl: (h: string) => `https://leetcode.com/${h}` },
  ATCODER:    { color: "#00f5ff", label: "AtCoder",    short: "AC", profileUrl: (h: string) => `https://atcoder.jp/users/${h}` },
  CODECHEF:   { color: "#39ff14", label: "CodeChef",   short: "CC", profileUrl: (h: string) => `https://www.codechef.com/users/${h}` },
} as const;

const VERDICT_COLOR: Record<string, string> = {
  ACCEPTED: "#39ff14", WRONG_ANSWER: "#ff2d78", TIME_LIMIT_EXCEEDED: "#ff6b35",
  MEMORY_LIMIT_EXCEEDED: "#bf5fff", RUNTIME_ERROR: "#ffd700",
  COMPILATION_ERROR: "#888", PARTIAL: "#aaa", SKIPPED: "#555",
};

export default function DashboardPage() {
  const { user, updateUser } = useAuthStore();
  const qc = useQueryClient();

  // ── Fetch fresh profile so handles are ALWAYS up-to-date ─────────────────
  const { data: freshProfile } = useQuery({
    queryKey: ["my-profile"],
    queryFn: () => userApi.getMe().then((r) => r.data.data),
    staleTime: 1000 * 60 * 3,
  });

  useEffect(() => {
    if (freshProfile) updateUser(freshProfile);
  }, [freshProfile]);

  // Use freshProfile first (server), then store (localStorage cache)
  const handles = {
    CODEFORCES: freshProfile?.codeforcesHandle ?? user?.codeforcesHandle,
    LEETCODE:   freshProfile?.leetcodeHandle   ?? user?.leetcodeHandle,
    ATCODER:    freshProfile?.atcoderHandle    ?? user?.atcoderHandle,
    CODECHEF:   freshProfile?.codechefHandle   ?? user?.codechefHandle,
  };

  const { data: analytics } = useQuery({
    queryKey: ["analytics"],
    queryFn: () => analyticsApi.getMyAnalytics().then((r) => r.data.data),
  });

  const { data: recentSubs, isLoading: loadingSubs } = useQuery({
    queryKey: ["recent-subs"],
    queryFn: () => submissionsApi.getRecent(12).then((r) => r.data.data),
  });

  const syncMutation = useMutation({
    mutationFn: () => analyticsApi.syncData(),
    onSuccess: () => {
      toast.success("Sync started — results update in a moment");
      setTimeout(() => {
        qc.invalidateQueries({ queryKey: ["analytics"] });
        qc.invalidateQueries({ queryKey: ["recent-subs"] });
        qc.invalidateQueries({ queryKey: ["my-profile"] });
      }, 4000);
    },
    onError: () => toast.error("Sync failed — add your handles in Profile first"),
  });

  const displayName = freshProfile?.fullName || freshProfile?.username || user?.username;

  return (
    <>
      {/* Header */}
      <motion.div initial={{ opacity: 0, y: -14 }} animate={{ opacity: 1, y: 0 }}
        className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3 mb-7">
        <div>
          <h1 className="text-2xl lg:text-3xl font-display font-bold text-white">
            Hey, <span className="gradient-text">{displayName}</span> 👋
          </h1>
          <p className="text-slate-400 text-sm mt-1">
            Combined overview across all connected platforms.
            {(freshProfile?.lastSyncedAt || user?.lastSyncedAt) && (
              <span className="ml-2 text-slate-600 font-mono text-xs">
                Last sync: {new Date(freshProfile?.lastSyncedAt || user?.lastSyncedAt || "").toLocaleString()}
              </span>
            )}
          </p>
        </div>
        <motion.button whileHover={{ scale: 1.03 }} whileTap={{ scale: 0.97 }}
          onClick={() => syncMutation.mutate()} disabled={syncMutation.isPending}
          className="glow-btn flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-semibold self-start"
          style={{ background: "rgba(0,245,255,0.1)", border: "1px solid rgba(0,245,255,0.3)", color: "#00f5ff" }}>
          <RefreshCw className={`w-4 h-4 ${syncMutation.isPending ? "animate-spin" : ""}`} />
          {syncMutation.isPending ? "Syncing…" : "Sync All Platforms"}
        </motion.button>
      </motion.div>

      {/* Stat Cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <StatCard label="CodePulse Rating" value={analytics?.combinedRating ?? 0}
          icon={Zap} color="#00f5ff" delay={0} subtext={analytics?.ratingTier} />
        <StatCard label="Problems Solved" value={analytics?.uniqueProblemsSolved ?? 0}
          icon={CheckCircle} color="#39ff14" delay={0.08} />
        <StatCard label="Current Streak" value={analytics?.currentStreak ?? 0}
          suffix=" days" icon={Flame} color="#ff6b35" delay={0.16} />
        <StatCard label="Acceptance Rate" value={analytics?.acceptanceRate ?? 0}
          suffix="%" icon={Percent} color="#bf5fff" delay={0.24} isPercent />
      </div>

      {/* Streak widget + Platform connections */}
      <div className="grid lg:grid-cols-3 gap-5 mb-6">
        <StreakWidget
          currentStreak={analytics?.currentStreak ?? 0}
          longestStreak={analytics?.longestStreak ?? 0}
          totalActiveDays={analytics?.totalActiveDays ?? 0}
          ratingTier={analytics?.ratingTier}
          combinedRating={analytics?.combinedRating ?? 0}
        />

        {/* Platform connection grid */}
        <div className="lg:col-span-2 glass-card rounded-2xl p-5">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-sm font-display font-bold text-white flex items-center gap-2">
              <Trophy className="w-4 h-4 text-yellow-400" />
              Platform Connections
            </h3>
            <Link href="/profile" className="text-xs text-slate-500 hover:text-cyan-400 transition-colors">
              Manage handles →
            </Link>
          </div>
          <div className="grid grid-cols-2 gap-3">
            {(Object.keys(PLATFORM_META) as Array<keyof typeof PLATFORM_META>).map((plat) => {
              const meta = PLATFORM_META[plat];
              const handle = handles[plat];
              const connected = !!handle;
              const stats = analytics?.platformBreakdown?.[plat];
              return (
                <motion.div key={plat} whileHover={{ scale: 1.02 }}
                  className="rounded-xl p-3.5 border transition-all"
                  style={{
                    background: connected ? `${meta.color}0d` : "rgba(255,255,255,0.02)",
                    borderColor: connected ? `${meta.color}35` : "rgba(255,255,255,0.06)",
                  }}>
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-xs font-display font-bold"
                      style={{ color: connected ? meta.color : "#4a5568" }}>
                      {meta.label}
                    </span>
                    <div className={`w-2 h-2 rounded-full flex-shrink-0 ${connected ? "animate-pulse" : ""}`}
                      style={{ background: connected ? meta.color : "#2d3748" }} />
                  </div>

                  {connected ? (
                    <>
                      {/* Handle displayed prominently with external link */}
                      <a href={meta.profileUrl(handle!)} target="_blank" rel="noopener noreferrer"
                        className="flex items-center gap-1.5 group mb-1">
                        <span className="font-mono text-sm font-bold truncate"
                          style={{ color: meta.color }}>{handle}</span>
                        <ExternalLink className="w-3 h-3 opacity-0 group-hover:opacity-70 transition-opacity flex-shrink-0"
                          style={{ color: meta.color }} />
                      </a>
                      {stats ? (
                        <p className="text-xs text-slate-500">
                          {stats.uniqueSolved} solved · {stats.acceptanceRate}% AC
                        </p>
                      ) : (
                        <p className="text-xs text-slate-600">Sync to see stats</p>
                      )}
                    </>
                  ) : (
                    <div>
                      <p className="text-xs text-slate-600 mb-1">Not connected</p>
                      <Link href="/profile"
                        className="text-xs hover:underline flex items-center gap-1"
                        style={{ color: meta.color }}>
                        <Link2 className="w-3 h-3" /> Add handle
                      </Link>
                    </div>
                  )}
                </motion.div>
              );
            })}
          </div>
        </div>
      </div>

      {/* Quick actions */}
      <div className="grid grid-cols-3 gap-3 mb-6">
        {[
          { href: "/analytics",       icon: Zap,        label: "Deep Analytics",   color: "#bf5fff" },
          { href: "/recommendations", icon: Trophy,      label: "Curated Problems", color: "#39ff14" },
          { href: "/ai-recommend",    icon: BrainCircuit,label: "AI Coach",         color: "#ff6b35" },
        ].map((a) => (
          <Link key={a.href} href={a.href}>
            <motion.div whileHover={{ scale: 1.03, y: -2 }} whileTap={{ scale: 0.97 }}
              className="glass-card rounded-xl p-4 flex items-center gap-3 cursor-pointer group">
              <div className="w-9 h-9 rounded-lg flex items-center justify-center flex-shrink-0"
                style={{ background: `${a.color}18`, border: `1px solid ${a.color}30` }}>
                <a.icon className="w-4 h-4" style={{ color: a.color }} />
              </div>
              <span className="text-sm font-semibold text-slate-300 group-hover:text-white transition-colors">
                {a.label}
              </span>
            </motion.div>
          </Link>
        ))}
      </div>

      {/* Recent Submissions */}
      <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.3 }}
        className="glass-card rounded-2xl p-5">
        <h3 className="text-sm font-display font-bold text-white mb-4 flex items-center gap-2">
          <Clock className="w-4 h-4 text-cyan-400" />
          Recent Submissions
          <span className="text-slate-600 font-mono font-normal text-xs">all platforms</span>
        </h3>

        {loadingSubs ? (
          <div className="space-y-2">
            {[...Array(6)].map((_, i) => (
              <div key={i} className="h-12 bg-white/3 rounded-xl animate-pulse" />
            ))}
          </div>
        ) : !recentSubs?.length ? (
          <div className="text-center py-10">
            <p className="text-slate-500 text-sm mb-2">No submissions synced yet.</p>
            <button onClick={() => syncMutation.mutate()}
              className="text-xs text-cyan-400 hover:underline">
              Sync now →
            </button>
          </div>
        ) : (
          <div className="space-y-1.5">
            {recentSubs.map((sub: any, i: number) => {
              const meta = PLATFORM_META[sub.platform as keyof typeof PLATFORM_META];
              const pColor = meta?.color || "#888";
              const vColor = VERDICT_COLOR[sub.verdict] || "#888";
              return (
                <motion.div key={sub.id}
                  initial={{ opacity: 0, x: -8 }} animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: i * 0.035 }}
                  className="flex items-center gap-3 px-3 py-2.5 rounded-xl hover:bg-white/4 transition-colors group">

                  {/* Platform badge */}
                  <div className="w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0
                                  text-xs font-display font-bold"
                    style={{ background: `${pColor}18`, color: pColor, border: `1px solid ${pColor}25` }}>
                    {meta?.short || sub.platform.slice(0, 2)}
                  </div>

                  {/* Problem name — clickable to actual problem */}
                  <div className="flex-1 min-w-0">
                    <a href={sub.problemUrl} target="_blank" rel="noopener noreferrer"
                      className="text-sm text-slate-200 hover:text-white font-semibold truncate
                                 flex items-center gap-1.5 group-hover:underline decoration-dotted">
                      {sub.problemTitle}
                      <ExternalLink className="w-3 h-3 flex-shrink-0 opacity-0 group-hover:opacity-50 transition-opacity" />
                    </a>
                    <p className="text-xs text-slate-600">{sub.timeAgo}</p>
                  </div>

                  {/* Topic chip */}
                  {sub.topics?.[0] && (
                    <span className="hidden sm:block text-xs px-2 py-0.5 rounded-full flex-shrink-0 truncate max-w-[90px]"
                      style={{ background: "rgba(191,95,255,0.1)", color: "#bf5fff" }}>
                      {sub.topics[0]}
                    </span>
                  )}

                  {/* Verdict */}
                  <span className="text-xs font-mono font-bold px-2 py-0.5 rounded flex-shrink-0"
                    style={{ background: `${vColor}15`, color: vColor }}>
                    {sub.verdictLabel}
                  </span>
                </motion.div>
              );
            })}
          </div>
        )}
      </motion.div>
    </>
  );
}