"use client";

import { useState, useEffect } from "react";
import { motion } from "framer-motion";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Save, Shield, ExternalLink, CheckCircle2, XCircle,
  User, Mail, Image as ImageIcon, Link2,
} from "lucide-react";
import { userApi } from "@/lib/api";
import { useAuthStore } from "@/lib/store";
import toast from "react-hot-toast";

const PLATFORM_META = {
  CODEFORCES: {
    color: "#ff6b35", label: "Codeforces", key: "codeforcesHandle" as const,
    placeholder: "e.g. tourist",
    profileUrl: (h: string) => `https://codeforces.com/profile/${h}`,
    description: "Enables submission sync, rating trend, and CF-based problem recommendations",
  },
  LEETCODE: {
    color: "#ffd700", label: "LeetCode", key: "leetcodeHandle" as const,
    placeholder: "e.g. neetcode",
    profileUrl: (h: string) => `https://leetcode.com/${h}`,
    description: "Pulls Easy/Medium/Hard solves and interview-prep recommendations",
  },
  ATCODER: {
    color: "#00f5ff", label: "AtCoder", key: "atcoderHandle" as const,
    placeholder: "e.g. tourist",
    profileUrl: (h: string) => `https://atcoder.jp/users/${h}`,
    description: "Fetches contest submissions via the kenkoooo.com community API",
  },
  CODECHEF: {
    color: "#39ff14", label: "CodeChef", key: "codechefHandle" as const,
    placeholder: "e.g. admin",
    profileUrl: (h: string) => `https://www.codechef.com/users/${h}`,
    description: "Syncs solved problems and estimates division-based rating",
  },
} as const;

type FormState = {
  fullName: string; avatarUrl: string;
  codeforcesHandle: string; leetcodeHandle: string;
  atcoderHandle: string; codechefHandle: string;
};

const EMPTY_FORM: FormState = {
  fullName: "", avatarUrl: "",
  codeforcesHandle: "", leetcodeHandle: "",
  atcoderHandle: "", codechefHandle: "",
};

