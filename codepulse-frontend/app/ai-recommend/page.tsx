"use client";

import { useState, useEffect, useRef } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { motion, AnimatePresence } from "framer-motion";
import {
  BrainCircuit, Send, RefreshCw, ExternalLink, CheckCircle2, X,
  Sparkles, Tag, Lightbulb, Target, Clock, ChevronRight,
} from "lucide-react";
import { aiApi } from "@/lib/api";
import toast from "react-hot-toast";

const PLATFORM_COLOR: Record<string, string> = {
  CODEFORCES: "#ff6b35", LEETCODE: "#ffd700", ATCODER: "#00f5ff",
  CODECHEF: "#39ff14", OTHER: "#bf5fff",
};
const DIFF_COLOR = (d?: string, r?: number) => {
  if (d === "Easy" || d === "Beginner") return "#39ff14";
  if (d === "Medium") return "#bf5fff";
  if (d === "Hard") return "#ff6b35";
  if (d === "Expert") return "#ff2d78";
  if (r && r < 1200) return "#39ff14";
  if (r && r < 1600) return "#00f5ff";
  if (r && r < 2100) return "#bf5fff";
  return "#888";
};

const PROMPT_EXAMPLES = [
  "I struggle with Dynamic Programming — give me problems that build from easy to hard",
  "Prepare me for Codeforces Div 2 C and D problems at my level",
  "I keep failing greedy problems — what should I practice?",
  "Give me graph traversal problems from LeetCode and AtCoder",
  "I want to practice segment trees and binary indexed trees",
  "Help me improve on math-heavy competitive programming problems",
];

