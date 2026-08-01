/** @type {import('tailwindcss').Config} */
module.exports = {
  darkMode: ["class"],
  content: [
    "./pages/**/*.{ts,tsx}",
    "./components/**/*.{ts,tsx}",
    "./app/**/*.{ts,tsx}",
    "./src/**/*.{ts,tsx}",
  ],
  theme: {
    container: {
      center: true,
      padding: "2rem",
      screens: { "2xl": "1400px" },
    },
    extend: {
      colors: {
        border: "hsl(var(--border))",
        input: "hsl(var(--input))",
        ring: "hsl(var(--ring))",
        background: "hsl(var(--background))",
        foreground: "hsl(var(--foreground))",
        primary: {
          DEFAULT: "hsl(var(--primary))",
          foreground: "hsl(var(--primary-foreground))",
        },
        secondary: {
          DEFAULT: "hsl(var(--secondary))",
          foreground: "hsl(var(--secondary-foreground))",
        },
        destructive: {
          DEFAULT: "hsl(var(--destructive))",
          foreground: "hsl(var(--destructive-foreground))",
        },
        muted: {
          DEFAULT: "hsl(var(--muted))",
          foreground: "hsl(var(--muted-foreground))",
        },
        accent: {
          DEFAULT: "hsl(var(--accent))",
          foreground: "hsl(var(--accent-foreground))",
        },
        card: {
          DEFAULT: "hsl(var(--card))",
          foreground: "hsl(var(--card-foreground))",
        },
        // CodePulse custom palette
        neon: {
          cyan: "#00f5ff",
          green: "#39ff14",
          purple: "#bf5fff",
          pink: "#ff2d78",
          orange: "#ff6b35",
        },
        pulse: {
          950: "#04050d",
          900: "#080b18",
          800: "#0d1225",
          700: "#141d38",
          600: "#1e2d52",
          500: "#2a3f70",
          400: "#3a5599",
          300: "#5577cc",
          200: "#7799ee",
          100: "#aabbff",
        },
      },
      fontFamily: {
        display: ["'Orbitron'", "monospace"],
        body: ["'Rajdhani'", "sans-serif"],
        mono: ["'JetBrains Mono'", "monospace"],
        accent: ["'Exo 2'", "sans-serif"],
      },
      borderRadius: {
        lg: "var(--radius)",
        md: "calc(var(--radius) - 2px)",
        sm: "calc(var(--radius) - 4px)",
      },
      keyframes: {
        "accordion-down": { from: { height: "0" }, to: { height: "var(--radix-accordion-content-height)" } },
        "accordion-up": { from: { height: "var(--radix-accordion-content-height)" }, to: { height: "0" } },
        "pulse-glow": {
          "0%, 100%": { boxShadow: "0 0 5px #00f5ff, 0 0 10px #00f5ff, 0 0 20px #00f5ff" },
          "50%": { boxShadow: "0 0 10px #00f5ff, 0 0 30px #00f5ff, 0 0 60px #00f5ff" },
        },
        "scan-line": {
          "0%": { transform: "translateY(-100%)" },
          "100%": { transform: "translateY(100vh)" },
        },
        float: {
          "0%, 100%": { transform: "translateY(0px)" },
          "50%": { transform: "translateY(-12px)" },
        },
        "text-shimmer": {
          "0%": { backgroundPosition: "-200% center" },
          "100%": { backgroundPosition: "200% center" },
        },
        "border-beam": {
          "100%": { "offset-distance": "100%" },
        },
        "meteor": {
          "0%": { transform: "rotate(215deg) translateX(0)", opacity: "1" },
          "70%": { opacity: "1" },
          "100%": { transform: "rotate(215deg) translateX(-500px)", opacity: "0" },
        },
        grid: {
          "0%": { transform: "translateY(-50%)" },
          "100%": { transform: "translateY(0)" },
        },
      },
      animation: {
        "accordion-down": "accordion-down 0.2s ease-out",
        "accordion-up": "accordion-up 0.2s ease-out",
        "pulse-glow": "pulse-glow 2s ease-in-out infinite",
        "scan-line": "scan-line 3s linear infinite",
        float: "float 4s ease-in-out infinite",
        "text-shimmer": "text-shimmer 3s linear infinite",
        "border-beam": "border-beam calc(var(--duration)*1s) infinite linear",
        meteor: "meteor 5s linear infinite",
        grid: "grid 15s linear infinite",
      },
      backgroundImage: {
        "grid-pattern": "linear-gradient(to right, rgba(0,245,255,0.05) 1px, transparent 1px), linear-gradient(to bottom, rgba(0,245,255,0.05) 1px, transparent 1px)",
        "noise": "url(\"data:image/svg+xml,%3Csvg viewBox='0 0 256 256' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noise'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noise)' opacity='0.05'/%3E%3C/svg%3E\")",
      },
    },
  },
  plugins: [require("tailwindcss-animate")],
}
