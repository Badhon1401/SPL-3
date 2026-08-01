"use client";

import { useQuery } from "@tanstack/react-query";
import { motion } from "framer-motion";
import {
  BarChart3, AlertTriangle, CheckCircle2, Lightbulb, TrendingUp,
  Target, Flame, PieChart, Activity, Cpu,
} from "lucide-react";
import { analyticsApi } from "@/lib/api";
import {
  RadarChart, Radar, PolarGrid, PolarAngleAxis,
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Cell,
  PieChart as RechartsPie, Pie,
  AreaChart, Area,
} from "recharts";
import { ActivityHeatmap } from "@/components/charts/DifficultyBar";

// ─── Insight engine ────────────────────────────────────────────────────────────

type InsightLevel = "critical" | "warning" | "success" | "tip" | "pattern";

interface Insight {
  level: InsightLevel;
  title: string;
  body: string;
  icon: string;
}

function generateInsights(a: any): Insight[] {
  const insights: Insight[] = [];
  if (!a) return insights;

  // 1. Critical weaknesses
  const weakEntries = Object.entries(a.weaknessScores || {})
    .filter(([, s]) => (s as number) > 0.55)
    .sort((x, y) => (y[1] as number) - (x[1] as number));
  if (weakEntries.length > 0) {
    const [t, s] = weakEntries[0];
    insights.push({ level: "critical", icon: "🚨", title: "Critical Weakness",
      body: `Your "${t}" failure rate is ${Math.round((s as number)*100)}%. Every 2nd attempt fails — this needs focused daily practice before you level up.` });
  }

  // 2. Zero-accepted topic
  const zeroTopics = Object.entries(a.weaknessScores || {}).filter(([, s]) => (s as number) >= 1.0);
  if (zeroTopics.length > 0)
    insights.push({ level: "critical", icon: "❌", title: "Blind Spot Detected",
      body: `You have 0 accepted submissions in: ${zeroTopics.map(([t]) => t).slice(0,3).join(", ")}. These topics are complete blind spots requiring attention.` });

  // 3. Low acceptance rate
  if (a.acceptanceRate < 35)
    insights.push({ level: "warning", icon: "⚡", title: "Submit More Carefully",
      body: `Only ${a.acceptanceRate}% of your submissions are accepted. Spending 10 more minutes thinking before submitting could dramatically improve this.` });
  else if (a.acceptanceRate > 80)
    insights.push({ level: "success", icon: "🎯", title: "Sharpshooter",
      body: `${a.acceptanceRate}% acceptance rate puts you in the top tier for submission quality. Now focus on harder problem difficulty to grow.` });

  // 4. Difficulty balance
  const diff = a.difficultyBreakdown || {};
  const easyCount = (diff.Beginner || 0) + (diff.Easy || 0);
  const hardCount = (diff.Hard || 0) + (diff.Expert || 0);
  const totalDiff = Object.values(diff).reduce((x: any, y: any) => x + y, 0) as number;
  if (totalDiff > 20 && easyCount / totalDiff > 0.75)
    insights.push({ level: "warning", icon: "📈", title: "Comfort Zone Alert",
      body: `${Math.round(easyCount/totalDiff*100)}% of your solves are Easy/Beginner. You're in a comfort zone — push into Medium problems to see real rating gains.` });
  if (totalDiff > 20 && hardCount / totalDiff > 0.4)
    insights.push({ level: "success", icon: "💪", title: "Elite Problem Solver",
      body: `${Math.round(hardCount/totalDiff*100)}% of your solves are Hard/Expert — impressive. You're grinding at the level where rating gains compound.` });

  // 5. Streak pattern
  if (a.currentStreak >= 14)
    insights.push({ level: "success", icon: "🔥", title: `${a.currentStreak}-Day Warrior`,
      body: `You've coded for ${a.currentStreak} consecutive days. Consistency at this level correlates strongly with competitive programming success.` });
  else if (a.currentStreak === 0 && a.longestStreak >= 7)
    insights.push({ level: "warning", icon: "⏸️", title: "Streak Broken — Restart Today",
      body: `Your best streak was ${a.longestStreak} days but you're currently at 0. Comeback is one session away — the hardest day is day 1.` });

  // 6. Topic diversity
  const topicCount = Object.keys(a.topicBreakdown || {}).length;
  if (topicCount < 6 && a.uniqueProblemsSolved > 25)
    insights.push({ level: "tip", icon: "🗺️", title: "Explore More Topics",
      body: `You've only touched ${topicCount} distinct topics despite ${a.uniqueProblemsSolved} problems solved. Strong competitive programmers cover 15+ topic areas.` });

  // 7. Platform imbalance
  const platforms = Object.entries(a.platformBreakdown || {});
  if (platforms.length >= 2) {
    const total = platforms.reduce((s: number, [, p]: any) => s + p.totalSubmissions, 0);
    const [dom, domStats] = platforms.sort((p: any, q: any) => q[1].totalSubmissions - p[1].totalSubmissions)[0] as any;
    if (domStats.totalSubmissions / total > 0.85)
      insights.push({ level: "tip", icon: "🌐", title: "Platform Imbalance",
        body: `${Math.round(domStats.totalSubmissions/total*100)}% of activity is on ${dom}. Other platforms expose you to different styles — LeetCode for interviews, AtCoder for math.` });
  }

  // 8. Recent activity trend (last 30 vs previous 30 days)
  const heatmap = a.activityHeatmap || {};
  const now = new Date();
  let last30 = 0, prev30 = 0;
  Object.entries(heatmap).forEach(([d, cnt]) => {
    const diff = (now.getTime() - new Date(d).getTime()) / 86400000;
    if (diff <= 30) last30 += cnt as number;
    else if (diff <= 60) prev30 += cnt as number;
  });
  if (prev30 > 0 && last30 > prev30 * 1.3)
    insights.push({ level: "success", icon: "📊", title: "Momentum Building",
      body: `Your last 30 days had ${last30} submissions vs ${prev30} the month before — a ${Math.round((last30-prev30)/prev30*100)}% increase. You're trending upward!` });
  else if (prev30 > 0 && last30 < prev30 * 0.5)
    insights.push({ level: "warning", icon: "📉", title: "Activity Dropped",
      body: `Your submissions dropped from ${prev30} to ${last30} over the last month — a ${Math.round((prev30-last30)/prev30*100)}% decline. What happened? Time to re-engage.` });

  // 9. Strong topic recognition
  const strong = Object.entries(a.topicBreakdown || {})
    .sort((x,y) => (y[1] as number) - (x[1] as number)).slice(0,3);
  if (strong.length > 0 && (strong[0][1] as number) >= 15)
    insights.push({ level: "pattern", icon: "⭐", title: "Signature Strengths",
      body: `Your strongest topics are ${strong.map(([t,c]) => `${t} (${c})`).join(", ")}. These are your competitive edges — use them in contests strategically.` });

  return insights;
}

