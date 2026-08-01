"use client";

import { motion } from "framer-motion";
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid,
  Tooltip, ResponsiveContainer, Area, AreaChart, ReferenceLine,
} from "recharts";
import { TrendingUp } from "lucide-react";

interface RatingPoint { date: string; rating: number }

const CustomTooltip = ({ active, payload, label }: any) => {
  if (!active || !payload?.length) return null;
  const rating = payload[0].value;
  const rank = rating >= 2400 ? "Grandmaster" : rating >= 2100 ? "Master" : rating >= 1900 ? "Candidate Master" : rating >= 1600 ? "Expert" : rating >= 1400 ? "Specialist" : "Pupil";
  return (
    <div className="glass rounded-xl px-4 py-3 border border-cyan-400/20">
      <p className="text-slate-400 text-xs mb-1 font-mono">{label}</p>
      <p className="text-2xl font-display font-bold text-cyan-400">{rating}</p>
      <p className="text-xs text-slate-500">{rank}</p>
    </div>
  );
};

export function RatingLineChart({ data }: { data: RatingPoint[] }) {
  if (!data.length) {
    return (
      <div className="glass-card rounded-2xl p-6 h-72 flex items-center justify-center">
        <p className="text-slate-500 font-body">No rating data — sync Codeforces</p>
      </div>
    );
  }

  const maxRating = Math.max(...data.map((d) => d.rating));
  const minRating = Math.min(...data.map((d) => d.rating));

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ delay: 0.3 }}
      className="glass-card rounded-2xl p-6"
    >
      <div className="flex items-center justify-between mb-5">
        <h3 className="text-base font-display font-bold text-white flex items-center gap-2">
          <TrendingUp className="w-4 h-4 text-cyan-400" />
          Rating Trajectory
        </h3>
        <div className="flex items-center gap-4">
          <div className="text-right">
            <p className="text-xs text-slate-500">Peak</p>
            <p className="text-sm font-display font-bold text-cyan-400">{maxRating}</p>
          </div>
          <div className="text-right">
            <p className="text-xs text-slate-500">Current</p>
            <p className="text-sm font-display font-bold text-white">{data[data.length - 1]?.rating}</p>
          </div>
        </div>
      </div>

      <ResponsiveContainer width="100%" height={200}>
        <AreaChart data={data} margin={{ top: 5, right: 5, left: -20, bottom: 0 }}>
          <defs>
            <linearGradient id="ratingGrad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor="#00f5ff" stopOpacity={0.3} />
              <stop offset="95%" stopColor="#00f5ff" stopOpacity={0} />
            </linearGradient>
          </defs>
          <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.04)" />
          <XAxis
            dataKey="date"
            tick={{ fill: "#64748b", fontSize: 10, fontFamily: "JetBrains Mono" }}
            tickLine={false}
            axisLine={false}
            interval="preserveStartEnd"
          />
          <YAxis
            tick={{ fill: "#64748b", fontSize: 10, fontFamily: "JetBrains Mono" }}
            tickLine={false}
            axisLine={false}
            domain={[minRating - 100, maxRating + 100]}
          />
          <Tooltip content={<CustomTooltip />} />
          <ReferenceLine y={2400} stroke="#ff2d78" strokeDasharray="4 4" strokeWidth={1} opacity={0.4} />
          <ReferenceLine y={2100} stroke="#ff6b35" strokeDasharray="4 4" strokeWidth={1} opacity={0.3} />
          <Area
            type="monotone"
            dataKey="rating"
            stroke="#00f5ff"
            strokeWidth={2.5}
            fill="url(#ratingGrad)"
            dot={false}
            activeDot={{ r: 5, fill: "#00f5ff", stroke: "#04050d", strokeWidth: 2 }}
          />
        </AreaChart>
      </ResponsiveContainer>
    </motion.div>
  );
}
