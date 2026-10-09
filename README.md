# CodePulse — Spring Boot Backend

**Intelligent Coding Performance Analyzer**
**Student:** Abdus Salam Islam Badhon | **ID:** BSSE-1401 | **Supervisor:** Mohd. Zulfiquar Hafiz

CodePulse collects a competitive programmer's submissions from **Codeforces, LeetCode, AtCoder and CodeChef**,
turns them into analytics (a unified *CodePulse Rating*, weak/strong topics, streaks, heatmap), and recommends
what to practise next, both with a rule-based engine and with an **AI coach** that answers free-text requests
such as "give me some tree problems".

---

## Tech Stack

| Layer        | Technology                                                             |
|--------------|------------------------------------------------------------------------|
| Language     | Java 17                                                                |
| Framework    | Spring Boot 3.3.4 (Hibernate 6.5.3)                                    |
| Security     | Spring Security + JWT + BCrypt                                         |
| Database     | PostgreSQL (hosted on Neon, SSL required)                              |
| HTTP clients | WebClient (platform APIs), RestTemplate (AI providers)                 |
| AI providers | Groq, Mistral, OpenRouter (free models), all with automatic fallback   |
| Build tool   | Maven                                                                  |
| Frontend     | Next.js 14, React 18, Tailwind, Recharts, Zustand, Axios, React Query  |

---

## Features

- **Auth:** register / login with JWT, roles `USER` and `ADMIN`.
- **Multi-platform sync:** Codeforces, LeetCode, AtCoder, CodeChef (manual button + automatic every 6 hours).
- **Analytics:** acceptance rate, unique problems solved, streaks, activity heatmap, verdict distribution,
  topic strengths, topic weakness scores, difficulty breakdown, per-platform stats, Codeforces rating trend.
- **CodePulse Rating:** one Elo-like number combining all connected platforms (see below).
- **Rule-based recommendations:** weakness-driven and level-driven problems from the local database.
- **AI recommendations:** natural-language requests answered by an LLM using the user's own statistics,
  stored as sessions, with solved / dismiss feedback per item.
- **Housekeeping:** old submissions are pruned (default 300 per user) so analytics stay current.

---

## Project Structure

```
src/main/java/com/codepulse/
├── CodePulseApplication.java
├── config/                      DataSeeder, SecurityConfig, WebClientConfig
├── controller/
│   ├── AdminController.java
│   ├── AiDiagnosticsController.java      (self-test for AI providers)
│   ├── AiRecommendationController.java
│   ├── AnalyticsController.java
│   ├── AuthController.java
│   ├── RecommendationController.java
│   ├── SubmissionController.java
│   └── UserController.java
├── dto/request | dto/response
├── entity/
│   ├── AiRecommendationItem.java
│   ├── AiRecommendationSession.java
│   ├── Problem.java
│   ├── Recommendation.java
│   ├── Submission.java
│   ├── Topic.java
│   └── User.java
├── exception/                   BadRequest, ResourceNotFound, GlobalExceptionHandler
├── repository/                  7 Spring Data JPA repositories
├── security/                    JwtAuthenticationFilter, JwtUtil, UserDetailsServiceImpl, UserPrincipal
└── service/
    ├── AiRecommendationService, AnalyticsService, AuthService, RecommendationService, UserService
    └── impl/
        ├── AiRecommendationServiceImpl.java   AI orchestration + persistence
        ├── AiJsonUtil.java                    JSON extraction / validation helpers
        ├── ModelHealthRegistry.java           remembers working / failing models
        ├── GroqService.java                   Groq provider (auto model discovery)
        ├── DirectMistralService.java          Mistral provider (auto model discovery)
        ├── OpenRouterService.java             OpenRouter provider (free models)
        ├── OpenRouterModelDiscovery.java      finds usable free OpenRouter models
        ├── AnalyticsServiceImpl.java
        ├── CombinedRatingCalculator.java      CodePulse Rating
        ├── CodeforcesDataService.java
        ├── LeetcodeDataService.java
        ├── AtCoderDataService.java
        ├── CodeChefDataService.java
        ├── SubmissionPruningService.java
        ├── ScheduledSyncService.java
        ├── RecommendationServiceImpl.java     rule-based engine
        ├── AuthServiceImpl.java
        └── UserServiceImpl.java
```

