"use client";

import { motion } from "framer-motion";
import { Radar, RadarChart, PolarGrid, PolarAngleAxis, ResponsiveContainer, Tooltip } from "recharts";
import { AlertTriangle } from "lucide-react";

export function WeaknessRadar({ data }: { data: Record<string, number> }) {
  const entries = Object.entries(data)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 8);

  const chartData = entries.map(([topic, score]) => ({
    topic: topic.length > 10 ? topic.slice(0, 10) + "…" : topic,
    weakness: Math.round(score * 100),
    strength: Math.round((1 - score) * 100),
  }));

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ delay: 0.45 }}
      className="glass-card rounded-2xl p-6"
    >
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-base font-display font-bold text-white flex items-center gap-2">
          <AlertTriangle className="w-4 h-4 text-yellow-400" />
          Weakness Radar
        </h3>
        <div className="flex items-center gap-3 text-xs">
          <span className="flex items-center gap-1.5">
            <div className="w-2 h-2 rounded-full bg-pink-500" />
            <span className="text-slate-400">Weakness</span>
          </span>
          <span className="flex items-center gap-1.5">
            <div className="w-2 h-2 rounded-full bg-cyan-400" />
            <span className="text-slate-400">Strength</span>
          </span>
        </div>
      </div>

      {chartData.length === 0 ? (
        <div className="h-52 flex items-center justify-center text-slate-500">
          No topic data available
        </div>
      ) : (
        <ResponsiveContainer width="100%" height={220}>
          <RadarChart data={chartData}>
            <PolarGrid stroke="rgba(255,255,255,0.05)" />
            <PolarAngleAxis
              dataKey="topic"
              tick={{ fill: "#64748b", fontSize: 10, fontFamily: "Rajdhani" }}
            />
            <Radar
              name="Weakness"
              dataKey="weakness"
              stroke="#ff2d78"
              fill="#ff2d78"
              fillOpacity={0.15}
              strokeWidth={1.5}
            />
            <Radar
              name="Strength"
              dataKey="strength"
              stroke="#00f5ff"
              fill="#00f5ff"
              fillOpacity={0.1}
              strokeWidth={1.5}
            />
            <Tooltip
              contentStyle={{
                background: "rgba(13,18,37,0.9)",
                border: "1px solid rgba(0,245,255,0.2)",
                borderRadius: "8px",
                fontFamily: "Rajdhani",
              }}
            />
          </RadarChart>
        </ResponsiveContainer>
      )}
    </motion.div>
  );
}
