# CodePulse — Intelligent Multi-Platform Coding Performance Analyzer

> **Student:** Abdus Salam Islam Badhon | **ID:** BSSE-1401  
> **Supervisor:** Mohd. Zulfiquar Hafiz, Professor, IIT — University of Dhaka

---

## Overview

CodePulse is a full-stack web application that aggregates and analyses competitive programming activity from **Codeforces, LeetCode, AtCoder, and CodeChef** into a single intelligent dashboard. Instead of checking four separate platforms, users get one unified view of their strengths, weaknesses, combined rating, and AI-generated problem recommendations.

### Core Value Propositions

| Problem (status quo) | CodePulse solution |
|---|---|
| Stats scattered across 4 platforms | Single combined dashboard |
| No cross-platform weakness detection | Unified topic weakness radar |
| Rating comparison between platforms is hard | CodePulse Rating — one number from all 4 |
| Problem selection is manual/random | Algorithmic + AI-powered recommendations |
| No insight into behavioural patterns | Hidden findings engine (8+ auto-generated insights) |
| AI can't understand your coding history | Mistral AI receives your full performance context |

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Next.js 14 Frontend                  │
│  Dashboard · Analytics · Recommendations · AI Coach     │
└──────────────────────┬──────────────────────────────────┘
                       │ REST / JWT
┌──────────────────────▼──────────────────────────────────┐
│               Spring Boot 3.2 Backend                   │
│                                                         │
│  AuthController  AnalyticsController  AiController      │
│  UserController  RecommendationController               │
│  SubmissionController                                   │
│                                                         │
│  ┌──────────────┐  ┌───────────────┐  ┌─────────────┐  │
│  │ Analytics    │  │ Recommendation│  │  Mistral AI  │  │
│  │ Service      │  │ Engine        │  │  (Spring AI) │  │
│  └──────┬───────┘  └───────┬───────┘  └──────┬──────┘  │
│         │                  │                  │         │
│  ┌──────▼──────────────────▼──────────────────▼──────┐  │
│  │         Data Sync Layer (Async, @Scheduled)       │  │
│  │  CF Sync  ·  LC Sync  ·  AtCoder Sync  ·  CC Sync │  │
│  └──────────────────────┬────────────────────────────┘  │
└─────────────────────────┼───────────────────────────────┘
                          │ JPA / Hibernate
┌─────────────────────────▼───────────────────────────────┐
│                    PostgreSQL                           │
│  users · submissions · problems · topics                │
│  recommendations · ai_sessions · ai_items               │
└─────────────────────────────────────────────────────────┘
```

---

## Tech Stack

### Backend
| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2 |
| Security | Spring Security + JWT (jjwt 0.11.5) + BCrypt |
| AI | Spring AI 1.0.0-M6 + Mistral `mistral-large-latest` |
| ORM | Spring Data JPA / Hibernate 6 |
| HTTP Client | Spring WebFlux WebClient |
| Database | PostgreSQL 15+ |
| Build | Maven 3.9 |

### Frontend
| Layer | Technology |
|---|---|
| Framework | Next.js 14 (App Router) |
| Language | TypeScript |
| Styling | Tailwind CSS + custom cyberpunk theme |
| Animation | Framer Motion |
| Charts | Recharts |
| State | Zustand + TanStack Query v5 |
| HTTP | Axios |
| Fonts | Orbitron · Rajdhani · JetBrains Mono |

---

## Supported Platforms

| Platform | API | Data collected |
|---|---|---|
| **Codeforces** | Official REST API | Submissions, verdict, rating history, problem tags, difficulty |
| **LeetCode** | GraphQL API | Submissions, Easy/Medium/Hard difficulty, topic tags |
| **AtCoder** | kenkoooo.com community API | Submissions, difficulty model (Elo-like), contest info |
| **CodeChef** | Community wrapper API | Solved problems, division-based difficulty estimation |

---

## Features

### 1. Combined Dashboard
- **CodePulse Rating** — unified rating computed from all 4 platforms (see algorithm below)
- Current & longest streak across all platforms
- Platform connection grid showing handle names + stats
- Recent submissions timeline (clickable links to actual problems)

### 2. Deep Analytics (no duplication with dashboard)
- Activity heatmap (26 weeks, all platforms combined)
- Per-platform comparison chart
- CF rating trend line chart
- Difficulty distribution (normalised across platforms)
- Topic coverage bar chart (top 12)
- Weakness vs Strength radar
- Combined verdict distribution donut
- **Hidden Insights Engine** — auto-generates 6–10 findings:
  - Critical weakness detection
  - Zero-accepted topic (blind spots)
  - Acceptance rate analysis
  - Difficulty comfort-zone alert
  - Streak pattern recognition
  - Topic diversity score
  - Platform imbalance detection
  - Activity trend (last 30 vs previous 30 days)
  - Strength signature recognition

### 3. Curated Recommendations (`/recommendations`)
- Algorithmic recommendations based on detected weakness topics
- Estimated user rating determines difficulty range
- Recommendations come from any of the 4 platforms
- Platform filter (All / CF / LC / AC / CC)
- Mark solved (feedback loop) · Dismiss
- Generate new replaces previous

### 4. AI Coach (`/ai-recommend`)
- Free-text prompt ("I want to practice DP on trees")
- Mistral AI receives **full user context**: rating, weakness scores, per-platform stats, recently solved problems, recent WA/TLE topics
- Returns 4–10 personalised problem recommendations with direct URLs
- Coach Insight paragraph + Focus Areas
- Results stored in DB — shown on page load (previous session)
- Regenerate replaces previous session
- Mark solved · Dismiss per item

### 5. Profile
- All 4 platform handles always pre-filled from server
- Connection status badge per platform
- Clicking handle opens profile on that platform
- Saving triggers re-sync eligibility

### 6. Auto-Sync
- Runs every 6 hours for all active users (configurable cron)
- Triggered manually by user from any page
- Submission pruning: keeps latest 300 per user (configurable)

---

## CodePulse Rating Algorithm

A single Elo-like number representing your combined performance:

```
For each connected platform:
  CF_rating  = actual CF rating from /user.rating API (or estimated from solved problems)
  LC_rating  = 1200 + (easy × 2) + (medium × 9) + (hard × 22) + acceptance_rate_bonus
  AC_rating  = average difficulty of solved AtCoder problems × 1.05
  CC_rating  = 1000 + (easy × 3) + (medium × 10) + (hard × 20) + (expert × 35)