export default function ProfilePage() {
  const { user, updateUser } = useAuthStore();
  const qc = useQueryClient();

  const [form, setForm] = useState<FormState>(EMPTY_FORM);

  // ── Always fetch fresh data from server ──────────────────────────────────
  const { data: serverProfile, isLoading } = useQuery({
    queryKey: ["my-profile"],
    queryFn: () => userApi.getMe().then((r) => r.data.data),
    staleTime: 0, // always fresh on this page
  });

  // Pre-fill form from server data (this is the key fix for handles not showing)
  useEffect(() => {
    if (serverProfile) {
      updateUser(serverProfile);
      setForm({
        fullName:          serverProfile.fullName          || "",
        avatarUrl:         serverProfile.avatarUrl         || "",
        codeforcesHandle:  serverProfile.codeforcesHandle  || "",
        leetcodeHandle:    serverProfile.leetcodeHandle    || "",
        atcoderHandle:     serverProfile.atcoderHandle     || "",
        codechefHandle:    serverProfile.codechefHandle    || "",
      });
    }
  }, [serverProfile]);

  const mutation = useMutation({
    mutationFn: (data: FormState) => userApi.updateMe(data),
    onSuccess: (res) => {
      updateUser(res.data.data);
      qc.invalidateQueries({ queryKey: ["my-profile"] });
      qc.invalidateQueries({ queryKey: ["analytics"] });
      toast.success("Profile saved!");
    },
    onError: () => toast.error("Failed to save profile"),
  });

  const inputCls = "w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-white " +
    "placeholder:text-slate-600 focus:outline-none focus:border-cyan-400/50 focus:bg-white/8 " +
    "transition-all font-body text-sm";

  const connectedCount = [
    form.codeforcesHandle, form.leetcodeHandle, form.atcoderHandle, form.codechefHandle,
  ].filter(Boolean).length;

  return (
    <>
      <motion.div initial={{ opacity: 0, y: -10 }} animate={{ opacity: 1, y: 0 }} className="mb-7">
        <h1 className="text-2xl font-display font-bold text-white flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-yellow-400/15 flex items-center justify-center">
            <User className="w-5 h-5 text-yellow-400" />
          </div>
          Profile
        </h1>
        <p className="text-slate-400 text-sm mt-1">
          Manage your identity and platform handles.
          <span className="ml-2 font-mono text-xs"
            style={{ color: connectedCount > 0 ? "#39ff14" : "#64748b" }}>
            {connectedCount}/4 platforms connected
          </span>
        </p>
      </motion.div>

      <div className="grid lg:grid-cols-3 gap-6">
        {/* ── Left: Avatar + summary ─────────────────────────────────────── */}
        <motion.div initial={{ opacity: 0, x: -16 }} animate={{ opacity: 1, x: 0 }}
          transition={{ delay: 0.05 }}
          className="glass-card rounded-2xl p-6 flex flex-col gap-5">

          {/* Avatar */}
          <div className="flex flex-col items-center text-center">
            <div className="relative mb-3">
              {form.avatarUrl ? (
                <img src={form.avatarUrl} alt="avatar"
                  className="w-20 h-20 rounded-full object-cover ring-2 ring-cyan-400/30" />
              ) : (
                <div className="w-20 h-20 rounded-full bg-gradient-to-br from-cyan-400 to-purple-600
                                flex items-center justify-center text-3xl font-display font-black text-white">
                  {(serverProfile?.username || user?.username || "?")[0].toUpperCase()}
                </div>
              )}
              <div className="absolute inset-0 rounded-full bg-gradient-to-br from-cyan-400/20 to-purple-600/20 blur-xl -z-10" />
              {/* Online dot */}
              <div className="absolute -bottom-0.5 -right-0.5 w-5 h-5 rounded-full bg-green-400
                              border-2 border-pulse-950 flex items-center justify-center">
                <div className="w-2 h-2 rounded-full bg-green-900 animate-pulse" />
              </div>
            </div>
            <h3 className="text-base font-display font-bold text-white">
              {serverProfile?.fullName || serverProfile?.username || user?.username}
            </h3>
            <p className="text-slate-500 text-xs mt-0.5">{serverProfile?.email || user?.email}</p>
            <span className="mt-2 px-3 py-0.5 rounded-full text-xs font-mono font-bold"
              style={{ background: "rgba(0,245,255,0.1)", color: "#00f5ff", border: "1px solid rgba(0,245,255,0.2)" }}>
              {serverProfile?.role || user?.role}
            </span>
          </div>

          {/* Platform connection status */}
          <div>
            <p className="text-xs font-mono font-bold text-slate-500 mb-3">CONNECTED PLATFORMS</p>
            <div className="space-y-2">
              {(Object.entries(PLATFORM_META) as Array<[keyof typeof PLATFORM_META, typeof PLATFORM_META[keyof typeof PLATFORM_META]]>).map(([plat, meta]) => {
                const handle = form[meta.key] || serverProfile?.[meta.key];
                const connected = !!handle;
                return (
                  <div key={plat} className="flex items-center gap-3 px-3 py-2.5 rounded-xl"
                    style={{
                      background: connected ? `${meta.color}0a` : "rgba(255,255,255,0.02)",
                      border: `1px solid ${connected ? meta.color + "30" : "rgba(255,255,255,0.05)"}`,
                    }}>
                    {/* Status icon */}
                    {connected
                      ? <CheckCircle2 className="w-4 h-4 flex-shrink-0" style={{ color: meta.color }} />
                      : <XCircle className="w-4 h-4 flex-shrink-0 text-slate-700" />}

                    <div className="flex-1 min-w-0">
                      <p className="text-xs font-semibold" style={{ color: connected ? meta.color : "#4a5568" }}>
                        {meta.label}
                      </p>
                      {connected ? (
                        <a href={meta.profileUrl(handle!)} target="_blank" rel="noopener noreferrer"
                          className="text-xs font-mono truncate flex items-center gap-1 hover:underline"
                          style={{ color: meta.color }}>
                          {handle}
                          <ExternalLink className="w-2.5 h-2.5 flex-shrink-0" />
                        </a>
                      ) : (
                        <p className="text-xs text-slate-700">Not set</p>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Badges */}
          <div className="flex flex-wrap gap-2">
            <div className="flex items-center gap-1.5 text-xs px-2.5 py-1.5 rounded-lg"
              style={{ background: "rgba(57,255,20,0.1)", color: "#39ff14", border: "1px solid rgba(57,255,20,0.2)" }}>
              <Shield className="w-3 h-3" /> Verified
            </div>
            {connectedCount >= 2 && (
              <div className="flex items-center gap-1.5 text-xs px-2.5 py-1.5 rounded-lg"
                style={{ background: "rgba(0,245,255,0.1)", color: "#00f5ff", border: "1px solid rgba(0,245,255,0.2)" }}>
                <Link2 className="w-3 h-3" /> Multi-Platform
              </div>
            )}
          </div>
        </motion.div>

        {/* ── Right: Edit form ────────────────────────────────────────────── */}
        <motion.div initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.1 }}
          className="lg:col-span-2 space-y-5">

          {/* Basic info */}
          <div className="glass-card rounded-2xl p-6">
            <h3 className="text-sm font-display font-bold text-white mb-5 flex items-center gap-2">
              <User className="w-4 h-4 text-cyan-400" /> Basic Info
            </h3>
            <div className="grid sm:grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-mono font-semibold text-slate-500 mb-2 tracking-widest uppercase">
                  Full Name
                </label>
                <div className="relative">
                  <User className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-600" />
                  <input type="text" value={form.fullName}
                    onChange={(e) => setForm(f => ({ ...f, fullName: e.target.value }))}
                    placeholder="Abdus Salam Islam Badhon" className={`${inputCls} pl-10`} />
                </div>
              </div>
              <div>
                <label className="block text-xs font-mono font-semibold text-slate-500 mb-2 tracking-widest uppercase">
                  Email
                </label>
                <div className="relative">
                  <Mail className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-600" />
                  <input type="email" value={serverProfile?.email || user?.email || ""} disabled
                    className={`${inputCls} pl-10 opacity-50 cursor-not-allowed`} />
                </div>
              </div>
              <div className="sm:col-span-2">
                <label className="block text-xs font-mono font-semibold text-slate-500 mb-2 tracking-widest uppercase">
                  Avatar URL
                </label>
                <div className="relative">
                  <ImageIcon className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-600" />
                  <input type="url" value={form.avatarUrl}
                    onChange={(e) => setForm(f => ({ ...f, avatarUrl: e.target.value }))}
                    placeholder="https://avatars.githubusercontent.com/u/..." className={`${inputCls} pl-10`} />
                </div>
              </div>
            </div>
          </div>

          {/* Platform handles — each platform is a card */}
          <div className="glass-card rounded-2xl p-6">
  <h3 className="text-sm font-display font-bold text-white mb-1 flex items-center gap-2">
    <Link2 className="w-4 h-4 text-orange-400" /> Platform Handles
  </h3>
  <p className="text-xs text-slate-500 mb-5">
    Set your handles to enable submission sync and combined analytics.
    Handles are saved as-is — enter exactly as they appear on the platform.
  </p>
  <div className="grid sm:grid-cols-2 gap-4">
    {(Object.entries(PLATFORM_META) as Array<[keyof typeof PLATFORM_META, typeof PLATFORM_META[keyof typeof PLATFORM_META]]>).map(([plat, meta]) => {
      const currentVal = form[meta.key];
      const connected = !!currentVal;
      return (
        <div key={plat}>
          <label className="block text-xs font-mono font-semibold mb-2 tracking-widest uppercase"
            style={{ color: connected ? meta.color : "#64748b" }}>
            {meta.label}
            {connected && <span className="ml-2 normal-case font-normal text-slate-600">connected</span>}
          </label>
          <div className="relative">
            {/* Added left-3.5 and w-6 to ensure perfect centering and prevent letter clipping */}
            <div className="absolute left-3.5 top-1/2 -translate-y-1/2 w-6 h-6 rounded flex items-center justify-center text-xs font-display font-black z-10 select-none pointer-events-none"
              style={{ background: `${meta.color}20`, color: meta.color }}>
              {meta.label.slice(0,1)}
            </div>
            
            {/* Changed from 'pl-11' to 'pl-14' to prevent typing overlap with the badge */}
            <input 
              type="text" 
              value={currentVal}
              onChange={(e) => setForm(f => ({ ...f, [meta.key]: e.target.value }))}
              placeholder={meta.placeholder}
              className={inputCls + " pl-14"}
              style={{ borderColor: connected ? `${meta.color}50` : undefined }}
            />
          </div>
          <p className="text-xs text-slate-600 mt-1">{meta.description}</p>
        </div>
      );
    })}
  </div>
</div>

          {/* Save button */}
          <motion.button
            onClick={() => mutation.mutate(form)}
            disabled={mutation.isPending || isLoading}
            whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.97 }}
            className="flex items-center gap-2 px-6 py-3 rounded-xl font-display font-bold text-sm transition-all"
            style={{
              background: "linear-gradient(135deg,rgba(0,245,255,0.15),rgba(191,95,255,0.15))",
              border: "1px solid rgba(0,245,255,0.4)",
              color: "#00f5ff",
            }}>
            {mutation.isPending
              ? <><div className="w-4 h-4 border-2 border-cyan-400/30 border-t-cyan-400 rounded-full animate-spin" /> Saving…</>
              : <><Save className="w-4 h-4" /> Save Changes</>
            }
          </motion.button>
        </motion.div>
      </div>
    </>
  );
}