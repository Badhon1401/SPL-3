"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Toaster } from "react-hot-toast";
import { useState } from "react";

export function Providers({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(() => new QueryClient({
    defaultOptions: {
      queries: { staleTime: 1000 * 60 * 5, retry: 1 },
    },
  }));

  return (
    <QueryClientProvider client={queryClient}>
      {children}
      <Toaster
        position="top-right"
        toastOptions={{
          style: {
            background: "rgba(13, 18, 37, 0.95)",
            color: "#e2e8f0",
            border: "1px solid rgba(0, 245, 255, 0.3)",
            backdropFilter: "blur(10px)",
            fontFamily: "'Rajdhani', sans-serif",
            fontSize: "15px",
          },
          success: {
            iconTheme: { primary: "#00f5ff", secondary: "#04050d" },
          },
          error: {
            iconTheme: { primary: "#ff2d78", secondary: "#04050d" },
          },
        }}
      />
    </QueryClientProvider>
  );
}
