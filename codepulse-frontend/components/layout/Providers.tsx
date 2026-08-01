"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Toaster } from "react-hot-toast";
import { useState } from "react";

export function Providers({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 0,               // Data is immediately stale
            gcTime: 0,                  // v5 property: Purge memory immediately when leaving a page
            refetchOnMount: "always",   // Always hit backend API on every page mount
            refetchOnWindowFocus: true, // Refetch when clicking back into browser window
            retry: 0,                   // Fail fast if Spring Boot is down (no retry delays)
          },
        },
      })
  );

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