const INSIGHT_STYLES: Record<InsightLevel, { border: string; bg: string; badge: string }> = {
  critical: { border: "#ff2d78", bg: "rgba(255,45,120,0.06)", badge: "rgba(255,45,120,0.15)" },
  warning:  { border: "#ff6b35", bg: "rgba(255,107,53,0.06)", badge: "rgba(255,107,53,0.15)" },
  success:  { border: "#39ff14", bg: "rgba(57,255,20,0.06)",  badge: "rgba(57,255,20,0.15)" },
  tip:      { border: "#00f5ff", bg: "rgba(0,245,255,0.06)",  badge: "rgba(0,245,255,0.15)" },
  pattern:  { border: "#bf5fff", bg: "rgba(191,95,255,0.06)", badge: "rgba(191,95,255,0.15)" },
};

// ─── Chart helpers ─────────────────────────────────────────────────────────────

const PLATFORM_COLORS: Record<string,string> = {
  CODEFORCES:"#ff6b35", LEETCODE:"#ffd700", ATCODER:"#00f5ff", CODECHEF:"#39ff14"
};
const DIFF_COLORS: Record<string,string> = {
  Beginner:"#39ff14", Easy:"#00f5ff", Medium:"#bf5fff", Hard:"#ff6b35", Expert:"#ff2d78", Unknown:"#555"
};
const VERDICT_COLORS: Record<string,string> = {
  "Accepted":"#39ff14","Wrong Answer":"#ff2d78","Time Limit Exceeded":"#ff6b35",
  "Memory Limit Exceeded":"#bf5fff","Runtime Error":"#ffd700","Compilation Error":"#888",
  "Partial":"#aaa","Skipped":"#444"
};

