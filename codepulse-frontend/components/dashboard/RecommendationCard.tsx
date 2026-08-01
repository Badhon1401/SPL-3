"use client";

import { motion } from "framer-motion";
import { ExternalLink, CheckCircle2, X, Zap, Tag } from "lucide-react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { recommendationsApi } from "@/lib/api";
import toast from "react-hot-toast";

interface Rec {
  id: number;
  problemTitle: string;
  problemUrl: string;
  platform: string;
  difficultyRating?: number;
  difficultyLabel?: string;
  topics: string[];
  reason: string;
  score: number;
}

const DIFF_COLOR = (r?: number) => {
  if (!r) return "#888";
  if (r < 1200) return "#39ff14";
  if (r < 1600) return "#00f5ff";
  if (r < 2100) return "#bf5fff";
  if (r < 2400) return "#ff6b35";
  return "#ff2d78";
};

export function RecommendationCard({ rec, compact = false }: { rec: Rec; compact?: boolean }) {
  const qc = useQueryClient();

  const solveMutation = useMutation({
    mutationFn: () => recommendationsApi.markSolved(rec.id),
    onSuccess: () => {
      toast.success("Nice! Marked as solved 🎉");
      qc.invalidateQueries({ queryKey: ["recommendations"] });
    },
  });

  const dismissMutation = useMutation({
    mutationFn: () => recommendationsApi.dismiss(rec.id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["recommendations"] });
    },
  });

  const color = DIFF_COLOR(rec.difficultyRating);
  const strengthPct = Math.round(rec.score * 100);

  return (
    <motion.div
      layout
      initial={{ opacity: 0, scale: 0.95 }}
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0, scale: 0.9, y: -10 }}
      className="glass-card rounded-2xl p-5 group relative overflow-hidden"
    >
      {/* Accent top line */}
      <div
        className="absolute top-0 left-0 right-0 h-0.5 opacity-60"
        style={{ background: `linear-gradient(90deg, transparent, ${color}, transparent)` }}
      />

      <div className="flex items-start justify-between gap-3 mb-3">
        <div className="flex-1 min-w-0">
          <h4 className="font-semibold text-white text-sm leading-tight mb-1 truncate">
            {rec.problemTitle}
          </h4>
          <div className="flex items-center gap-2">
            <span
              className="text-xs font-mono font-bold px-1.5 py-0.5 rounded"
              style={{ background: `${color}20`, color }}
            >
              {rec.difficultyRating || rec.difficultyLabel || "?"}
            </span>
            <span className="text-xs text-slate-500">{rec.platform}</span>
          </div>
        </div>

        {!compact && (
          <div className="flex gap-1 flex-shrink-0">
            <button
              onClick={() => solveMutation.mutate()}
              disabled={solveMutation.isPending}
              className="w-7 h-7 rounded-lg flex items-center justify-center hover:bg-green-400/20 transition-colors"
              title="Mark solved"
            >
              <CheckCircle2 className="w-4 h-4 text-green-400" />
            </button>
            <button
              onClick={() => dismissMutation.mutate()}
              disabled={dismissMutation.isPending}
              className="w-7 h-7 rounded-lg flex items-center justify-center hover:bg-red-400/20 transition-colors"
              title="Dismiss"
            >
              <X className="w-4 h-4 text-slate-500" />
            </button>
          </div>
        )}
      </div>

      {/* Topics */}
      {rec.topics.length > 0 && (
        <div className="flex flex-wrap gap-1.5 mb-3">
          {rec.topics.slice(0, 3).map((t) => (
            <span
              key={t}
              className="flex items-center gap-1 text-xs px-2 py-0.5 rounded-full"
              style={{ background: "rgba(191,95,255,0.12)", color: "#bf5fff" }}
            >
              <Tag className="w-2.5 h-2.5" />
              {t}
            </span>
          ))}
        </div>
      )}

      {/* Reason */}
      {!compact && (
        <p className="text-xs text-slate-500 mb-3 flex items-center gap-1.5">
          <Zap className="w-3 h-3 text-yellow-400 flex-shrink-0" />
          {rec.reason}
        </p>
      )}

      {/* Score bar */}
      <div className="mb-3">
        <div className="flex justify-between items-center mb-1">
          <span className="text-xs text-slate-500">Priority score</span>
          <span className="text-xs font-mono" style={{ color }}>{strengthPct}%</span>
        </div>
        <div className="h-1 bg-white/5 rounded-full overflow-hidden">
          <motion.div
            className="h-full rounded-full"
            style={{ background: `linear-gradient(90deg, ${color}, #bf5fff)` }}
            initial={{ width: 0 }}
            animate={{ width: `${strengthPct}%` }}
            transition={{ duration: 1, ease: "easeOut" }}
          />
        </div>
      </div>

      <a
        href={rec.problemUrl}
        target="_blank"
        rel="noopener noreferrer"
        className="flex items-center gap-1.5 text-xs font-semibold text-cyan-400 hover:text-cyan-300 transition-colors"
      >
        Solve Problem
        <ExternalLink className="w-3 h-3" />
      </a>
    </motion.div>
  );
}
