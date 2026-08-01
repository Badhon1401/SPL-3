"use client";

import { motion } from "framer-motion";
import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer, Legend } from "recharts";
import { PieChart as PieIcon } from "lucide-react";

const VERDICT_COLORS: Record<string, string> = {
  ACCEPTED: "#39ff14",
  WRONG_ANSWER: "#ff2d78",
  TIME_LIMIT_EXCEEDED: "#ff6b35",
  MEMORY_LIMIT_EXCEEDED: "#bf5fff",
  RUNTIME_ERROR: "#ffd700",
  COMPILATION_ERROR: "#00f5ff",
  PARTIAL: "#a0aec0",
  SKIPPED: "#4a5568",
};

const VERDICT_SHORT: Record<string, string> = {
  ACCEPTED: "AC",
  WRONG_ANSWER: "WA",
  TIME_LIMIT_EXCEEDED: "TLE",
  MEMORY_LIMIT_EXCEEDED: "MLE",
  RUNTIME_ERROR: "RE",
  COMPILATION_ERROR: "CE",
  PARTIAL: "PT",
  SKIPPED: "SK",
};

const CustomTooltip = ({ active, payload }: any) => {
  if (!active || !payload?.length) return null;
  return (
    <div className="glass rounded-xl px-3 py-2 border border-white/10">
      <p className="text-sm font-semibold" style={{ color: payload[0].payload.fill }}>
        {payload[0].name}
      </p>
      <p className="text-lg font-display font-bold text-white">{payload[0].value}</p>
    </div>
  );
};

export function VerdictDonut({ data }: { data: Record<string, number> }) {
  const chartData = Object.entries(data).map(([name, value]) => ({
    name: VERDICT_SHORT[name] || name,
    fullName: name,
    value,
    fill: VERDICT_COLORS[name] || "#888",
  }));

  const total = chartData.reduce((s, d) => s + d.value, 0);

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ delay: 0.4 }}
      className="glass-card rounded-2xl p-6 h-full"
    >
      <h3 className="text-base font-display font-bold text-white flex items-center gap-2 mb-4">
        <PieIcon className="w-4 h-4 text-purple-400" />
        Verdict Split
      </h3>

      <div className="relative">
        <ResponsiveContainer width="100%" height={180}>
          <PieChart>
            <Pie
              data={chartData}
              cx="50%"
              cy="50%"
              innerRadius={55}
              outerRadius={80}
              paddingAngle={3}
              dataKey="value"
            >
              {chartData.map((entry, i) => (
                <Cell key={i} fill={entry.fill} stroke="transparent" />
              ))}
            </Pie>
            <Tooltip content={<CustomTooltip />} />
          </PieChart>
        </ResponsiveContainer>
        {/* Center label */}
        <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
          <span className="text-2xl font-display font-bold text-white">{total}</span>
          <span className="text-xs text-slate-500">Total</span>
        </div>
      </div>

      {/* Legend */}
      <div className="grid grid-cols-2 gap-1.5 mt-2">
        {chartData.map((d) => (
          <div key={d.name} className="flex items-center gap-2">
            <div className="w-2 h-2 rounded-full flex-shrink-0" style={{ background: d.fill }} />
            <span className="text-xs text-slate-400 truncate">{d.name}</span>
            <span className="text-xs font-mono ml-auto" style={{ color: d.fill }}>{d.value}</span>
          </div>
        ))}
      </div>
    </motion.div>
  );
}
