"use client";

import { motion } from "framer-motion";
import CountUp from "react-countup";
import { Flame, Calendar, TrendingUp, Star } from "lucide-react";
import { CombinedRatingCalculator } from "@/lib/ratingTier";

interface Props {
  currentStreak: number;
  longestStreak: number;
  totalActiveDays: number;
  ratingTier?: string;
  combinedRating: number;
}

const TIER_COLORS: Record<string, string> = {
  "Beginner":               "#888",
  "Pupil":                  "#aaaaff",
  "Apprentice":             "#00c853",
  "Specialist":             "#00e5ff",
  "Expert":                 "#7c4dff",
  "Candidate Master":       "#aa00ff",
  "Master":                 "#ff6d00",
  "International Master":   "#ff6d00",
  "Grandmaster":            "#f44336",
  "Legendary Grandmaster":  "#f44336",
};

export function StreakWidget({ currentStreak, longestStreak, totalActiveDays, ratingTier, combinedRating }: Props) {
  const tierColor = TIER_COLORS[ratingTier || "Beginner"] || "#888";
  const streakIntensity = Math.min(currentStreak / 30, 1);
  const fireColor = streakIntensity > 0.7 ? "#ff2d78"
                  : streakIntensity > 0.3 ? "#ff6b35"
                  : "#ffd700";

  return (
    <motion.div initial={{ opacity: 0, scale: 0.97 }} animate={{ opacity: 1, scale: 1 }}
      transition={{ delay: 0.1 }}
      className="glass-card rounded-2xl p-5 relative overflow-hidden">

      {/* Background glow */}
      <div className="absolute top-0 right-0 w-32 h-32 rounded-full blur-3xl opacity-20 pointer-events-none"
        style={{ background: tierColor }} />

      {/* Rating tier */}
      <div className="flex items-center justify-between mb-4">
        <div>
          <p className="text-xs text-slate-500 font-mono mb-0.5">CODEPULSE RATING</p>
          <div className="flex items-end gap-2">
            <span className="text-3xl font-display font-black" style={{ color: tierColor }}>
              <CountUp end={combinedRating} duration={2} separator="," />
            </span>
          </div>
          {ratingTier && (
            <span className="text-xs font-semibold px-2 py-0.5 rounded-full mt-1 inline-block"
              style={{ background: `${tierColor}20`, color: tierColor, border: `1px solid ${tierColor}40` }}>
              <Star className="w-2.5 h-2.5 inline mr-1" />
              {ratingTier}
            </span>
          )}
        </div>
        <div className="text-4xl" style={{ filter: `drop-shadow(0 0 8px ${fireColor})` }}>
          {currentStreak > 0 ? "🔥" : "💤"}
        </div>
      </div>

      {/* Streak stats */}
      <div className="grid grid-cols-3 gap-2 pt-3 border-t border-white/5">
        <div className="text-center">
          <div className="flex items-center justify-center gap-1 mb-1">
            <Flame className="w-3 h-3" style={{ color: fireColor }} />
            <span className="text-xs text-slate-500">Current</span>
          </div>
          <p className="text-lg font-display font-bold" style={{ color: fireColor }}>
            {currentStreak}
            <span className="text-xs font-normal text-slate-500 ml-0.5">d</span>
          </p>
        </div>
        <div className="text-center border-x border-white/5">
          <div className="flex items-center justify-center gap-1 mb-1">
            <TrendingUp className="w-3 h-3 text-cyan-400" />
            <span className="text-xs text-slate-500">Best</span>
          </div>
          <p className="text-lg font-display font-bold text-cyan-400">
            {longestStreak}
            <span className="text-xs font-normal text-slate-500 ml-0.5">d</span>
          </p>
        </div>
        <div className="text-center">
          <div className="flex items-center justify-center gap-1 mb-1">
            <Calendar className="w-3 h-3 text-purple-400" />
            <span className="text-xs text-slate-500">Active</span>
          </div>
          <p className="text-lg font-display font-bold text-purple-400">
            {totalActiveDays}
            <span className="text-xs font-normal text-slate-500 ml-0.5">d</span>
          </p>
        </div>
      </div>
    </motion.div>
  );
}