---

## Setup

### 1. Database
Create a PostgreSQL database (local, or a Neon project). Tables are created automatically
(`spring.jpa.hibernate.ddl-auto=update`).

### 2. Configure `src/main/resources/application.properties`

```properties
server.port=8080

# --- Database (example for Neon; use your own values) ---
spring.datasource.url=jdbc:postgresql://<host>/<db>?sslmode=require
spring.datasource.username=<user>
spring.datasource.password=<password>
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
spring.jpa.open-in-view=false

# --- JWT ---
jwt.secret=<base64-encoded 256-bit secret>      # openssl rand -base64 32
jwt.expiration=86400000

# --- CORS ---
cors.allowed-origins=http://localhost:3000

# --- Platform APIs ---
codeforces.api.base-url=https://codeforces.com/api
leetcode.api.base-url=https://leetcode.com/graphql
leetcode.max-submissions=200
atcoder.api.base-url=https://kenkoooo.com/atcoder
atcoder.difficulties.url=https://kenkoooo.com/atcoder/resources/problem-models.json
codechef.api.base-url=https://codechef-api.vercel.app

# --- Housekeeping ---
codepulse.submissions.max-per-user=300
codepulse.sync.enabled=true
codepulse.sync.cron=0 0 */6 * * *

# --- AI providers: ONLY the keys are needed (leave one out to skip that provider) ---
app.groq.api-key=gsk_<your key>
mistral.api-key=<your key>
app.openrouter.api-key=sk-or-v1-<your key>
```

Rules for keys: one per line, no quotes, no spaces, keep the prefix (`gsk_`, `sk-or-v1-`).
**Never commit `application.properties` to Git** (add it to `.gitignore`) and rotate any key that was shared.

Free keys: Groq https://console.groq.com/keys · Mistral https://console.mistral.ai · OpenRouter https://openrouter.ai/keys

### 3. Run
```bash
mvn clean package -DskipTests
mvn spring-boot:run
```
The API starts on `http://localhost:8080`. Frontend (separate project): `npm install && npm run dev` → `http://localhost:3000`.

---

## How the AI Recommendation Works

```
POST /api/ai/recommend  { "prompt": "some dp problems", "count": 4 }
        │
        ▼
1. Build context from the user's analytics (rating, weak topics, recent solves / failures)
2. Choose provider order automatically: last working first, recently failed last
3. For each provider: discover the models the key can use, try them best-first
4. Accept an answer ONLY if it is valid JSON containing a "recommendations" array
5. Clean the result (real platform URLs only), save the session + items, return it
```

Key behaviours:

- **No model names to configure.** Groq and Mistral are asked which models your key can use; OpenRouter is
  filtered to truly free text models (non-reasoning and JSON-capable ones first).
- **Automatic fallback** Groq → Mistral → OpenRouter, and model-by-model inside each provider.
- **Failure memory** (`ModelHealthRegistry`): rate-limited models are skipped for 3 minutes, dead or useless
  models for 10–60 minutes, so later requests are fast. Nothing is banned forever.
- **Strict validation:** reasoning text, refusals, truncated JSON or empty lists count as failures and the next
  model is tried; `<think>` blocks and code fences are stripped.
- **Safe links:** a model's URL is kept only if it points to Codeforces, LeetCode, AtCoder or CodeChef,
  otherwise a search link is generated.
- **No long DB transactions:** the LLM call runs outside any transaction; saving uses a short one.
- **Time limits:** 45–60 s per model, at most 10 OpenRouter attempts within 90 s.
- If every provider fails, the API returns an empty list with a friendly message (never a stack trace).