Weight per platform = √(submission_count_on_platform)
  → More activity on a platform gives it more influence but with diminishing returns

CodePulse_Rating = Σ(platform_rating × weight) / Σ(weight)
  → Clipped to [800, 3800]
```

**Tier mapping** (mirrors CF tier names for familiarity):

| Rating | Tier |
|---|---|
| < 1000 | Beginner |
| 1000–1199 | Pupil |
| 1200–1399 | Apprentice |
| 1400–1599 | Specialist |
| 1600–1899 | Expert |
| 1900–2099 | Candidate Master |
| 2100–2399 | Master |
| 2400–2599 | International Master |
| 2600–2999 | Grandmaster |
| 3000+ | Legendary Grandmaster |

---

## Project Structure

```
codepulse/
├── backend/                              Spring Boot backend
│   └── src/main/java/com/codepulse/
│       ├── CodePulseApplication.java
│       ├── config/
│       │   ├── AppConfig.java            ChatClient bean, @EnableScheduling
│       │   ├── MistralRestClientConfig.java  ← FIXES M4/M6 JSON parse error
│       │   ├── SecurityConfig.java
│       │   ├── WebClientConfig.java
│       │   └── DataSeeder.java           Seeds 20 algorithm topics on startup
│       ├── controller/
│       │   ├── AuthController.java
│       │   ├── UserController.java
│       │   ├── AnalyticsController.java
│       │   ├── RecommendationController.java
│       │   ├── SubmissionController.java  GET /api/submissions/recent
│       │   ├── AiRecommendationController.java
│       │   └── AdminController.java
│       ├── entity/
│       │   ├── User.java                 (4 platform handles)
│       │   ├── Problem.java
│       │   ├── Submission.java
│       │   ├── Topic.java
│       │   ├── Recommendation.java
│       │   ├── AiRecommendationSession.java
│       │   └── AiRecommendationItem.java
│       ├── dto/
│       │   ├── request/  LoginRequest, RegisterRequest, UpdateProfileRequest, AiPromptRequest
│       │   └── response/ AuthResponse, UserProfileResponse, PerformanceAnalyticsResponse,
│       │                 RecommendationResponse, AiPromptResponse, RecentSubmissionResponse
│       ├── repository/   (all JPA repositories + pruning queries)
│       ├── security/     JwtUtil, JwtAuthFilter, UserPrincipal, UserDetailsServiceImpl
│       └── service/
│           ├── impl/
│           │   ├── AnalyticsServiceImpl.java      Combined analytics + streak + platform breakdown
│           │   ├── CombinedRatingCalculator.java  CodePulse Rating algorithm
│           │   ├── CodeforcesDataService.java     CF API sync
│           │   ├── LeetcodeDataService.java       LC GraphQL sync
│           │   ├── AtCoderDataService.java        kenkoooo API sync
│           │   ├── CodeChefDataService.java       CC community API sync
│           │   ├── AiRecommendationServiceImpl.java  Mistral AI + DB storage
│           │   ├── RecommendationServiceImpl.java    Algorithmic recommendations
│           │   ├── SubmissionPruningService.java     300-submission ceiling
│           │   ├── ScheduledSyncService.java         Auto-sync every 6h
│           │   ├── AuthServiceImpl.java
│           │   └── UserServiceImpl.java
│           ├── AnalyticsService.java
│           ├── AiRecommendationService.java
│           ├── RecommendationService.java
│           └── UserService.java
│
└── frontend/                             Next.js 14 frontend
    ├── app/
    │   ├── page.tsx                      → redirects to /auth/login
    │   ├── layout.tsx                    Root layout
    │   ├── globals.css                   Cyberpunk theme + neon utils
    │   ├── auth/login/page.tsx           Split-panel login + particle bg
    │   ├── auth/register/page.tsx        2-step wizard
    │   ├── dashboard/
    │   │   ├── layout.tsx                Auth guard + sidebar + particle bg
    │   │   └── page.tsx                  Overview: rating, handles, recent subs
    │   ├── analytics/page.tsx            Deep analytics + insights panel
    │   ├── recommendations/page.tsx      Curated problem recommendations
    │   ├── ai-recommend/page.tsx         AI Coach with prompt + stored session
    │   └── profile/page.tsx             Handles pre-filled from server
    ├── components/
    │   ├── animations/ParticleBackground.tsx
    │   ├── charts/
    │   │   ├── RatingLineChart.tsx
    │   │   ├── VerdictDonut.tsx
    │   │   ├── WeaknessRadar.tsx
    │   │   └── DifficultyBar.tsx + ActivityHeatmap (same file)
    │   ├── dashboard/
    │   │   ├── StatCard.tsx              Animated CountUp stat card
    │   │   ├── StreakWidget.tsx          Streak + rating tier display
    │   │   └── RecommendationCard.tsx
    │   └── layout/
    │       ├── Sidebar.tsx               Collapsible + mobile + platform badges
    │       └── Providers.tsx             QueryClient + Toaster
    └── lib/
        ├── api.ts                        Axios client + all API functions
        ├── store.ts                      Zustand auth store
        └── ratingTier.ts                 Tier label helper