const CTip = ({ active, payload }: any) => {
  if (!active || !payload?.length) return null;
  return (
    <div className="glass rounded-xl px-3 py-2 border border-white/10 text-xs">
      <p className="text-white font-semibold">{payload[0].name || payload[0].dataKey}</p>
      <p style={{ color: payload[0].color || "#00f5ff" }}>{payload[0].value}</p>
    </div>
  );
};

export default function AnalyticsPage() {
  const { data: a, isLoading } = useQuery({
    queryKey: ["analytics"],
    queryFn: () => analyticsApi.getMyAnalytics().then((r) => r.data.data),
  });

  const insights = generateInsights(a);

  // Prepare chart data
  const platformData = Object.entries(a?.platformBreakdown || {}).map(([p, s]: any) => ({
    name: p, solved: s.uniqueSolved, total: s.totalSubmissions, rate: s.acceptanceRate,
    color: PLATFORM_COLORS[p] || "#888",
  }));

  const diffData = Object.entries(a?.difficultyBreakdown || {})
    .sort((x,y) => ["Beginner","Easy","Medium","Hard","Expert","Unknown"].indexOf(x[0]) - ["Beginner","Easy","Medium","Hard","Expert","Unknown"].indexOf(y[0]))
    .map(([d,c]) => ({ name: d, count: c, color: DIFF_COLORS[d] || "#888" }));

  const verdictData = Object.entries(a?.verdictDistribution || {}).map(([v,c]) => ({
    name: v, value: c as number, fill: VERDICT_COLORS[v] || "#888"
  }));

  const radarData = Object.entries(a?.weaknessScores || {}).slice(0,8).map(([t,s]) => ({
    topic: t.length > 12 ? t.slice(0,12)+"…" : t,
    weakness: Math.round((s as number)*100),
    strength: Math.round((1-(s as number))*100),
  }));

  const topicData = Object.entries(a?.topicBreakdown || {})
    .sort((x,y) => (y[1] as number) - (x[1] as number)).slice(0,12)
    .map(([t,c], i) => ({ name: t, count: c, color: `hsl(${i*30},70%,60%)` }));

  const cfTrend = (a?.ratingTrend || []).slice(-20);

  if (isLoading) return (
    <div className="space-y-5">
      {[...Array(4)].map((_,i) => <div key={i} className="glass-card rounded-2xl h-56 animate-pulse" />)}
    </div>
  );

  return (
    <>
      <motion.div initial={{ opacity: 0, y: -10 }} animate={{ opacity: 1, y: 0 }} className="mb-7">
        <h1 className="text-2xl font-display font-bold text-white flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-purple-400/15 flex items-center justify-center">
            <BarChart3 className="w-5 h-5 text-purple-400" />
          </div>
          Deep Analytics
        </h1>
        <p className="text-slate-400 text-sm mt-1">Full breakdown + hidden pattern findings from your combined data</p>
      </motion.div>

      {/* ─── Insights panel ─────────────────────────────────────────────── */}
      {insights.length > 0 && (
        <motion.section initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.1 }} className="mb-6">
          <h2 className="text-sm font-display font-bold text-slate-400 mb-3 flex items-center gap-2">
            <Lightbulb className="w-4 h-4 text-yellow-400" />
            HIDDEN FINDINGS &amp; INSIGHTS
          </h2>
          <div className="grid sm:grid-cols-2 gap-3">
            {insights.map((ins, i) => {
              const s = INSIGHT_STYLES[ins.level];
              return (
                <motion.div key={i} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: 0.15 + i * 0.05 }}
                  className="rounded-2xl p-4 relative overflow-hidden"
                  style={{ background: s.bg, border: `1px solid ${s.border}35` }}>
                  <div className="absolute top-0 left-0 right-0 h-0.5 opacity-40"
                    style={{ background: `linear-gradient(90deg, transparent, ${s.border}, transparent)` }} />
                  <div className="flex items-start gap-3">
                    <span className="text-2xl flex-shrink-0">{ins.icon}</span>
                    <div>
                      <p className="text-sm font-display font-bold text-white mb-1">{ins.title}</p>
                      <p className="text-xs text-slate-400 leading-relaxed">{ins.body}</p>
                    </div>
                  </div>
                </motion.div>
              );
            })}
          </div>
        </motion.section>
      )}

      {/* ─── Platform breakdown ─────────────────────────────────────────── */}
      {platformData.length > 0 && (
        <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.2 }}
          className="glass-card rounded-2xl p-5 mb-5">
          <h3 className="text-sm font-display font-bold text-white mb-4 flex items-center gap-2">
            <Cpu className="w-4 h-4 text-cyan-400" />
            Platform Breakdown
          </h3>
          <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-3 mb-5">
            {platformData.map((p) => (
              <div key={p.name} className="rounded-xl p-3 text-center"
                style={{ background: `${p.color}0e`, border: `1px solid ${p.color}25` }}>
                <p className="text-xs font-mono font-bold mb-1" style={{ color: p.color }}>{p.name}</p>
                <p className="text-2xl font-display font-black" style={{ color: p.color }}>{p.solved}</p>
                <p className="text-xs text-slate-500">solved · {p.rate}% AC</p>
              </div>
            ))}
          </div>
          <ResponsiveContainer width="100%" height={160}>
            <BarChart data={platformData} margin={{ left: -20, right: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.04)" vertical={false} />
              <XAxis dataKey="name" tick={{ fill: "#64748b", fontSize: 11, fontFamily: "JetBrains Mono" }} tickLine={false} axisLine={false} />
              <YAxis tick={{ fill: "#64748b", fontSize: 10 }} tickLine={false} axisLine={false} />
              <Tooltip content={<CTip />} />
              <Bar dataKey="solved" radius={[4,4,0,0]} name="Solved">
                {platformData.map((p,i) => <Cell key={i} fill={p.color} fillOpacity={0.85} />)}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </motion.div>
      )}

      {/* ─── CF Rating trend + Difficulty ───────────────────────────────── */}
      <div className="grid lg:grid-cols-2 gap-5 mb-5">
        {cfTrend.length > 0 && (
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.25 }}
            className="glass-card rounded-2xl p-5">
            <h3 className="text-sm font-display font-bold text-white mb-4 flex items-center gap-2">
              <TrendingUp className="w-4 h-4 text-cyan-400" />
              Codeforces Rating Trend
            </h3>
            <ResponsiveContainer width="100%" height={180}>
              <AreaChart data={cfTrend} margin={{ left: -20 }}>
                <defs>
                  <linearGradient id="cfGrad" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#00f5ff" stopOpacity={0.3} />
                    <stop offset="95%" stopColor="#00f5ff" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.04)" />
                <XAxis dataKey="date" tick={{ fill: "#64748b", fontSize: 9 }} tickLine={false} axisLine={false} interval="preserveStartEnd" />
                <YAxis tick={{ fill: "#64748b", fontSize: 10 }} tickLine={false} axisLine={false} />
                <Tooltip content={<CTip />} />
                <Area type="monotone" dataKey="rating" stroke="#00f5ff" strokeWidth={2.5}
                  fill="url(#cfGrad)" dot={false}
                  activeDot={{ r: 4, fill: "#00f5ff", stroke: "#04050d", strokeWidth: 2 }} />
              </AreaChart>
            </ResponsiveContainer>
          </motion.div>
        )}

        {/* Difficulty breakdown */}
        <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.3 }}
          className="glass-card rounded-2xl p-5">
          <h3 className="text-sm font-display font-bold text-white mb-4 flex items-center gap-2">
            <Target className="w-4 h-4 text-orange-400" />
            Difficulty Distribution
          </h3>
          <ResponsiveContainer width="100%" height={180}>
            <BarChart data={diffData} margin={{ left: -20 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.04)" vertical={false} />
              <XAxis dataKey="name" tick={{ fill: "#64748b", fontSize: 11 }} tickLine={false} axisLine={false} />
              <YAxis tick={{ fill: "#64748b", fontSize: 10 }} tickLine={false} axisLine={false} />
              <Tooltip content={<CTip />} />
              <Bar dataKey="count" name="Solved" radius={[4,4,0,0]}>
                {diffData.map((d,i) => <Cell key={i} fill={d.color} fillOpacity={0.85} />)}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </motion.div>
      </div>

      {/* ─── Weakness Radar + Verdict ────────────────────────────────────── */}
      <div className="grid lg:grid-cols-2 gap-5 mb-5">
        {radarData.length > 0 && (
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.35 }}
            className="glass-card rounded-2xl p-5">
            <h3 className="text-sm font-display font-bold text-white mb-4 flex items-center gap-2">
              <AlertTriangle className="w-4 h-4 text-yellow-400" />
              Weakness vs Strength Radar
            </h3>
            <ResponsiveContainer width="100%" height={220}>
              <RadarChart data={radarData}>
                <PolarGrid stroke="rgba(255,255,255,0.05)" />
                <PolarAngleAxis dataKey="topic" tick={{ fill: "#64748b", fontSize: 10, fontFamily: "Rajdhani" }} />
                <Radar name="Weakness %" dataKey="weakness" stroke="#ff2d78" fill="#ff2d78" fillOpacity={0.15} strokeWidth={1.5} />
                <Radar name="Strength %" dataKey="strength" stroke="#00f5ff" fill="#00f5ff" fillOpacity={0.1} strokeWidth={1.5} />
                <Tooltip contentStyle={{ background: "rgba(13,18,37,0.9)", border: "1px solid rgba(0,245,255,0.2)", borderRadius: "8px" }} />
              </RadarChart>
            </ResponsiveContainer>
          </motion.div>
        )}

        {verdictData.length > 0 && (
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.4 }}
            className="glass-card rounded-2xl p-5">
            <h3 className="text-sm font-display font-bold text-white mb-4 flex items-center gap-2">
              <PieChart className="w-4 h-4 text-purple-400" />
              Verdict Distribution (All Platforms)
            </h3>
            <ResponsiveContainer width="100%" height={180}>
              <RechartsPie>
                <defs>
                  {verdictData.map((v,i) => <linearGradient key={i} id={`vg${i}`} />)}
                </defs>
                <Pie data={verdictData} cx="50%" cy="50%" innerRadius={50} outerRadius={80}
                  paddingAngle={3} dataKey="value">
                  {verdictData.map((v,i) => <Cell key={i} fill={v.fill} stroke="transparent" />)}
                </Pie>
                <Tooltip contentStyle={{ background: "rgba(13,18,37,0.9)", border: "1px solid rgba(255,255,255,0.1)", borderRadius: "8px" }} />
              </RechartsPie>
            </ResponsiveContainer>
            <div className="grid grid-cols-2 gap-1.5 mt-2">
              {verdictData.slice(0,6).map((v) => (
                <div key={v.name} className="flex items-center gap-1.5">
                  <div className="w-2 h-2 rounded-full flex-shrink-0" style={{ background: v.fill }} />
                  <span className="text-xs text-slate-400 truncate">{v.name}</span>
                  <span className="text-xs font-mono ml-auto" style={{ color: v.fill }}>{v.value}</span>
                </div>
              ))}
            </div>
          </motion.div>
        )}
      </div>

      {/* ─── Topic coverage ──────────────────────────────────────────────── */}
      {topicData.length > 0 && (
        <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.45 }}
          className="glass-card rounded-2xl p-5 mb-5">
          <h3 className="text-sm font-display font-bold text-white mb-5 flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 text-green-400" />
            Topic Coverage (Combined, All Platforms)
          </h3>
          <div className="space-y-2.5">
            {topicData.map((t, i) => {
              const pct = Math.round((t.count as number) / (topicData[0].count as number) * 100);
              return (
                <motion.div key={t.name} initial={{ opacity: 0, x: -8 }} animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: 0.5 + i * 0.03 }}
                  className="flex items-center gap-3">
                  <div className="w-32 text-xs text-slate-300 font-semibold capitalize truncate flex-shrink-0">{t.name}</div>
                  <div className="flex-1 h-2 bg-white/5 rounded-full overflow-hidden">
                    <motion.div className="h-full rounded-full"
                      style={{ background: t.color }}
                      initial={{ width: 0 }} animate={{ width: `${pct}%` }}
                      transition={{ delay: 0.55 + i * 0.03, duration: 0.7, ease: "easeOut" }} />
                  </div>
                  <span className="w-10 text-right text-xs font-mono flex-shrink-0" style={{ color: t.color }}>
                    {t.count as number}
                  </span>
                </motion.div>
              );
            })}
          </div>
        </motion.div>
      )}

      {/* ─── Activity Heatmap ────────────────────────────────────────────── */}
      <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.5 }}>
        <ActivityHeatmap data={a?.activityHeatmap || {}} />
      </motion.div>
    </>
  );
}