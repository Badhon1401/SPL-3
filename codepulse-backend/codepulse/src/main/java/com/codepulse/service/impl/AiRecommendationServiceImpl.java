package com.codepulse.service.impl;

import com.codepulse.dto.request.AiPromptRequest;
import com.codepulse.dto.response.AiPromptResponse;
import com.codepulse.dto.response.AiPromptResponse.AiItem;
import com.codepulse.dto.response.PerformanceAnalyticsResponse;
import com.codepulse.entity.*;
import com.codepulse.repository.AiRecommendationItemRepository;
import com.codepulse.repository.AiRecommendationSessionRepository;
import com.codepulse.repository.SubmissionRepository;
import com.codepulse.service.AiRecommendationService;
import com.codepulse.service.AnalyticsService;
import com.codepulse.service.UserService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiRecommendationServiceImpl implements AiRecommendationService {

    private final DirectMistralService directMistralService;
    private final OpenRouterService openRouterService;
    private final GroqService groqService;          // ← NEW
    private final AnalyticsService analyticsService;
    private final UserService userService;
    private final SubmissionRepository submissionRepository;
    private final AiRecommendationSessionRepository sessionRepo;
    private final AiRecommendationItemRepository itemRepo;
    private final ObjectMapper objectMapper;

    @Value("${mistral.model:mistral-small-latest}")
    private String mistralModel;

    private static final String SYSTEM_PROMPT = """
        You are CodePulse AI — an expert competitive programming coach with deep knowledge of Codeforces, LeetCode, AtCoder, and CodeChef problems.

        CRITICAL OUTPUT RULE — YOUR ENTIRE RESPONSE MUST BE A SINGLE JSON OBJECT:
        - The FIRST character of your response MUST be '{'.
        - The LAST character of your response MUST be '}'.
        - Do NOT write any text before the JSON. No "Here is", no "Sure", no "Thinking Process", no prose.
        - Do NOT write any text after the JSON.
        - Do NOT wrap the JSON in markdown code fences. No ```json. No ```.
        - Do NOT add an extra '{' wrapper around the JSON. Output exactly ONE root object.

        OTHER RULES:
        - Never truncate. Always close all braces and brackets.
        - Recommend REAL problems with REAL URLs from Codeforces, LeetCode, AtCoder, or CodeChef.
        - The "reason" field must reference specific weaknesses or goals from THIS user's data.
        - Spread recommendations across platforms when applicable.
        - Consider the user's recent problems to avoid repeating what they've recently solved.

        If you cannot comply, respond with exactly:
        {"recommendations":[],"coachInsight":"Unable to generate recommendations.","focusAreas":[]}
        """;

    @Override
    @Transactional
    public AiPromptResponse generateRecommendations(Long userId, AiPromptRequest request) {
        User user = userService.getUserById(userId);
        PerformanceAnalyticsResponse analytics = safeGetAnalytics(userId);
        List<Submission> recentSubs = submissionRepository.findRecentByUserId(userId, PageRequest.of(0, 50));

        String context = buildRichContext(user, analytics, recentSubs);
        int count = request.getCount() != null ? Math.min(request.getCount(), 10) : 6;

        String userMessage = context
                + "\n\n## User's Request\n\"" + request.getPrompt() + "\""
                + "\n\n## REQUIRED JSON OUTPUT — respond with ONLY this structure:\n"
                + jsonSchema(count);

        log.info("Sending AI request for user {} ({})", userId, request.getPrompt());

        String rawResponse = null;
        String modelUsed = null;

        // ═══ Attempt 1: Groq (reliable, fast, free) ═══
        if (groqService.isConfigured()) {
            try {
                rawResponse = groqService.chat(SYSTEM_PROMPT, userMessage);
                modelUsed = groqService.getLastSuccessfulModel();
                log.info("✅ Groq succeeded: {}", modelUsed);
            } catch (Exception e) {
                log.warn("Groq failed: {}", e.getMessage());
            }
        }

        // ═══ Attempt 2: Mistral ═══
        if (rawResponse == null) {
            try {
                rawResponse = directMistralService.chat(SYSTEM_PROMPT, userMessage);
                modelUsed = mistralModel;
                log.info("✅ Mistral succeeded: {}", modelUsed);
            } catch (Exception e) {
                log.warn("Mistral failed: {}", e.getMessage());
            }
        }

        // ═══ Attempt 3: OpenRouter ═══
        if (rawResponse == null && openRouterService.isConfigured()) {
            try {
                rawResponse = openRouterService.chat(SYSTEM_PROMPT, userMessage);
                modelUsed = openRouterService.getLastSuccessfulModel();
                log.info("✅ OpenRouter succeeded: {}", modelUsed);
            } catch (Exception e) {
                log.warn("OpenRouter failed: {}", e.getMessage());
            }
        }

        if (rawResponse == null) {
            return buildError(request.getPrompt(), "All AI providers are currently unavailable.");
        }

        return parseAndPersist(rawResponse, userId, user, request.getPrompt(), count, modelUsed);
    }

    @Override
    @Transactional(readOnly = true)
    public AiPromptResponse getLatestSession(Long userId) {
        List<AiRecommendationSession> sessions = sessionRepo.findLatestActiveSessions(userId);
        if (sessions.isEmpty()) {
            return null;
        }
        return toResponse(sessions.get(0));
    }

    @Override
    @Transactional
    public void markItemSolved(Long userId, Long itemId) {
        itemRepo.findByIdWithSessionAndUser(itemId).ifPresent(item -> {
            if (!item.getSession().getUser().getId().equals(userId)) return;
            item.setSolved(true);
            itemRepo.save(item);
        });
    }

    @Override
    @Transactional
    public void dismissItem(Long userId, Long itemId) {
        itemRepo.findByIdWithSessionAndUser(itemId).ifPresent(item -> {
            if (!item.getSession().getUser().getId().equals(userId)) return;
            item.setDismissed(true);
            itemRepo.save(item);
        });
    }

    // ─── Rich context builder ─────────────────────────────────────────────────

    private String buildRichContext(User user, PerformanceAnalyticsResponse a, List<Submission> recent) {
        StringBuilder sb = new StringBuilder();
        sb.append("## User Profile\n");
        sb.append("- Username: ").append(user.getUsername()).append("\n");

        List<String> platforms = new ArrayList<>();
        if (user.getCodeforcesHandle() != null) platforms.add("Codeforces [" + user.getCodeforcesHandle() + "]");
        if (user.getLeetcodeHandle()   != null) platforms.add("LeetCode ["   + user.getLeetcodeHandle()   + "]");
        if (user.getAtcoderHandle()    != null) platforms.add("AtCoder ["    + user.getAtcoderHandle()    + "]");
        if (user.getCodechefHandle()   != null) platforms.add("CodeChef ["   + user.getCodechefHandle()   + "]");
        sb.append("- Active Platforms: ").append(String.join(", ", platforms)).append("\n\n");

        if (a != null) {
            sb.append("## Performance Overview\n");
            sb.append("- CodePulse Rating: ").append(a.getCombinedRating())
                    .append(" (").append(a.getRatingTier()).append(")\n");
            if (a.getPlatformRatings() != null)
                a.getPlatformRatings().forEach((p, r) ->
                        sb.append("  • ").append(p).append(" estimated rating: ").append(r).append("\n"));
            sb.append("- Unique Problems Solved: ").append(a.getUniqueProblemsSolved()).append(" (across all platforms)\n");
            sb.append("- Total Submissions: ").append(a.getTotalSubmissions()).append("\n");
            sb.append("- Overall Acceptance Rate: ").append(a.getAcceptanceRate()).append("%\n");
            sb.append("- Current Streak: ").append(a.getCurrentStreak()).append(" days\n");
            sb.append("- Longest Streak: ").append(a.getLongestStreak()).append(" days\n\n");

            if (a.getPlatformBreakdown() != null && !a.getPlatformBreakdown().isEmpty()) {
                sb.append("## Per-Platform Stats\n");
                a.getPlatformBreakdown().forEach((plat, stats) ->
                        sb.append("- ").append(plat).append(": ")
                                .append(stats.getUniqueSolved()).append(" solved, ")
                                .append(stats.getAcceptanceRate()).append("% AC, ")
                                .append(stats.getTotalSubmissions()).append(" total subs\n"));
                sb.append("\n");
            }

            if (a.getWeaknessScores() != null && !a.getWeaknessScores().isEmpty()) {
                sb.append("## Topic Weakness Analysis (higher % = weaker)\n");
                a.getWeaknessScores().entrySet().stream().limit(8).forEach(e ->
                        sb.append("- ").append(e.getKey())
                                .append(": ").append(Math.round(e.getValue() * 100)).append("% failure rate\n"));
                sb.append("\n");
            }

            if (a.getTopicBreakdown() != null && !a.getTopicBreakdown().isEmpty()) {
                sb.append("## Topic Strengths (most solved)\n");
                a.getTopicBreakdown().entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(5)
                        .forEach(e -> sb.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append(" solved\n"));
                sb.append("\n");
            }

            if (a.getDifficultyBreakdown() != null && !a.getDifficultyBreakdown().isEmpty()) {
                sb.append("## Difficulty Distribution\n");
                a.getDifficultyBreakdown().forEach((d, cnt) ->
                        sb.append("- ").append(d).append(": ").append(cnt).append(" solved\n"));
                sb.append("\n");
            }
        } else {
            sb.append("## Performance Data: Not yet available (no synced submissions)\n\n");
        }

        if (!recent.isEmpty()) {
            sb.append("## Recently Solved Problems (avoid recommending these)\n");
            recent.stream()
                    .filter(s -> s.getVerdict() == Submission.Verdict.ACCEPTED)
                    .map(s -> s.getProblem().getTitle() + " [" + s.getProblem().getPlatform() + "]")
                    .distinct().limit(15)
                    .forEach(t -> sb.append("- ").append(t).append("\n"));
            sb.append("\n");

            sb.append("## Recent Struggle Areas (recent WA/TLE attempts)\n");
            recent.stream()
                    .filter(s -> s.getVerdict() == Submission.Verdict.WRONG_ANSWER
                            || s.getVerdict() == Submission.Verdict.TIME_LIMIT_EXCEEDED)
                    .flatMap(s -> s.getProblem().getTopics().stream().map(Topic::getName))
                    .collect(Collectors.groupingBy(t -> t, Collectors.counting()))
                    .entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(5)
                    .forEach(e -> sb.append("- ").append(e.getKey()).append(": ")
                            .append(e.getValue()).append(" recent failures\n"));
        }

        return sb.toString();
    }

    private String jsonSchema(int count) {
        return """
        {
          "recommendations": [
            {
              "title": "Exact problem name",
              "platform": "CODEFORCES or LEETCODE or ATCODER or CODECHEF",
              "url": "https://real-direct-link-to-problem",
              "difficulty": "Beginner or Easy or Medium or Hard or Expert",
              "estimatedRating": 1400,
              "topics": ["Topic1", "Topic2"],
              "reason": "Specific reason referencing this user's weakness/goal",
              "timeEstimate": "25-35 min"
            }
          ],
          "coachInsight": "2-3 sentence personalized analysis of this user's status and path forward",
          "focusAreas": ["Area1", "Area2", "Area3"]
        }
        Produce exactly %d recommendations. Return ONLY the JSON object. No other text.
        """.formatted(count);
    }

    // ─── Robust JSON extraction ─────────────────────────────────────────────

    private String extractJson(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("AI response was null or blank");
        }

        String s = raw.trim();

        s = s.replaceAll("(?s)^\\s*```(?:json)?\\s*", "")
                .replaceAll("(?s)\\s*```\\s*$", "")
                .trim();

        for (int start = 0; start < s.length(); start++) {
            if (s.charAt(start) != '{') continue;

            int end = s.lastIndexOf('}');
            while (end > start) {
                String candidate = s.substring(start, end + 1);
                try {
                    objectMapper.readTree(candidate);
                    return candidate;
                } catch (Exception ignored) {
                    // try a shorter end
                }
                end = s.lastIndexOf('}', end - 1);
            }
        }

        throw new IllegalArgumentException(
                "No valid JSON object found in AI response. First 300 chars: "
                        + s.substring(0, Math.min(300, s.length())));
    }

    // ─── Parse + persist ──────────────────────────────────────────────────────

    @Transactional
    public AiPromptResponse parseAndPersist(String raw, Long userId, User user,
                                            String prompt, int expectedCount, String modelUsed) {
        try {
            String cleaned = extractJson(raw);
            JsonNode root = objectMapper.readTree(cleaned);

            sessionRepo.deactivateAllForUser(userId);

            List<String> focusAreas = new ArrayList<>();
            root.path("focusAreas").forEach(f -> focusAreas.add(f.asText()));

            AiRecommendationSession session = AiRecommendationSession.builder()
                    .user(user)
                    .prompt(prompt)
                    .coachInsight(root.path("coachInsight").asText(""))
                    .focusAreasJson(objectMapper.writeValueAsString(focusAreas))
                    .modelUsed(modelUsed)
                    .active(true)
                    .build();

            final AiRecommendationSession savedSession = sessionRepo.save(session);

            List<AiRecommendationItem> itemsToSave = new ArrayList<>();
            JsonNode recsNode = root.path("recommendations");

            if (recsNode.isArray()) {
                for (JsonNode n : recsNode) {
                    List<String> topics = new ArrayList<>();
                    n.path("topics").forEach(t -> topics.add(t.asText()));

                    AiRecommendationItem item = AiRecommendationItem.builder()
                            .session(savedSession)
                            .title(n.path("title").asText())
                            .platform(n.path("platform").asText("OTHER"))
                            .url(n.path("url").asText())
                            .difficulty(n.path("difficulty").asText("Medium"))
                            .estimatedRating(n.path("estimatedRating").asInt(0))
                            .topicsJson(objectMapper.writeValueAsString(topics))
                            .reason(n.path("reason").asText())
                            .timeEstimate(n.path("timeEstimate").asText())
                            .build();
                    itemsToSave.add(item);
                }
            }

            List<AiRecommendationItem> savedItems = itemRepo.saveAll(itemsToSave);
            savedSession.setItems(savedItems);

            return toResponse(savedSession);

        } catch (Exception e) {
            log.error("AI parse/persist failed: {}. Raw: {}", e.getMessage(),
                    raw == null ? "null" : raw.substring(0, Math.min(300, raw.length())));
            return buildError(prompt, null);
        }
    }

    private AiPromptResponse toResponse(AiRecommendationSession session) {
        List<AiItem> items = session.getItems().stream()
                .filter(i -> !i.isDismissed())
                .map(i -> {
                    List<String> topics = new ArrayList<>();
                    try { topics = objectMapper.readValue(i.getTopicsJson() != null ? i.getTopicsJson() : "[]",
                            new TypeReference<>() {}); } catch (Exception ignored) {}
                    return AiItem.builder()
                            .itemId(i.getId())
                            .title(i.getTitle()).platform(i.getPlatform())
                            .url(i.getUrl()).difficulty(i.getDifficulty())
                            .estimatedRating(i.getEstimatedRating()).topics(topics)
                            .reason(i.getReason()).timeEstimate(i.getTimeEstimate())
                            .solved(i.isSolved()).dismissed(i.isDismissed())
                            .build();
                }).toList();

        List<String> focusAreas = new ArrayList<>();
        try { if (session.getFocusAreasJson() != null)
            focusAreas = objectMapper.readValue(session.getFocusAreasJson(), new TypeReference<>() {});
        } catch (Exception ignored) {}

        return AiPromptResponse.builder()
                .sessionId(session.getId())
                .recommendations(items)
                .coachInsight(session.getCoachInsight())
                .focusAreas(focusAreas)
                .originalPrompt(session.getPrompt())
                .modelUsed(session.getModelUsed())
                .generatedAt(session.getCreatedAt())
                .build();
    }

    private PerformanceAnalyticsResponse safeGetAnalytics(Long userId) {
        try { return analyticsService.getAnalytics(userId); }
        catch (Exception e) { log.warn("Analytics for AI context: {}", e.getMessage()); return null; }
    }

    private AiPromptResponse buildError(String prompt, String msg) {
        return AiPromptResponse.builder()
                .recommendations(List.of())
                .coachInsight(msg != null ? msg : "The AI service is temporarily unavailable. Please try again.")
                .focusAreas(List.of())
                .originalPrompt(prompt)
                .modelUsed(mistralModel)
                .generatedAt(LocalDateTime.now())
                .build();
    }
}
