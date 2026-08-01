"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { motion } from "framer-motion";
import { Eye, EyeOff, Zap, ArrowRight, User, Mail, Lock, UserCircle } from "lucide-react";
import { useMutation } from "@tanstack/react-query";
import { authApi } from "@/lib/api";
import { useAuthStore } from "@/lib/store";
import { ParticleBackground } from "@/components/animations/ParticleBackground";
import toast from "react-hot-toast";

const STEPS = ["identity", "credentials"] as const;

export default function RegisterPage() {
  const router = useRouter();
  const { setAuth } = useAuthStore();
  const [step, setStep] = useState(0);
  const [showPass, setShowPass] = useState(false);
  const [form, setForm] = useState({
    username: "", fullName: "", email: "", password: "",
  });

  const mutation = useMutation({
    mutationFn: (data: typeof form) => authApi.register(data),
    onSuccess: (res) => {
      const { token, userId, username, email, role } = res.data.data;
      setAuth({ id: userId, username, email, role }, token);
      toast.success("Welcome to CodePulse! Let's analyze your code.");
      router.push("/dashboard");
    },
    onError: (err: any) => {
      toast.error(err.response?.data?.message || "Registration failed.");
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (step === 0) { setStep(1); return; }
    mutation.mutate(form);
  };

  const inputClass = "w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-white placeholder:text-slate-600 focus:outline-none focus:border-cyan-400/50 focus:bg-white/8 transition-all font-body";

  return (
    <div className="min-h-screen bg-pulse-950 flex items-center justify-center relative overflow-hidden p-8">
      <ParticleBackground />
      <div
        className="fixed inset-0 pointer-events-none z-0"
        style={{
          backgroundImage: "linear-gradient(to right, rgba(0,245,255,0.03) 1px, transparent 1px), linear-gradient(to bottom, rgba(0,245,255,0.03) 1px, transparent 1px)",
          backgroundSize: "40px 40px",
        }}
      />

      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className="w-full max-w-md relative z-10"
      >
        {/* Logo */}
        <div className="flex items-center justify-center gap-3 mb-8">
          <div className="relative">
            <div className="w-12 h-12 rounded-2xl bg-gradient-to-br from-cyan-400 to-purple-600 flex items-center justify-center">
              <Zap className="w-7 h-7 text-white" />
            </div>
            <div className="absolute inset-0 rounded-2xl bg-gradient-to-br from-cyan-400 to-purple-600 blur-xl opacity-50" />
          </div>
          <span className="font-display text-2xl font-bold gradient-text">CodePulse</span>
        </div>

        {/* Progress steps */}
        <div className="flex items-center gap-3 mb-8">
          {STEPS.map((s, i) => (
            <div key={s} className="flex-1 flex items-center gap-2">
              <div
                className="flex-1 h-0.5 rounded-full transition-all duration-500"
                style={{
                  background: i <= step
                    ? "linear-gradient(90deg, #00f5ff, #bf5fff)"
                    : "rgba(255,255,255,0.1)"
                }}
              />
              <div
                className="w-6 h-6 rounded-full flex items-center justify-center text-xs font-mono font-bold flex-shrink-0 transition-all duration-300"
                style={{
                  background: i <= step ? "rgba(0,245,255,0.2)" : "rgba(255,255,255,0.05)",
                  border: `1px solid ${i <= step ? "rgba(0,245,255,0.5)" : "rgba(255,255,255,0.1)"}`,
                  color: i <= step ? "#00f5ff" : "#64748b",
                }}
              >
                {i + 1}
              </div>
            </div>
          ))}
        </div>

        <div className="glass rounded-2xl p-8 neon-border-cyan border">
          <div className="mb-7">
            <h2 className="text-2xl font-display font-bold text-white mb-2">
              {step === 0 ? "Create Identity" : "Set Credentials"}
            </h2>
            <p className="text-slate-400 text-sm">
              {step === 0 ? "Who are you, coder?" : "Secure your account"}
            </p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-5">
            <motion.div
              key={step}
              initial={{ opacity: 0, x: 20 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: -20 }}
              className="space-y-5"
            >
              {step === 0 ? (
                <>
                  <div>
                    <label className="block text-xs font-semibold text-slate-400 mb-2 tracking-widest uppercase font-mono">Username</label>
                    <div className="relative">
                      <User className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
                      <input
                        type="text"
                        required
                        minLength={3}
                        value={form.username}
                        onChange={(e) => setForm({ ...form, username: e.target.value })}
                        placeholder="Badhon"
                        className={`${inputClass} pl-10`}
                      />
                    </div>
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-slate-400 mb-2 tracking-widest uppercase font-mono">Full Name</label>
                    <div className="relative">
                      <UserCircle className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
                      <input
                        type="text"
                        value={form.fullName}
                        onChange={(e) => setForm({ ...form, fullName: e.target.value })}
                        placeholder="Abdus Salam Islam Badhon"
                        className={`${inputClass} pl-10`}
                      />
                    </div>
                  </div>
                </>
              ) : (
                <>
                  <div>
                    <label className="block text-xs font-semibold text-slate-400 mb-2 tracking-widest uppercase font-mono">Email</label>
                    <div className="relative">
                      <Mail className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
                      <input
                        type="email"
                        required
                        value={form.email}
                        onChange={(e) => setForm({ ...form, email: e.target.value })}
                        placeholder="you@codepulse.io"
                        className={`${inputClass} pl-10`}
                      />
                    </div>
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-slate-400 mb-2 tracking-widest uppercase font-mono">Password</label>
                    <div className="relative">
                      <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
                      <input
                        type={showPass ? "text" : "password"}
                        required
                        minLength={6}
                        value={form.password}
                        onChange={(e) => setForm({ ...form, password: e.target.value })}
                        placeholder="••••••••"
                        className={`${inputClass} pl-10 pr-10`}
                      />
                      <button
                        type="button"
                        onClick={() => setShowPass(!showPass)}
                        className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-300"
                      >
                        {showPass ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                      </button>
                    </div>
                  </div>
                </>
              )}
            </motion.div>

            <div className="flex gap-3 pt-1">
              {step > 0 && (
                <button
                  type="button"
                  onClick={() => setStep(0)}
                  className="flex-1 py-3 rounded-xl font-semibold text-sm text-slate-400 hover:text-white border border-white/10 hover:border-white/20 transition-all"
                >
                  Back
                </button>
              )}
              <motion.button
                type="submit"
                disabled={mutation.isPending}
                whileHover={{ scale: 1.02 }}
                whileTap={{ scale: 0.98 }}
                className="flex-1 relative overflow-hidden rounded-xl py-3.5 font-display font-bold text-sm tracking-wider flex items-center justify-center gap-2"
                style={{
                  background: "linear-gradient(135deg, rgba(0,245,255,0.2), rgba(191,95,255,0.2))",
                  border: "1px solid rgba(0,245,255,0.4)",
                  color: "#00f5ff",
                }}
              >
                {mutation.isPending ? (
                  <div className="w-4 h-4 border-2 border-cyan-400/30 border-t-cyan-400 rounded-full animate-spin" />
                ) : step === 0 ? (
                  <>Next <ArrowRight className="w-4 h-4" /></>
                ) : (
                  <>Launch <Zap className="w-4 h-4" /></>
                )}
              </motion.button>
            </div>
          </form>

          <div className="mt-6 pt-6 border-t border-white/5 text-center">
            <p className="text-sm text-slate-500">
              Already have an account?{" "}
              <Link href="/auth/login" className="text-cyan-400 hover:text-cyan-300 font-semibold">
                Log In
              </Link>
            </p>
          </div>
        </div>
      </motion.div>
    </div>
  );
}
