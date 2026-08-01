# CodePulse — Spring Boot Backend

Intelligent Coding Performance Analyzer  
**Student:** Abdus Salam Islam Badhon | **ID:** BSSE-1401 | **Supervisor:** Mohd. Zulfiquar Hafiz

---

## Tech Stack

| Layer        | Technology                                    |
|--------------|-----------------------------------------------|
| Language     | Java 17                                       |
| Framework    | Spring Boot 3.2                               |
| Security     | Spring Security + JWT (jjwt 0.11.5) + BCrypt  |
| Database     | PostgreSQL                                    |
| ORM          | Spring Data JPA / Hibernate                   |
| HTTP Client  | Spring WebFlux WebClient (Codeforces API)     |
| Build Tool   | Maven                                         |

---

## Project Structure

```
src/main/java/com/codepulse/
├── CodePulseApplication.java
├── config/
│   ├── DataSeeder.java          ← seeds 20 algorithm topics on startup
│   ├── SecurityConfig.java
│   └── WebClientConfig.java
├── controller/
│   ├── AdminController.java
│   ├── AnalyticsController.java
│   ├── AuthController.java
│   ├── RecommendationController.java
│   └── UserController.java
├── dto/
│   ├── request/
│   │   ├── LoginRequest.java
│   │   ├── RegisterRequest.java
│   │   └── UpdateProfileRequest.java
│   └── response/
│       ├── ApiResponse.java
│       ├── AuthResponse.java
│       ├── PerformanceAnalyticsResponse.java
│       ├── RecommendationResponse.java
│       └── UserProfileResponse.java
├── entity/
│   ├── Problem.java
│   ├── Recommendation.java
│   ├── Submission.java
│   ├── Topic.java
│   └── User.java
├── exception/
│   ├── BadRequestException.java
│   ├── GlobalExceptionHandler.java
│   └── ResourceNotFoundException.java
├── repository/
│   ├── ProblemRepository.java
│   ├── RecommendationRepository.java
│   ├── SubmissionRepository.java
│   ├── TopicRepository.java
│   └── UserRepository.java
├── security/
│   ├── JwtAuthenticationFilter.java
│   ├── JwtUtil.java
│   ├── UserDetailsServiceImpl.java
│   └── UserPrincipal.java
└── service/
    ├── AnalyticsService.java
    ├── AuthService.java
    ├── RecommendationService.java
    ├── UserService.java
    └── impl/
        ├── AnalyticsServiceImpl.java
        ├── AuthServiceImpl.java
        ├── CodeforcesDataService.java
        ├── RecommendationServiceImpl.java
        └── UserServiceImpl.java
```

---

## Setup

### 1. Create PostgreSQL Database
```sql
CREATE DATABASE codepulse;
```

### 2. Configure `application.properties`
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/codepulse
spring.datasource.username=postgres
spring.datasource.password=your_password

# Generate a Base64-encoded 256-bit key and paste here:
jwt.secret=your-base64-encoded-256-bit-secret
jwt.expiration=86400000
```

**Generate JWT secret (run once):**
```bash
openssl rand -base64 32
```

### 3. Run
```bash
mvn spring-boot:run
```

The server starts on `http://localhost:8080`. Topics are seeded automatically on first boot.

---

## API Reference

### Auth
| Method | Endpoint             | Body                             | Auth |
|--------|----------------------|----------------------------------|------|
| POST   | `/api/auth/register` | `{username, email, password, fullName}` | ❌ |
| POST   | `/api/auth/login`    | `{email, password}`              | ❌   |

Both return `{ token, type, userId, username, email, role }`.

---

### Users
| Method | Endpoint          | Description            | Auth |
|--------|-------------------|------------------------|------|
| GET    | `/api/users/me`   | Get own profile        | ✅   |
| PUT    | `/api/users/me`   | Update own profile     | ✅   |
| GET    | `/api/users/{id}` | Get user by ID         | ✅   |

**Update profile body** (all optional):
```json
{
  "fullName": "Abdus Salam",
  "codeforcesHandle": "tourist",
  "leetcodeHandle": "myhandle",
  "avatarUrl": "https://..."
}
```

---

### Analytics
| Method | Endpoint              | Description                          | Auth |
|--------|-----------------------|--------------------------------------|------|
| GET    | `/api/analytics/me`   | Full performance analytics           | ✅   |
| GET    | `/api/analytics/{id}` | Analytics for specific user          | ✅   |
| POST   | `/api/analytics/sync` | Trigger Codeforces data sync (async) | ✅   |

**Analytics response includes:**
- `totalSubmissions`, `acceptedSubmissions`, `acceptanceRate`, `uniqueProblemsSolved`
- `topicBreakdown` — accepted problems per topic (for pie/bar charts)
- `weaknessScores` — 0.0–1.0 per topic (0 = strong, 1 = weakest)
- `difficultyBreakdown` — Beginner / Easy / Medium / Hard / Expert
- `activityHeatmap` — `{ "2025-04-01": 5, ... }` (last 365 days)
- `verdictDistribution` — ACCEPTED / WRONG_ANSWER / TLE / ...
- `ratingTrend` — `[{ date, rating }]` from Codeforces API

---

### Recommendations
| Method | Endpoint                         | Description                  | Auth |
|--------|----------------------------------|------------------------------|------|
| GET    | `/api/recommendations`           | Get active recommendations   | ✅   |
| POST   | `/api/recommendations/generate`  | Generate new recommendations | ✅   |
| PATCH  | `/api/recommendations/{id}/solved`  | Mark as solved (feedback)  | ✅   |
| PATCH  | `/api/recommendations/{id}/dismiss` | Dismiss recommendation     | ✅   |

---

### Admin (ROLE_ADMIN only)
| Method | Endpoint               | Description       |
|--------|------------------------|-------------------|
| GET    | `/api/admin/users`     | List all users    |
| GET    | `/api/admin/users/{id}`| Get user by ID    |
| DELETE | `/api/admin/users/{id}`| Deactivate user   |

---

## Workflow

```
User sets Codeforces handle
        ↓
POST /api/analytics/sync   ← fetches up to 1000 submissions from CF API (async)
        ↓
Problems & Topics stored in DB
        ↓
GET /api/analytics/me      ← computes all metrics on-the-fly
        ↓
POST /api/recommendations/generate  ← engine analyses weaknesses & rating
        ↓
GET /api/recommendations   ← frontend displays ranked problems to solve
```

---

## Data Model

```
User ──< Submission >── Problem ──< Topic
User ──< Recommendation >── Problem
```

---

## Security

All endpoints except `/api/auth/**` require:
```
Authorization: Bearer <token>
```

Passwords are hashed with BCrypt. JWT tokens expire after 24 hours (configurable).
