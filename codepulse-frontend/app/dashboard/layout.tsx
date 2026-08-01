"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Sidebar } from "@/components/layout/Sidebar";
import { useAuthStore } from "@/lib/store";
import dynamic from "next/dynamic";

const ParticleBackground = dynamic(
  () => import("@/components/animations/ParticleBackground").then((m) => m.ParticleBackground),
  { ssr: false }
);

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const { isAuthenticated } = useAuthStore();
  const router = useRouter();
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
    if (!isAuthenticated) router.push("/auth/login");
  }, [isAuthenticated, router]);

  if (!mounted || !isAuthenticated) return null;

  return (
    <div className="min-h-screen bg-pulse-950 relative">
      <ParticleBackground />
      <div
        className="fixed inset-0 pointer-events-none z-0"
        style={{
          backgroundImage:
            "linear-gradient(to right,rgba(0,245,255,0.025) 1px,transparent 1px)," +
            "linear-gradient(to bottom,rgba(0,245,255,0.025) 1px,transparent 1px)",
          backgroundSize: "40px 40px",
        }}
      />
      <div className="fixed top-0 left-1/4 w-96 h-96 rounded-full bg-cyan-500/4 blur-3xl pointer-events-none" />
      <div className="fixed bottom-1/4 right-1/4 w-80 h-80 rounded-full bg-purple-500/4 blur-3xl pointer-events-none" />

      <Sidebar />

      {/* lg:ml-[232px] matches the expanded sidebar width */}
      <main className="lg:ml-[232px] pt-16 lg:pt-0 min-h-screen relative z-10">
        <div className="p-5 lg:p-7">{children}</div>
      </main>
    </div>
  );
}