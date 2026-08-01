"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { motion } from "framer-motion";
import { Eye, EyeOff, Zap, ArrowRight, Code2 } from "lucide-react";
import { useMutation } from "@tanstack/react-query";
import { authApi } from "@/lib/api";
import { useAuthStore } from "@/lib/store";
import { ParticleBackground } from "@/components/animations/ParticleBackground";
import toast from "react-hot-toast";
import { TypeAnimation } from "react-type-animation";

export default function LoginPage() {
  const router = useRouter();
  const { setAuth } = useAuthStore();
  const [showPass, setShowPass] = useState(false);
  const [form, setForm] = useState({ email: "", password: "" });

  const mutation = useMutation({
    mutationFn: (data: typeof form) => authApi.login(data),
    onSuccess: (res) => {
      const { token, userId, username, email, role } = res.data.data;
      setAuth({ id: userId, username, email, role }, token);
      toast.success("Access granted. Welcome back.");
      router.push("/dashboard");
    },
    onError: () => toast.error("Invalid credentials. Try again."),
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    mutation.mutate(form);
  };

  return (
    <div className="min-h-screen bg-pulse-950 flex relative overflow-hidden">
      <ParticleBackground />

      {/* Grid bg */}
      <div
        className="fixed inset-0 pointer-events-none z-0"
        style={{
          backgroundImage:
            "linear-gradient(to right, rgba(0,245,255,0.03) 1px, transparent 1px), linear-gradient(to bottom, rgba(0,245,255,0.03) 1px, transparent 1px)",
          backgroundSize: "40px 40px",
        }}
      />

      {/* Left panel — branding */}
      <div className="hidden lg:flex lg:w-1/2 flex-col justify-between p-16 relative z-10">
        <div>
          <div className="flex items-center gap-3 mb-16">
            <div className="relative">
              <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-cyan-400 to-purple-600 flex items-center justify-center">
                <Zap className="w-6 h-6 text-white" />
              </div>
              <div className="absolute inset-0 rounded-xl bg-gradient-to-br from-cyan-400 to-purple-600 blur-lg opacity-60" />
            </div>
            <span className="font-display text-xl font-bold gradient-text">CodePulse</span>
          </div>

          <motion.div
            initial={{ opacity: 0, y: 30 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.2 }}
          >
            <h1 className="text-5xl font-display font-bold text-white leading-tight mb-6">
              Decode Your{" "}
              <span className="neon-text-cyan">Coding</span>
              <br />
              Performance
            </h1>
            <p className="text-slate-400 font-body text-lg leading-relaxed max-w-md">
              Transform raw submission data into intelligent insights. Know exactly where you're weak, what to practice, and how to level up faster.
            </p>
          </motion.div>
        </div>

        
      </div>

      {/* Right panel — form */}
      <div className="w-full lg:w-1/2 flex items-center justify-center p-8 relative z-10">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6 }}
          className="w-full max-w-md"
        >
          {/* Mobile logo */}
          <div className="flex items-center gap-2 mb-8 lg:hidden">
            <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-cyan-400 to-purple-600 flex items-center justify-center">
              <Zap className="w-4 h-4 text-white" />
            </div>
            <span className="font-display text-lg font-bold gradient-text">CodePulse</span>
          </div>

          <div className="glass rounded-2xl p-8 neon-border-cyan border">
            <div className="mb-8">
              <h2 className="text-2xl font-display font-bold text-white mb-2">
                System Access
              </h2>
              <p className="text-slate-400 text-sm font-body">
                Authenticate to continue
              </p>
            </div>

            <form onSubmit={handleSubmit} className="space-y-5">
              <div>
                <label className="block text-xs font-semibold text-slate-400 mb-2 tracking-widest uppercase font-mono">
                  Email
                </label>
                <input
                  type="email"
                  required
                  value={form.email}
                  onChange={(e) => setForm({ ...form, email: e.target.value })}
                  placeholder="agent@codepulse.io"
                  className="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-white placeholder:text-slate-600 focus:outline-none focus:border-cyan-400/50 focus:bg-white/8 transition-all font-body"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-400 mb-2 tracking-widest uppercase font-mono">
                  Password
                </label>
                <div className="relative">
                  <input
                    type={showPass ? "text" : "password"}
                    required
                    value={form.password}
                    onChange={(e) => setForm({ ...form, password: e.target.value })}
                    placeholder="••••••••"
                    className="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 pr-11 text-white placeholder:text-slate-600 focus:outline-none focus:border-cyan-400/50 focus:bg-white/8 transition-all font-body"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPass(!showPass)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-300 transition-colors"
                  >
                    {showPass ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
              </div>

              <motion.button
                type="submit"
                disabled={mutation.isPending}
                whileHover={{ scale: 1.02 }}
                whileTap={{ scale: 0.98 }}
                className="w-full relative overflow-hidden rounded-xl py-3.5 font-display font-bold text-sm tracking-wider flex items-center justify-center gap-2 transition-all"
                style={{
                  background: mutation.isPending
                    ? "rgba(0,245,255,0.1)"
                    : "linear-gradient(135deg, rgba(0,245,255,0.2), rgba(191,95,255,0.2))",
                  border: "1px solid rgba(0,245,255,0.4)",
                  color: "#00f5ff",
                }}
              >
                {mutation.isPending ? (
                  <>
                    <div className="w-4 h-4 border-2 border-cyan-400/30 border-t-cyan-400 rounded-full animate-spin" />
                    Authenticating...
                  </>
                ) : (
                  <>
                    Initialize Session
                    <ArrowRight className="w-4 h-4" />
                  </>
                )}

                {/* Shimmer */}
                <div className="absolute inset-0 -translate-x-full hover:translate-x-full transition-transform duration-700 bg-gradient-to-r from-transparent via-white/5 to-transparent" />
              </motion.button>
            </form>

            <div className="mt-6 pt-6 border-t border-white/5 text-center">
              <p className="text-sm text-slate-500">
                New operative?{" "}
                <Link href="/auth/register" className="text-cyan-400 hover:text-cyan-300 font-semibold transition-colors">
                  Create Account
                </Link>
              </p>
            </div>
          </div>

          
        </motion.div>
      </div>
    </div>
  );
}
