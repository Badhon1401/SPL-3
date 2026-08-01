"use client";

import { motion } from "framer-motion";
import CountUp from "react-countup";
import { LucideIcon } from "lucide-react";

interface StatCardProps {
  label: string;
  value: number | string;
  suffix?: string;
  icon: LucideIcon;
  color: string;
  subtext?: string;
  delay?: number;
  isPercent?: boolean;
}

export function StatCard({ label, value, suffix = "", icon: Icon, color, subtext, delay = 0, isPercent }: StatCardProps) {
  const numValue = typeof value === "number" ? value : parseFloat(value as string) || 0;

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay, duration: 0.5, ease: "easeOut" }}
      className="glass-card rounded-2xl p-6 stat-card corner-brackets group"
      style={{ "--accent-color": color } as React.CSSProperties}
    >
      <div className="flex items-start justify-between mb-4">
        <div
          className="w-11 h-11 rounded-xl flex items-center justify-center"
          style={{ background: `${color}18`, border: `1px solid ${color}30` }}
        >
          <Icon className="w-5 h-5" style={{ color }} />
        </div>
        <div
          className="px-2 py-0.5 rounded-full text-xs font-mono font-semibold"
          style={{ background: `${color}15`, color }}
        >
          LIVE
        </div>
      </div>

      <div className="mt-2">
        <div className="flex items-end gap-1">
          <span
            className="text-3xl font-display font-bold"
            style={{ color }}
          >
            <CountUp
              end={numValue}
              duration={2}
              delay={delay + 0.2}
              decimals={isPercent ? 1 : 0}
              separator=","
            />
          </span>
          {suffix && (
            <span className="text-lg font-display mb-0.5" style={{ color: `${color}80` }}>
              {suffix}
            </span>
          )}
        </div>
        <p className="text-slate-400 text-sm font-semibold mt-1">{label}</p>
        {subtext && <p className="text-slate-600 text-xs mt-0.5">{subtext}</p>}
      </div>

      {/* Bottom glow line */}
      <div
        className="absolute bottom-0 left-6 right-6 h-px rounded-full opacity-0 group-hover:opacity-100 transition-opacity duration-300"
        style={{ background: `linear-gradient(90deg, transparent, ${color}, transparent)` }}
      />
    </motion.div>
  );
}
