"use client";

import { motion, AnimatePresence } from "framer-motion";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { recommendationsApi } from "@/lib/api";
import { Lightbulb, RefreshCw, ExternalLink, CheckCircle2, X, Tag, Zap } from "lucide-react";
import toast from "react-hot-toast";
import { useState } from "react";

const PLATFORM_COLOR: Record<string, string> = {
  CODEFORCES: "#ff6b35", LEETCODE: "#ffd700", ATCODER: "#00f5ff", CODECHEF: "#39ff14",
};
const DIFF_COLOR = (r?: number, label?: string) => {
  if (label) return label === "Easy" ? "#39ff14" : label === "Medium" ? "#bf5fff" : "#ff2d78";
  if (!r) return "#888";
  if (r < 1200) return "#39ff14";
  if (r < 1600) return "#00f5ff";
  if (r < 2100) return "#bf5fff";
  if (r < 2400) return "#ff6b35";
  return "#ff2d78";
};

export default function RecommendationsPage() {
  const qc = useQueryClient();
  const [filter, setFilter] = useState<"all"|"CODEFORCES"|"LEETCODE"|"ATCODER"|"CODECHEF">("all");

  const { data, isLoading } = useQuery({
    queryKey: ["recommendations"],
    queryFn: () => recommendationsApi.getRecommendations().then((r) => r.data.data),
  });

  const genMutation = useMutation({
    mutationFn: () => recommendationsApi.generate(),
    onSuccess: () => {
      toast.success("New recommendations generated!");
      qc.invalidateQueries({ queryKey: ["recommendations"] });
    },
    onError: () => toast.error("Failed — sync your data first"),
  });

  const solveMutation = useMutation({
    mutationFn: (id: number) => recommendationsApi.markSolved(id),
    onSuccess: () => { toast.success("Marked as solved 🎉"); qc.invalidateQueries({ queryKey: ["recommendations"] }); },
  });

  const dismissMutation = useMutation({
    mutationFn: (id: number) => recommendationsApi.dismiss(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["recommendations"] }),
  });

  const recs = (data || []).filter((r: any) => filter === "all" || r.platform === filter);

  return (
    <>
      {/* Header */}
      <motion.div initial={{ opacity: 0, y: -10 }} animate={{ opacity: 1, y: 0 }}
        className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-7">
        <div>
          <h1 className="text-2xl font-display font-bold text-white flex items-center gap-3">
            <div className="w-8 h-8 rounded-lg bg-green-400/15 flex items-center justify-center">
              <Lightbulb className="w-5 h-5 text-green-400" />
            </div>
            Curated Recommendations
          </h1>
          <p className="text-slate-400 text-sm mt-1">
            Algorithmically selected problems from your weak topics across all platforms.
            <span className="ml-2 font-mono text-green-400 text-xs">{recs.length} problems</span>
          </p>
        </div>
        <div className="flex items-center gap-2">
          {/* Platform filter */}
          <div className="flex gap-1 p-1 rounded-xl bg-white/5 border border-white/8">
            {(["all","CODEFORCES","LEETCODE","ATCODER","CODECHEF"] as const).map((f) => {
              const c = f === "all" ? "#aaa" : PLATFORM_COLOR[f];
              return (
                <button key={f} onClick={() => setFilter(f)}
                  className="px-2.5 py-1.5 rounded-lg text-xs font-semibold transition-all"
                  style={{
                    background: filter === f ? `${c}20` : "transparent",
                    color: filter === f ? c : "#64748b",
                    border: filter === f ? `1px solid ${c}40` : "1px solid transparent",
                  }}>
                  {f === "all" ? "All" : f === "CODEFORCES" ? "CF" : f === "LEETCODE" ? "LC" : f === "ATCODER" ? "AC" : "CC"}
                </button>
              );
            })}
          </div>
          <motion.button whileHover={{ scale: 1.03 }} whileTap={{ scale: 0.97 }}
            onClick={() => genMutation.mutate()} disabled={genMutation.isPending}
            className="flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-semibold"
            style={{ background: "rgba(57,255,20,0.1)", border: "1px solid rgba(57,255,20,0.3)", color: "#39ff14" }}>
            {genMutation.isPending ? <RefreshCw className="w-4 h-4 animate-spin" /> : <Zap className="w-4 h-4" />}
            {genMutation.isPending ? "Generating…" : "Generate New"}
          </motion.button>
        </div>
      </motion.div>

      {isLoading ? (
        <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {[...Array(6)].map((_,i) => <div key={i} className="glass-card rounded-2xl h-52 animate-pulse" />)}
        </div>
      ) : recs.length === 0 ? (
        <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }}
          className="flex flex-col items-center justify-center py-28 text-center">
          <div className="w-20 h-20 rounded-full bg-green-400/10 flex items-center justify-center mb-6">
            <Zap className="w-10 h-10 text-green-400" />
          </div>
          <h3 className="text-xl font-display font-bold text-white mb-2">No Recommendations Yet</h3>
          <p className="text-slate-400 max-w-sm mb-6 text-sm">
            First sync your platforms, then generate recommendations based on your performance.
          </p>
          <button onClick={() => genMutation.mutate()} disabled={genMutation.isPending}
            className="flex items-center gap-2 px-6 py-3 rounded-xl font-semibold text-sm"
            style={{ background: "rgba(57,255,20,0.1)", border: "1px solid rgba(57,255,20,0.3)", color: "#39ff14" }}>
            <Zap className="w-4 h-4" /> Generate Now
          </button>
        </motion.div>
      ) : (
        <AnimatePresence mode="popLayout">
          <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {recs.map((rec: any, i: number) => {
              const pColor = PLATFORM_COLOR[rec.platform] || "#888";
              const dColor = DIFF_COLOR(rec.difficultyRating, rec.difficultyLabel);
              const score = Math.round(rec.score * 100);
              return (
                <motion.div key={rec.id} layout
                  initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, scale: 0.9 }} transition={{ delay: i * 0.04 }}
                  className="glass-card rounded-2xl p-5 relative overflow-hidden group">

                  {/* Top accent */}
                  <div className="absolute top-0 left-0 right-0 h-0.5 opacity-60"
                    style={{ background: `linear-gradient(90deg, transparent, ${pColor}, transparent)` }} />

                  <div className="flex items-start justify-between gap-2 mb-3">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-1.5">
                        <span className="text-xs font-mono font-bold px-1.5 py-0.5 rounded"
                          style={{ background: `${pColor}20`, color: pColor }}>
                          {rec.platform === "CODEFORCES" ? "CF" : rec.platform === "LEETCODE" ? "LC"
                           : rec.platform === "ATCODER" ? "AC" : "CC"}
                        </span>
                        <span className="text-xs font-mono px-1.5 py-0.5 rounded"
                          style={{ background: `${dColor}18`, color: dColor }}>
                          {rec.difficultyRating || rec.difficultyLabel || "?"}
                        </span>
                      </div>
                      <h4 className="font-semibold text-white text-sm leading-snug line-clamp-2">
                        {rec.problemTitle}
                      </h4>
                    </div>
                    <div className="flex gap-1 flex-shrink-0">
                      <button onClick={() => solveMutation.mutate(rec.id)}
                        disabled={solveMutation.isPending}
                        title="Mark solved"
                        className="w-7 h-7 rounded-lg flex items-center justify-center hover:bg-green-400/20 transition-colors">
                        <CheckCircle2 className="w-4 h-4 text-green-400" />
                      </button>
                      <button onClick={() => dismissMutation.mutate(rec.id)}
                        disabled={dismissMutation.isPending}
                        title="Dismiss"
                        className="w-7 h-7 rounded-lg flex items-center justify-center hover:bg-red-400/20 transition-colors">
                        <X className="w-4 h-4 text-slate-500" />
                      </button>
                    </div>
                  </div>

                  {/* Topics */}
                  {rec.topics?.length > 0 && (
                    <div className="flex flex-wrap gap-1.5 mb-3">
                      {rec.topics.slice(0,3).map((t: string) => (
                        <span key={t} className="flex items-center gap-1 text-xs px-2 py-0.5 rounded-full"
                          style={{ background: "rgba(191,95,255,0.1)", color: "#bf5fff" }}>
                          <Tag className="w-2.5 h-2.5" />{t}
                        </span>
                      ))}
                    </div>
                  )}

                  {/* Reason */}
                  <p className="text-xs text-slate-500 mb-3 line-clamp-2">{rec.reason}</p>

                  {/* Score bar */}
                  <div className="mb-3">
                    <div className="flex justify-between text-xs mb-1">
                      <span className="text-slate-600">Priority</span>
                      <span className="font-mono" style={{ color: dColor }}>{score}%</span>
                    </div>
                    <div className="h-1 bg-white/5 rounded-full overflow-hidden">
                      <motion.div className="h-full rounded-full"
                        style={{ background: `linear-gradient(90deg, ${dColor}, #bf5fff)` }}
                        initial={{ width: 0 }} animate={{ width: `${score}%` }}
                        transition={{ duration: 0.8, ease: "easeOut" }} />
                    </div>
                  </div>

                  {/* Clickable link */}
                  <a href={rec.problemUrl} target="_blank" rel="noopener noreferrer"
                    className="flex items-center gap-1.5 text-xs font-semibold transition-colors hover:underline"
                    style={{ color: pColor }}>
                    Solve on {rec.platform.charAt(0) + rec.platform.slice(1).toLowerCase()}
                    <ExternalLink className="w-3 h-3" />
                  </a>
                </motion.div>
              );
            })}
          </div>
        </AnimatePresence>
      )}
    </>
  );
}