"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { motion, AnimatePresence } from "framer-motion";
import {
  LayoutDashboard, BarChart3, Lightbulb, User, LogOut,
  Zap, Menu, X, Activity, ChevronRight, BrainCircuit,
} from "lucide-react";
import { useState } from "react";
import { useAuthStore } from "@/lib/store";
import toast from "react-hot-toast";

const NAV = [
  { href: "/dashboard",       label: "Dashboard",   icon: LayoutDashboard, color: "#00f5ff" },
  { href: "/analytics",       label: "Analytics",   icon: BarChart3,        color: "#bf5fff" },
  { href: "/recommendations", label: "Curated",     icon: Lightbulb,        color: "#39ff14" },
  { href: "/ai-recommend",    label: "AI Coach",    icon: BrainCircuit,     color: "#ff6b35" },
  { href: "/profile",         label: "Profile",     icon: User,             color: "#ffd700" },
];

const PLATFORM_COLORS: Record<string, string> = {
  CODEFORCES: "#ff6b35", LEETCODE: "#ffd700", ATCODER: "#00f5ff", CODECHEF: "#39ff14",
};
const PLATFORM_LABELS: Record<string, string> = {
  CODEFORCES: "CF", LEETCODE: "LC", ATCODER: "AC", CODECHEF: "CC",
};