```

---

## Setup & Running

### Prerequisites
- Java 17+
- Node.js 18+
- PostgreSQL 15+
- Mistral AI API key (free at [console.mistral.ai](https://console.mistral.ai))

---

### Backend Setup

**1. Create the database**
```sql
CREATE DATABASE codepulse;
```

**2. Generate JWT secret**
```bash
openssl rand -base64 32
```

**3. Configure `application.properties`**
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/codepulse
spring.datasource.username=postgres
spring.datasource.password=YOUR_DB_PASSWORD

jwt.secret=YOUR_BASE64_SECRET_FROM_ABOVE

spring.ai.mistralai.api-key=YOUR_MISTRAL_API_KEY
```

Or use environment variables:
```bash
export MISTRAL_API_KEY=your_key_here
```

**4. Run**
```bash
mvn spring-boot:run
```

Server starts on `http://localhost:8080`. 20 algorithm topics are seeded automatically.

**Known Fix — Mistral M6 JSON parse error:**  
The `MistralRestClientConfig.java` bean is already included and fixes the `prompt_tokens_details` `UnrecognizedPropertyException` that occurs with Spring AI milestone versions. No manual action needed — it is auto-configured on startup.

---

### Frontend Setup

**1. Install dependencies**
```bash
cd frontend
npm install
```

**2. Configure environment**
```bash
cp .env.local.example .env.local
# Edit if backend is not on localhost:8080
# NEXT_PUBLIC_API_URL=http://localhost:8080
```

**3. Run**
```bash
npm run dev
```

