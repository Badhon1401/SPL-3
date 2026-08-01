# CodePulse — Next.js Frontend

Intelligent Coding Performance Analyzer  
**Student:** Abdus Salam Islam Badhon | **ID:** BSSE-1401

---

## Tech Stack

| Layer        | Technology                                         |
|--------------|----------------------------------------------------|
| Framework    | Next.js 14 (App Router)                            |
| Language     | TypeScript                                         |
| Styling      | Tailwind CSS + custom cyberpunk theme              |
| UI Components| Radix UI primitives (shadcn/ui compatible)         |
| Animations   | Framer Motion                                      |
| Charts       | Recharts                                           |
| State        | Zustand (auth store)                               |
| Server State | TanStack React Query                               |
| HTTP Client  | Axios (with JWT interceptors)                      |
| Icons        | Lucide React                                       |
| Toasts       | react-hot-toast                                    |
| Type effects | react-type-animation                               |
| Fonts        | Orbitron · Rajdhani · Exo 2 · JetBrains Mono      |

---

## Project Structure

```
codepulse-frontend/
├── app/
│   ├── page.tsx                    ← redirects → /auth/login
│   ├── layout.tsx                  ← root layout + metadata
│   ├── globals.css                 ← cyberpunk theme, neon utils, animations
│   ├── auth/
│   │   ├── login/page.tsx          ← split-panel login with particle bg
│   │   └── register/page.tsx       ← 2-step registration wizard
│   ├── dashboard/
│   │   ├── layout.tsx              ← auth guard + sidebar + particle bg
│   │   └── page.tsx                ← main dashboard (stats, charts, recs)
│   ├── analytics/
│   │   └── page.tsx                ← deep analytics with all chart types
│   ├── recommendations/
│   │   └── page.tsx                ← full recommendations list + generate
│   └── profile/
│       └── page.tsx                ← profile edit + platform handles
├── components/
│   ├── animations/
│   │   └── ParticleBackground.tsx  ← interactive canvas particle system
│   ├── charts/
│   │   ├── RatingLineChart.tsx     ← area chart, CF rating over time
│   │   ├── VerdictDonut.tsx        ← donut chart, submission verdicts
│   │   ├── WeaknessRadar.tsx       ← radar chart, topic weakness vs strength
│   │   ├── DifficultyBar.tsx       ← bar chart, difficulty breakdown
│   │   └── ActivityHeatmap.tsx     ← GitHub-style heatmap (last 26 weeks)
│   ├── dashboard/
│   │   ├── StatCard.tsx            ← animated countup stat card
│   │   └── RecommendationCard.tsx  ← problem card with solve/dismiss
│   └── layout/
│       ├── Sidebar.tsx             ← collapsible sidebar + mobile drawer
│       └── Providers.tsx           ← QueryClient + Toaster
├── lib/
│   ├── api.ts                      ← Axios client + all API functions
│   └── store.ts                    ← Zustand auth store (persisted)
├── .env.local.example
├── next.config.js
├── tailwind.config.js
├── tsconfig.json
└── package.json
```

---

## Setup & Run

### 1. Install dependencies
```bash
npm install
# or
yarn install
```

### 2. Configure environment
```bash
cp .env.local.example .env.local
# Edit .env.local:
# NEXT_PUBLIC_API_URL=http://localhost:8080
```

### 3. Start the Spring Boot backend first
Make sure the backend is running on port 8080.

### 4. Run development server
```bash
npm run dev
```

Open [http://localhost:3000](http://localhost:3000)

---

## Pages & Features

### `/auth/login`
- Split-panel layout: branding left, form right
- Interactive particle background with mouse repulsion
- TypeAnimation terminal effect
- JWT token stored in localStorage via Zustand

### `/auth/register`
- 2-step wizard: Identity → Credentials
- Animated step progress indicator

### `/dashboard` *(protected)*
- Stat cards with CountUp animation: total subs, solved, acceptance rate, accepted
- Rating area chart (live from Codeforces API)
- Verdict donut chart
- Weakness radar (topic failure rates)
- Difficulty bar chart
- Activity heatmap (26 weeks)
- Topic mastery bars
- AI recommendations preview
- Sync CF button (triggers async backend job)

### `/analytics` *(protected)*
- All charts from dashboard + expanded views
- Weakness detail table with severity badges (Critical / Weak / Good)
- Topic mastery grid

### `/recommendations` *(protected)*
- Full recommendations list with platform filter (All / CF / LC)
- Mark solved (feedback loop) + dismiss buttons
- Regenerate button triggers backend AI engine

### `/profile` *(protected)*
- Edit full name, avatar URL
- Set Codeforces + LeetCode handles
- Badge display (CF Connected, Verified)

---

## Design System

**Aesthetic:** Cyberpunk / Futuristic dark  
**Primary color:** Neon Cyan `#00f5ff`  
**Accent:** Neon Purple `#bf5fff`  
**Success:** Neon Green `#39ff14`  
**Danger:** Neon Pink `#ff2d78`  
**Background:** Deep Navy `#04050d`

**Fonts:**
- Display / Headings: `Orbitron` (geometric, sci-fi)
- Body / Labels: `Rajdhani` (clean, semi-condensed)
- Monospace: `JetBrains Mono`

**Effects:**
- Glassmorphism cards with blur backdrop
- Neon glow text shadows
- Particle network canvas with mouse interaction
- Framer Motion staggered page reveals
- Corner bracket decorators
- Animated progress bars with glow tips
- Scan line overlays
- CSS shimmer text animation

---

## API Integration

All API calls go through `lib/api.ts` which:
- Attaches `Authorization: Bearer <token>` automatically
- Redirects to `/auth/login` on 401

Endpoints used:
| Feature | Endpoint |
|---------|----------|
| Login | `POST /api/auth/login` |
| Register | `POST /api/auth/register` |
| Get profile | `GET /api/users/me` |
| Update profile | `PUT /api/users/me` |
| Analytics | `GET /api/analytics/me` |
| Sync CF data | `POST /api/analytics/sync` |
| Get recommendations | `GET /api/recommendations` |
| Generate recommendations | `POST /api/recommendations/generate` |
| Mark solved | `PATCH /api/recommendations/{id}/solved` |
| Dismiss | `PATCH /api/recommendations/{id}/dismiss` |