Self-test (needs a login token):
```powershell
$h = @{ Authorization = "Bearer <token>" }
Invoke-RestMethod http://localhost:8080/api/ai/health/groq       -Headers $h
Invoke-RestMethod http://localhost:8080/api/ai/health/mistral    -Headers $h
Invoke-RestMethod http://localhost:8080/api/ai/health/openrouter -Headers $h
```
Expected: `status = OK`. Remove `AiDiagnosticsController` before a public deployment.

---

## CodePulse Rating

1. Estimate a rating per connected platform:
   - **Codeforces:** latest real rating from `user.rating` (fallback: average rating of solved problems).
   - **LeetCode:** `1200 + 2·Easy + 9·Medium + 22·Hard + acceptance%` (max 3200).
   - **AtCoder:** average difficulty of solved problems × 1.05 (fallback: 800 + 8 per solved).
   - **CodeChef:** `1000 + 3·Easy + 10·Medium + 20·Hard + 35·Expert` (max 3000).
2. Weight each platform by `sqrt(number of submissions on that platform)`.
3. Final rating = weighted average, clamped to 800 – 3800.

Tiers: Beginner (<1000) · Pupil · Apprentice · Specialist · Expert · Candidate Master · Master ·
International Master · Grandmaster · Legendary Grandmaster (≥3000).

---

## Platform Sync

| Platform   | Source                         | What is imported                                   |
|------------|--------------------------------|----------------------------------------------------|
| Codeforces | official API `user.status`     | latest 200 submissions with verdict, tags, rating  |
| LeetCode   | GraphQL `recentSubmissionList` | up to `leetcode.max-submissions` with all verdicts |
| AtCoder    | kenkoooo community API         | all submissions + difficulty models                |
| CodeChef   | community profile API          | solved problem codes (time = sync time)            |

Sync runs when the user presses *Sync* (`POST /api/analytics/sync`) and automatically every 6 hours
(`ScheduledSyncService`). After each sync, `SubmissionPruningService` keeps only the newest
`codepulse.submissions.max-per-user` submissions.

---

## API Reference

All endpoints except `/api/auth/**` need `Authorization: Bearer <token>`.

### Auth
| Method | Endpoint             | Body                                    |
|--------|----------------------|-----------------------------------------|
| POST   | `/api/auth/register` | `{username, email, password, fullName}` |
| POST   | `/api/auth/login`    | `{email, password}`                     |

Both return `{ token, type, userId, username, email, role }`.

### Users
| Method | Endpoint          | Description        |
|--------|-------------------|--------------------|
| GET    | `/api/users/me`   | Own profile        |
| PUT    | `/api/users/me`   | Update profile and platform handles (`codeforcesHandle`, `leetcodeHandle`, `atcoderHandle`, `codechefHandle`, `fullName`, `avatarUrl`) |
| GET    | `/api/users/{id}` | Profile by id      |

### Analytics & Submissions
| Method | Endpoint                              | Description                               |
|--------|---------------------------------------|-------------------------------------------|
| GET    | `/api/analytics/me`                   | Full analytics for the current user       |
| GET    | `/api/analytics/{id}`                 | Analytics for a specific user             |
| POST   | `/api/analytics/sync`                 | Sync all connected platforms              |
| GET    | `/api/submissions/recent?limit=12`    | Most recent submissions (max 50)          |

Analytics response: `totalSubmissions`, `acceptedSubmissions`, `uniqueProblemsSolved`, `acceptanceRate`,
`currentStreak`, `longestStreak`, `totalActiveDays`, `combinedRating`, `ratingTier`, `platformRatings`,
`topicBreakdown`, `weaknessScores`, `difficultyBreakdown`, `activityHeatmap`, `verdictDistribution`,
`ratingTrend`, `platformBreakdown`.