Open [http://localhost:3000](http://localhost:3000) — redirects to login.

---

## API Reference

### Authentication
```
POST /api/auth/register   { username, email, password, fullName }
POST /api/auth/login      { email, password }
```
Both return: `{ token, type, userId, username, email, role }`

### User
```
GET  /api/users/me              Get own profile (with platform handles)
PUT  /api/users/me              Update profile (handles, fullName, avatar)
GET  /api/users/{id}            Get user by ID
```

### Analytics
```
GET  /api/analytics/me          Full combined analytics (all 4 platforms)
GET  /api/analytics/{userId}    Analytics for specific user
POST /api/analytics/sync        Trigger sync for all connected platforms (async)
```

### Submissions
```
GET  /api/submissions/recent?limit=10   Recent submissions (all platforms)
```

### Recommendations (algorithmic)
```
GET  /api/recommendations               Active curated recommendations
POST /api/recommendations/generate      Generate new (replaces previous)
PATCH /api/recommendations/{id}/solved  Mark solved
PATCH /api/recommendations/{id}/dismiss Dismiss
```

### AI Coach
```
POST /api/ai/recommend              Generate AI recommendations with { prompt, count }
GET  /api/ai/sessions/latest        Load stored AI session
PATCH /api/ai/items/{id}/solved     Mark AI item solved
PATCH /api/ai/items/{id}/dismiss    Dismiss AI item
```

### Admin
```
GET    /api/admin/users             List all users
GET    /api/admin/users/{id}        Get user by ID
DELETE /api/admin/users/{id}        Deactivate user
```

All endpoints except `/api/auth/**` require:
```
Authorization: Bearer <token>
```

---

## Data Flow

```
User sets handle(s) in Profile
         ↓
POST /api/analytics/sync (or auto every 6h)
         ↓
  ┌──────┴──────────────────────────────────┐
  │  CF: /user.status (up to 200 subs)      │
  │  LC: GraphQL recentSubmissionList       │
  │  AC: kenkoooo /user/submissions         │
  │  CC: community API /handle             │
  └──────────────┬──────────────────────────┘
                 ↓
  Problems + Topics stored (platform-tagged)
  Submissions stored (deduplicated by platformSubmissionId)
  Pruning: keep latest 300 per user
                 ↓
GET /api/analytics/me
  → Computes CodePulse Rating (weighted average)
  → Streak calculation (all platforms combined)
  → Weakness scores, topic breakdown, difficulty distribution
  → Platform breakdown per-platform stats
  → CF rating trend (live from CF API)
                 ↓
Frontend: InsightsEngine generates 6–10 findings from the data
                 ↓
POST /api/ai/recommend { prompt: "I want to practice DP" }
  → Builds rich context (rating, weaknesses, recent WA topics, etc.)
  → Sends to Mistral API → JSON response
  → Stored in ai_recommendation_sessions + ai_recommendation_items
  → Returned with clickable problem URLs
```

---

## Environment Variables

### Backend
| Variable | Description |
|---|---|
| `MISTRAL_API_KEY` | Your Mistral AI API key |
| `spring.datasource.password` | PostgreSQL password |
| `jwt.secret` | Base64-encoded 256-bit JWT signing key |
| `codepulse.submissions.max-per-user` | Submission ceiling (default: 300) |
| `codepulse.sync.cron` | Cron expression for auto-sync (default: every 6h) |

### Frontend
| Variable | Description |
|---|---|
| `NEXT_PUBLIC_API_URL` | Backend URL (default: `http://localhost:8080`) |

---

## Page Guide

| Route | Purpose | Key content |
|---|---|---|
| `/dashboard` | Overview | CodePulse Rating, platform handles + stats, streak, recent submissions |
| `/analytics` | Deep analysis | Heatmap, platform breakdown, CF trend, topic radar, **hidden insights** |
| `/recommendations` | Curated problems | Algorithmic recs from weak topics, all 4 platforms, generate/dismiss |
| `/ai-recommend` | AI Coach | Prompt input, Mistral-generated recs with full context, stored session |
| `/profile` | Account | All handles (pre-filled from server), connection status, edit form |

---

## Troubleshooting

### Mistral AI — `prompt_tokens_details` UnrecognizedPropertyException
Already fixed by `MistralRestClientConfig.java`. Also ensure your `application.properties` has:
```properties
spring.jackson.deserialization.fail-on-unknown-properties=false
```

### Handles not showing on dashboard/profile
The profile page now always fetches from the server (`staleTime: 0`) and the dashboard refreshes the profile on load. If handles still aren't showing, click "Sync All Platforms" — this calls `PUT /api/analytics/sync` which re-reads handles from the DB.

### LeetCode sync failing
LeetCode's GraphQL endpoint occasionally rate-limits. Try again after a few minutes.

### AtCoder sync slow
The kenkoooo.com API returns the complete submission history in one call. First sync may take a few seconds.

---

*CodePulse — Department of IIT, University of Dhaka*