export function Sidebar() {
  const pathname = usePathname();
  const router = useRouter();
  const { user, logout } = useAuthStore();
  const [collapsed, setCollapsed] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);

  const handleLogout = () => {
    logout();
    toast.success("Logged out");
    router.push("/auth/login");
  };

  const connectedPlatforms = Object.entries(user?.platformConnections || {}).filter(([, v]) => v);

  const renderContent = () => (
    <div className="flex flex-col h-full w-full">
      {/* Logo */}
      <div className="p-5 border-b border-white/5">
        <Link href="/dashboard" className="flex items-center gap-3">
          <div className="relative flex-shrink-0">
            <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-cyan-400 to-purple-600 flex items-center justify-center">
              <Zap className="w-5 h-5 text-white" />
            </div>
            <div className="absolute inset-0 rounded-xl bg-gradient-to-br from-cyan-400 to-purple-600 blur-lg opacity-50" />
          </div>
          <AnimatePresence>
            {!collapsed && (
              <motion.div initial={{ opacity: 0, width: 0 }} animate={{ opacity: 1, width: "auto" }}
                exit={{ opacity: 0, width: 0 }} className="overflow-hidden">
                <span className="font-display text-lg font-bold gradient-text whitespace-nowrap">CodePulse</span>
              </motion.div>
            )}
          </AnimatePresence>
        </Link>
      </div>

      {/* Nav */}
      <nav className="flex-1 p-3 space-y-0.5">
        {NAV.map((item) => {
          const active = pathname.startsWith(item.href);
          return (
            <Link key={item.href} href={item.href} onClick={() => setMobileOpen(false)}>
              <motion.div whileHover={{ x: 3 }} whileTap={{ scale: 0.97 }}
                className={`relative flex items-center gap-3 px-3 py-2.5 rounded-xl transition-all duration-200 ${
                  active ? "text-white" : "text-slate-400 hover:text-white hover:bg-white/5"
                }`}>
                {active && (
                  <motion.div layoutId="active-nav" className="absolute inset-0 rounded-xl"
                    style={{ background: `${item.color}15`, border: `1px solid ${item.color}30` }}
                    transition={{ type: "spring", bounce: 0.2, duration: 0.4 }} />
                )}
                <item.icon className="w-4.5 h-4.5 flex-shrink-0 z-10" style={{ color: active ? item.color : undefined }} />
                <AnimatePresence>
                  {!collapsed && (
                    <motion.span initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
                      className="text-sm font-semibold font-body z-10 flex-1">{item.label}</motion.span>
                  )}
                </AnimatePresence>
                {active && (
                  <div className="absolute right-2 top-1/2 -translate-y-1/2 w-1.5 h-1.5 rounded-full z-10"
                    style={{ background: item.color, boxShadow: `0 0 6px ${item.color}` }} />
                )}
              </motion.div>
            </Link>
          );
        })}
      </nav>

      {/* Platform connection badges */}
      {!collapsed && connectedPlatforms.length > 0 && (
        <div className="px-4 pb-3">
          <p className="text-xs text-slate-600 mb-2 font-mono">CONNECTED</p>
          <div className="flex flex-wrap gap-1.5">
            {connectedPlatforms.map(([platform]) => (
              <span key={platform} className="text-xs px-2 py-0.5 rounded font-mono font-bold"
                style={{ background: `${PLATFORM_COLORS[platform]}18`, color: PLATFORM_COLORS[platform], border: `1px solid ${PLATFORM_COLORS[platform]}30` }}>
                {PLATFORM_LABELS[platform]}
              </span>
            ))}
          </div>
        </div>
      )}

      {/* User footer */}
      <div className="p-3 border-t border-white/5 space-y-1">
        <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg">
          <Activity className="w-3.5 h-3.5 text-green-400 animate-pulse flex-shrink-0" />
          {!collapsed && <span className="text-xs text-slate-500">System Online</span>}
        </div>

        {!collapsed && user && (
          <div className="flex items-center gap-2.5 px-3 py-2.5 rounded-xl bg-white/5">
            <div className="w-8 h-8 rounded-full bg-gradient-to-br from-cyan-400 to-purple-600 flex-shrink-0 flex items-center justify-center text-xs font-bold text-white">
              {user.username?.[0]?.toUpperCase()}
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-sm font-semibold text-white truncate">{user.username}</p>
              <p className="text-xs text-slate-500 truncate">
                {connectedPlatforms.length > 0
                  ? `${connectedPlatforms.length} platform${connectedPlatforms.length > 1 ? "s" : ""}`
                  : "No platforms"}
              </p>
            </div>
          </div>
        )}

        <button onClick={handleLogout}
          className="flex items-center gap-3 w-full px-3 py-2 rounded-xl text-slate-400 hover:text-red-400 hover:bg-red-400/10 transition-all group">
          <LogOut className="w-4 h-4 group-hover:rotate-12 transition-transform flex-shrink-0" />
          {!collapsed && <span className="text-sm font-semibold">Log Out</span>}
        </button>
      </div>
    </div>
  );

  return (
    <>
      {/* Desktop Sidebar Container */}
      <motion.aside 
        animate={{ width: collapsed ? 68 : 232 }} 
        transition={{ type: "spring", bounce: 0.1, duration: 0.4 }}
        className="hidden lg:flex flex-col fixed left-0 top-0 h-full z-40 glass border-r border-white/5"
      >
        {/* Inner wrapper prevents items from breaking layout boundaries while shrinking */}
        <div className="w-full h-full flex flex-col overflow-hidden">
          {renderContent()}
        </div>

        {/* Toggle Arrow Button is placed outside the overflow box to remain unclipped */}
        <button onClick={() => setCollapsed(!collapsed)}
          className="absolute top-5 -right-3 w-6 h-6 rounded-full glass border border-white/10 flex items-center justify-center hover:border-cyan-400/40 transition-colors z-50">
          <ChevronRight className="w-3 h-3 text-slate-400 transition-transform"
            style={{ transform: collapsed ? "rotate(0deg)" : "rotate(180deg)" }} />
        </button>
      </motion.aside>

      {/* Mobile topbar */}
      <div className="lg:hidden fixed top-0 left-0 right-0 z-50 glass border-b border-white/5 px-4 py-3 flex items-center justify-between">
        <Link href="/dashboard" className="flex items-center gap-2">
          <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-cyan-400 to-purple-600 flex items-center justify-center">
            <Zap className="w-4 h-4 text-white" />
          </div>
          <span className="font-display text-base font-bold gradient-text">CodePulse</span>
        </Link>
        <button onClick={() => setMobileOpen(!mobileOpen)} className="text-white p-1">
          {mobileOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
        </button>
      </div>

      {/* Mobile drawer */}
      <AnimatePresence>
        {mobileOpen && (
          <>
            <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
              className="lg:hidden fixed inset-0 z-40 bg-black/60 backdrop-blur-sm"
              onClick={() => setMobileOpen(false)} />
            <motion.div initial={{ x: "-100%" }} animate={{ x: 0 }} exit={{ x: "-100%" }}
              transition={{ type: "spring", bounce: 0, duration: 0.3 }}
              className="lg:hidden fixed left-0 top-0 h-full w-60 z-50 glass overflow-hidden">
              {renderContent()}
            </motion.div>
          </>
        )}
      </AnimatePresence>
    </>
  );
}