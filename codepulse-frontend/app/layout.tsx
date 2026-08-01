import type { Metadata } from "next";
import "./globals.css";
import { Providers } from "@/components/layout/Providers";

export const metadata: Metadata = {
  title: "CodePulse — Intelligent Coding Performance Analyzer",
  description: "Transform your coding activity into deep analytical insights. Track, analyze, and conquer competitive programming.",
  keywords: ["competitive programming", "codeforces", "leetcode", "analytics", "performance"],
  authors: [{ name: "Abdus Salam Islam Badhon" }],
  openGraph: {
    title: "CodePulse",
    description: "Intelligent Coding Performance Analyzer",
    type: "website",
  },
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
      </head>
      <body className="font-body antialiased">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