### Rule-based recommendations
| Method | Endpoint                            | Description                     |
|--------|-------------------------------------|---------------------------------|
| GET    | `/api/recommendations`              | Active recommendations          |
| POST   | `/api/recommendations/generate`     | (Re)generate                    |
| PATCH  | `/api/recommendations/{id}/solved`  | Mark solved                     |
| PATCH  | `/api/recommendations/{id}/dismiss` | Dismiss                         |

### AI recommendations
| Method | Endpoint                         | Description                                           |
|--------|----------------------------------|-------------------------------------------------------|
| POST   | `/api/ai/recommend`              | `{prompt, count (1-10)}` → new session (replaces old) |
| GET    | `/api/ai/sessions/latest`        | Latest stored session, or `null`                      |
| PATCH  | `/api/ai/items/{itemId}/solved`  | Mark an AI item solved                                |
| PATCH  | `/api/ai/items/{itemId}/dismiss` | Dismiss an AI item                                    |
| GET    | `/api/ai/health/{provider}`      | Self-test: `groq`, `mistral` or `openrouter`          |

Response fields: `sessionId`, `recommendations[]` (`itemId`, `title`, `platform`, `url`, `difficulty`,
`estimatedRating`, `topics`, `reason`, `timeEstimate`, `solved`, `dismissed`), `coachInsight`, `focusAreas`,
`originalPrompt`, `modelUsed`, `generatedAt`.

### Admin (ROLE_ADMIN)
| Method | Endpoint                | Description     |
|--------|-------------------------|-----------------|
| GET    | `/api/admin/users`      | List all users  |
| GET    | `/api/admin/users/{id}` | Get user        |
| DELETE | `/api/admin/users/{id}` | Deactivate user |

---

## Data Model

```
User ──< Submission >── Problem >──< Topic
User ──< Recommendation >── Problem
User ──< AiRecommendationSession ──< AiRecommendationItem
```

Only one AI session per user is active at a time; creating a new one deactivates the previous one.

---

## Workflow

```
User sets platform handles (profile page)
        ↓
POST /api/analytics/sync        (also automatic every 6 h)
        ↓
Submissions, problems and topics stored; old submissions pruned
        ↓
GET /api/analytics/me           CodePulse Rating + all metrics computed on the fly
        ↓
POST /api/recommendations/generate     or     POST /api/ai/recommend
        ↓
Frontend shows ranked problems; user marks solved / dismisses (feedback loop)
```

---

## Troubleshooting

| Symptom (log)                                              | Cause / fix                                                                 |
|------------------------------------------------------------|-----------------------------------------------------------------------------|
| `Groq rejected the API key (HTTP 401)`                     | Wrong or revoked key. Create a new one, paste the full `gsk_…` string.     |
| `Mistral HTTP 429 Rate limit exceeded`                     | Free-tier limit. Wait; the app falls back to other providers by itself.    |
| `model_not_found` / `decommissioned`                       | Harmless; the model is skipped and the list is re-read from the provider.  |
| OpenRouter `402`, `403`, `429`                             | Paid / restricted / busy model; skipped automatically.                     |
| `empty content (finish_reason=length)`                     | Reasoning model ran out of tokens; skipped and put on cooldown.            |
| `All AI providers failed`                                  | No key works right now. Run the `/api/ai/health/*` checks and fix the keys.|
| `There is not enough space on the disk` during `install`   | Free disk space; use `mvn clean package -DskipTests` instead of `install`. |
| `LazyInitializationException`                              | `open-in-view=false` is intentional; read lazy data inside `@Transactional`.|
| `HHH90000025 PostgreSQLDialect...` warning                 | Remove `spring.jpa.properties.hibernate.dialect` from properties.          |

---

## Security

- Passwords are hashed with BCrypt; JWT expiry is set by `jwt.expiration` (milliseconds).
- Keep secrets (database password, `jwt.secret`, API keys) out of Git; use environment variables or a local,
  git-ignored properties file.
- Open-in-view is disabled so database connections are released right after each repository call.
