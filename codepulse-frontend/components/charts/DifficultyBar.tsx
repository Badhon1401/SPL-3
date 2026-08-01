"use client";

import { motion } from "framer-motion";
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Cell } from "recharts";
import { BarChart2, Calendar } from "lucide-react";

const DIFF_COLORS = {
  Beginner: "#39ff14",
  Easy: "#00f5ff",
  Medium: "#bf5fff",
  Hard: "#ff6b35",
  Expert: "#ff2d78",
  Unknown: "#4a5568",
};

export function DifficultyBar({ data }: { data: Record<string, number> }) {
  const ORDER = ["Beginner", "Easy", "Medium", "Hard", "Expert", "Unknown"];
  const chartData = ORDER.filter((k) => data[k] !== undefined).map((k) => ({
    name: k,
    count: data[k],
    color: DIFF_COLORS[k as keyof typeof DIFF_COLORS] || "#888",
  }));

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ delay: 0.5 }}
      className="glass-card rounded-2xl p-6"
    >
      <h3 className="text-base font-display font-bold text-white flex items-center gap-2 mb-5">
        <BarChart2 className="w-4 h-4 text-orange-400" />
        Difficulty Breakdown
      </h3>
      <ResponsiveContainer width="100%" height={200}>
        <BarChart data={chartData} margin={{ top: 0, right: 0, left: -25, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.04)" vertical={false} />
          <XAxis
            dataKey="name"
            tick={{ fill: "#64748b", fontSize: 11, fontFamily: "Rajdhani" }}
            tickLine={false}
            axisLine={false}
          />
          <YAxis
            tick={{ fill: "#64748b", fontSize: 10, fontFamily: "JetBrains Mono" }}
            tickLine={false}
            axisLine={false}
          />
          <Tooltip
            contentStyle={{
              background: "rgba(13,18,37,0.9)",
              border: "1px solid rgba(0,245,255,0.2)",
              borderRadius: "8px",
              fontFamily: "Rajdhani",
            }}
          />
          <Bar dataKey="count" radius={[4, 4, 0, 0]}>
            {chartData.map((entry, i) => (
              <Cell key={i} fill={entry.color} fillOpacity={0.85} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </motion.div>
  );
}

// ─── Activity Heatmap ──────────────────────────────────────────────────────────
export function ActivityHeatmap({ data }: { data: Record<string, number> }) {
  // Build last 26 weeks of days
  const weeks: { date: string; count: number }[][] = [];
  const today = new Date();

  for (let w = 25; w >= 0; w--) {
    const week: { date: string; count: number }[] = [];
    for (let d = 0; d < 7; d++) {
      const day = new Date(today);
      day.setDate(today.getDate() - w * 7 - (6 - d));
      const key = day.toISOString().slice(0, 10);
      week.push({ date: key, count: data[key] || 0 });
    }
    weeks.push(week);
  }

  const max = Math.max(...Object.values(data), 1);

  const getColor = (count: number) => {
    if (count === 0) return "rgba(255,255,255,0.03)";
    const intensity = count / max;
    if (intensity < 0.25) return "rgba(0,245,255,0.2)";
    if (intensity < 0.5) return "rgba(0,245,255,0.45)";
    if (intensity < 0.75) return "rgba(0,245,255,0.7)";
    return "rgba(0,245,255,0.95)";
  };

  const totalDays = Object.values(data).filter(Boolean).length;
  const totalSubs = Object.values(data).reduce((a, b) => a + b, 0);

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ delay: 0.55 }}
      className="glass-card rounded-2xl p-6"
    >
      <div className="flex items-center justify-between mb-5">
        <h3 className="text-base font-display font-bold text-white flex items-center gap-2">
          <Calendar className="w-4 h-4 text-cyan-400" />
          Activity Heatmap
          <span className="text-xs text-slate-500 font-mono font-normal">(last 26 weeks)</span>
        </h3>
        <div className="flex items-center gap-4 text-xs text-slate-400">
          <span><span className="text-white font-bold">{totalDays}</span> active days</span>
          <span><span className="text-cyan-400 font-bold">{totalSubs}</span> submissions</span>
        </div>
      </div>

      <div className="overflow-x-auto">
        <div className="flex gap-1 min-w-fit">
          {weeks.map((week, wi) => (
            <div key={wi} className="flex flex-col gap-1">
              {week.map((day) => (
                <div
                  key={day.date}
                  className="w-3 h-3 rounded-sm transition-all duration-200 hover:scale-125 cursor-default relative group"
                  style={{ background: getColor(day.count) }}
                  title={`${day.date}: ${day.count} submissions`}
                >
                  {day.count > 0 && (
                    <div className="absolute -top-8 left-1/2 -translate-x-1/2 bg-slate-800 text-white text-xs rounded px-2 py-1 whitespace-nowrap opacity-0 group-hover:opacity-100 pointer-events-none z-10 border border-white/10">
                      {day.date}: {day.count}
                    </div>
                  )}
                </div>
              ))}
            </div>
          ))}
        </div>
      </div>

      <div className="flex items-center gap-2 mt-3 justify-end">
        <span className="text-xs text-slate-500">Less</span>
        {["rgba(255,255,255,0.03)", "rgba(0,245,255,0.2)", "rgba(0,245,255,0.45)", "rgba(0,245,255,0.7)", "rgba(0,245,255,0.95)"].map((c, i) => (
          <div key={i} className="w-3 h-3 rounded-sm" style={{ background: c }} />
        ))}
        <span className="text-xs text-slate-500">More</span>
      </div>
    </motion.div>
  );
}