export default function AiRecommendPage() {
  const qc = useQueryClient();
  const [prompt, setPrompt] = useState("");
  const [count, setCount] = useState(6);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  // Load stored session
  const { data: session, isLoading: loadingSession } = useQuery({
    queryKey: ["ai-session"],
    queryFn: () => aiApi.getLatestSession().then((r) => r.data.data),
    retry: false,
  });

  // Generate new
  const generateMutation = useMutation({
    mutationFn: () => aiApi.recommend(prompt.trim(), count),
    onSuccess: (res) => {
      const data = res.data.data;
      qc.setQueryData(["ai-session"], data);
      setPrompt("");
      toast.success("AI recommendations generated!");
    },
    onError: () => toast.error("AI generation failed — check API key or try again"),
  });

  // Mark solved
  const solveMutation = useMutation({
    mutationFn: (itemId: number) => aiApi.markSolved(itemId),
    onSuccess: () => { toast.success("Marked solved 🎉"); qc.invalidateQueries({ queryKey: ["ai-session"] }); },
  });

  // Dismiss
  const dismissMutation = useMutation({
    mutationFn: (itemId: number) => aiApi.dismiss(itemId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["ai-session"] }),
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!prompt.trim()) { toast.error("Enter a prompt first"); return; }
    generateMutation.mutate();
  };

  const activeItems = session?.recommendations?.filter((r: any) => !r.dismissed) ?? [];
  const isGenerating = generateMutation.isPending;

  return (
    <>
      {/* Header */}
      <motion.div initial={{ opacity: 0, y: -10 }} animate={{ opacity: 1, y: 0 }} className="mb-6">
        <h1 className="text-2xl font-display font-bold text-white flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-orange-400/15 flex items-center justify-center">
            <BrainCircuit className="w-5 h-5 text-orange-400" />
          </div>
          AI Coach
        </h1>
        <p className="text-slate-400 text-sm mt-1">
          Describe what you want to practice — Mistral AI will analyse your full performance history and recommend problems across all 4 platforms.
        </p>
      </motion.div>

      {/* Prompt card */}
      <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.05 }}
        className="glass-card rounded-2xl p-5 mb-5 relative overflow-hidden">
        <div className="absolute top-0 left-0 right-0 h-0.5 opacity-50"
          style={{ background: "linear-gradient(90deg,transparent,#ff6b35,#bf5fff,transparent)" }} />

        <div className="flex items-center gap-2 mb-3">
          <Sparkles className="w-4 h-4 text-orange-400" />
          <span className="text-sm font-display font-bold text-white">
            {session ? "Generate New Recommendations" : "Ask the AI Coach"}
          </span>
          {session && (
            <span className="text-xs text-slate-600 font-mono">
              · previous session will be replaced
            </span>
          )}
        </div>

        <form onSubmit={handleSubmit} className="space-y-3">
          <textarea
            ref={textareaRef}
            value={prompt}
            onChange={(e) => setPrompt(e.target.value)}
            onKeyDown={(e) => { if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) handleSubmit(e as any); }}
            rows={3}
            placeholder="e.g. I keep failing on DP problems and need to improve for Codeforces Div 2..."
            className="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-white text-sm
                       placeholder:text-slate-600 focus:outline-none focus:border-orange-400/50
                       focus:bg-white/8 transition-all font-body resize-none"
          />

          {/* Example prompts */}
          <div className="flex flex-wrap gap-2">
            {PROMPT_EXAMPLES.slice(0, 3).map((ex) => (
              <button type="button" key={ex}
                onClick={() => { setPrompt(ex); textareaRef.current?.focus(); }}
                className="text-xs px-2.5 py-1.5 rounded-full border transition-all hover:text-white"
                style={{ border: "1px solid rgba(255,107,53,0.25)", color: "#64748b", background: "rgba(255,107,53,0.06)" }}>
                {ex.length > 45 ? ex.slice(0, 45) + "…" : ex}
              </button>
            ))}
          </div>

          <div className="flex items-center gap-3">
            <div className="flex items-center gap-2">
              <label className="text-xs text-slate-500 font-mono">Problems:</label>
              {[4, 6, 8].map((n) => (
                <button type="button" key={n} onClick={() => setCount(n)}
                  className="w-8 h-7 rounded-lg text-xs font-display font-bold transition-all"
                  style={{
                    background: count === n ? "rgba(255,107,53,0.2)" : "rgba(255,255,255,0.04)",
                    border: `1px solid ${count === n ? "rgba(255,107,53,0.5)" : "rgba(255,255,255,0.08)"}`,
                    color: count === n ? "#ff6b35" : "#64748b",
                  }}>
                  {n}
                </button>
              ))}
            </div>
            <motion.button type="submit" disabled={isGenerating || !prompt.trim()}
              whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.97 }}
              className="ml-auto flex items-center gap-2 px-5 py-2.5 rounded-xl font-display font-bold text-sm transition-all"
              style={{
                background: isGenerating || !prompt.trim() ? "rgba(255,107,53,0.08)" : "linear-gradient(135deg,rgba(255,107,53,0.25),rgba(191,95,255,0.25))",
                border: `1px solid rgba(255,107,53,${prompt.trim() ? "0.5" : "0.2"})`,
                color: prompt.trim() ? "#ff6b35" : "#4a5568",
              }}>
              {isGenerating ? (
                <><RefreshCw className="w-4 h-4 animate-spin" /> Generating…</>
              ) : (
                <><Send className="w-4 h-4" /> Ask AI</>
              )}
            </motion.button>
          </div>
          <p className="text-xs text-slate-600">Ctrl+Enter to submit · Previous session will be replaced with new results</p>
        </form>
      </motion.div>

      {/* Loading */}
      {isGenerating && (
        <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="mb-5">
          <div className="glass-card rounded-2xl p-6 text-center">
            <div className="flex items-center justify-center gap-3 mb-3">
              <BrainCircuit className="w-6 h-6 text-orange-400 animate-pulse" />
              <span className="text-sm font-semibold text-white">Mistral AI is analysing your performance…</span>
            </div>
            <p className="text-xs text-slate-500">Building your personalised context: weakness scores, recent activity, topic gaps, and your prompt.</p>
            <div className="mt-4 flex justify-center gap-1.5">
              {[0,1,2,3,4].map((i) => (
                <motion.div key={i} className="w-2 h-2 rounded-full bg-orange-400"
                  animate={{ scale: [1,1.5,1], opacity: [0.4,1,0.4] }}
                  transition={{ duration: 1.2, delay: i * 0.2, repeat: Infinity }} />
              ))}
            </div>
          </div>
        </motion.div>
      )}

      {/* Session results */}
      {!isGenerating && session && (
        <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.1 }}>

          {/* Coach Insight */}
          {session.coachInsight && (
            <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}
              className="rounded-2xl p-5 mb-5 relative overflow-hidden"
              style={{ background: "rgba(255,107,53,0.07)", border: "1px solid rgba(255,107,53,0.25)" }}>
              <div className="absolute top-0 left-0 right-0 h-0.5 opacity-50"
                style={{ background: "linear-gradient(90deg,transparent,#ff6b35,transparent)" }} />
              <div className="flex items-start gap-3">
                <Lightbulb className="w-5 h-5 text-orange-400 flex-shrink-0 mt-0.5" />
                <div>
                  <p className="text-xs font-mono font-bold text-orange-400 mb-2">AI COACH INSIGHT</p>
                  <p className="text-sm text-slate-300 leading-relaxed">{session.coachInsight}</p>
                </div>
              </div>
            </motion.div>
          )}

          {/* Focus areas */}
          {session.focusAreas?.length > 0 && (
            <div className="flex flex-wrap gap-2 mb-5">
              <span className="text-xs text-slate-500 font-mono self-center">FOCUS AREAS:</span>
              {session.focusAreas.map((area: string) => (
                <span key={area}
                  className="flex items-center gap-1.5 text-xs px-3 py-1.5 rounded-full font-semibold"
                  style={{ background: "rgba(191,95,255,0.12)", color: "#bf5fff", border: "1px solid rgba(191,95,255,0.25)" }}>
                  <ChevronRight className="w-3 h-3" />{area}
                </span>
              ))}
            </div>
          )}

          {/* Session meta */}
          <div className="flex items-center justify-between mb-4">
            <div>
              <h2 className="text-sm font-display font-bold text-white flex items-center gap-2">
                <Target className="w-4 h-4 text-orange-400" />
                Recommended Problems
                <span className="text-slate-600 font-mono font-normal text-xs">
                  {activeItems.length} active
                </span>
              </h2>
              {session.originalPrompt && (
                <p className="text-xs text-slate-600 mt-0.5 italic">
                  "{session.originalPrompt.length > 80 ? session.originalPrompt.slice(0,80)+"…" : session.originalPrompt}"
                </p>
              )}
            </div>
            {session.generatedAt && (
              <span className="text-xs text-slate-600 font-mono flex items-center gap-1">
                <Clock className="w-3 h-3" />
                {new Date(session.generatedAt).toLocaleDateString()}
              </span>
            )}
          </div>

          {/* Recommendation cards */}
          <AnimatePresence mode="popLayout">
            <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
              {activeItems.map((rec: any, i: number) => {
                const pColor = PLATFORM_COLOR[rec.platform] || "#bf5fff";
                const dColor = DIFF_COLOR(rec.difficulty, rec.estimatedRating);
                const platformShort = rec.platform === "CODEFORCES" ? "CF"
                  : rec.platform === "LEETCODE" ? "LC"
                  : rec.platform === "ATCODER" ? "AC"
                  : rec.platform === "CODECHEF" ? "CC" : "AI";

                return (
                  <motion.div key={rec.itemId ?? i} layout
                    initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, scale: 0.9, y: -8 }}
                    transition={{ delay: i * 0.05 }}
                    className={`glass-card rounded-2xl p-5 relative overflow-hidden group transition-all ${rec.solved ? "opacity-50" : ""}`}>

                    {/* Top accent line */}
                    <div className="absolute top-0 left-0 right-0 h-0.5 opacity-60"
                      style={{ background: `linear-gradient(90deg,transparent,${pColor},transparent)` }} />

                    {/* Solved badge */}
                    {rec.solved && (
                      <div className="absolute top-2 right-2 flex items-center gap-1 text-xs px-2 py-0.5 rounded-full"
                        style={{ background: "rgba(57,255,20,0.15)", color: "#39ff14" }}>
                        <CheckCircle2 className="w-3 h-3" /> Solved
                      </div>
                    )}

                    <div className="flex items-start justify-between gap-2 mb-3">
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 mb-1.5">
                          <span className="text-xs font-mono font-bold px-1.5 py-0.5 rounded"
                            style={{ background: `${pColor}20`, color: pColor }}>{platformShort}</span>
                          <span className="text-xs font-mono px-1.5 py-0.5 rounded"
                            style={{ background: `${dColor}18`, color: dColor }}>
                            {rec.difficulty}{rec.estimatedRating ? ` · ${rec.estimatedRating}` : ""}
                          </span>
                        </div>
                        <h4 className="font-semibold text-white text-sm leading-snug line-clamp-2">
                          {rec.title}
                        </h4>
                      </div>
                      {!rec.solved && (
                        <div className="flex gap-1 flex-shrink-0">
                          <button onClick={() => solveMutation.mutate(rec.itemId)}
                            disabled={solveMutation.isPending}
                            title="Mark solved"
                            className="w-7 h-7 rounded-lg flex items-center justify-center hover:bg-green-400/20 transition-colors">
                            <CheckCircle2 className="w-4 h-4 text-green-400" />
                          </button>
                          <button onClick={() => dismissMutation.mutate(rec.itemId)}
                            disabled={dismissMutation.isPending}
                            title="Dismiss"
                            className="w-7 h-7 rounded-lg flex items-center justify-center hover:bg-red-400/20 transition-colors">
                            <X className="w-4 h-4 text-slate-500" />
                          </button>
                        </div>
                      )}
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

                    {/* Personalised reason from AI */}
                    <p className="text-xs text-slate-500 mb-3 leading-relaxed line-clamp-3 italic">
                      "{rec.reason}"
                    </p>

                    {/* Time estimate */}
                    {rec.timeEstimate && (
                      <p className="text-xs text-slate-600 mb-3 flex items-center gap-1">
                        <Clock className="w-3 h-3" /> {rec.timeEstimate}
                      </p>
                    )}

                    {/* Clickable problem link */}
                    {rec.url ? (
                      <a href={rec.url} target="_blank" rel="noopener noreferrer"
                        className="flex items-center gap-1.5 text-xs font-semibold transition-colors hover:underline decoration-dotted"
                        style={{ color: pColor }}>
                        Open on {rec.platform.charAt(0) + rec.platform.slice(1).toLowerCase()}
                        <ExternalLink className="w-3 h-3" />
                      </a>
                    ) : (
                      <span className="text-xs text-slate-600 italic">URL not available</span>
                    )}
                  </motion.div>
                );
              })}
            </div>
          </AnimatePresence>
        </motion.div>
      )}

      {/* Empty state (no session and not generating) */}
      {!isGenerating && !session && !loadingSession && (
        <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }}
          className="flex flex-col items-center justify-center py-24 text-center">
          <div className="w-20 h-20 rounded-full bg-orange-400/10 flex items-center justify-center mb-6 animate-pulse">
            <BrainCircuit className="w-10 h-10 text-orange-400" />
          </div>
          <h3 className="text-xl font-display font-bold text-white mb-2">No Sessions Yet</h3>
          <p className="text-slate-400 max-w-sm mb-4 text-sm">
            Type a prompt above to generate your first AI-powered recommendations. The AI will use your full performance history across all 4 platforms.
          </p>
          <div className="grid sm:grid-cols-2 gap-2 max-w-lg">
            {PROMPT_EXAMPLES.slice(0,4).map((ex) => (
              <button key={ex} onClick={() => { setPrompt(ex); textareaRef.current?.focus(); }}
                className="text-left text-xs px-3 py-2.5 rounded-xl border transition-all hover:text-white"
                style={{ border: "1px solid rgba(255,107,53,0.2)", color: "#64748b", background: "rgba(255,107,53,0.05)" }}>
                {ex}
              </button>
            ))}
          </div>
        </motion.div>
      )}
    </>
  );
}