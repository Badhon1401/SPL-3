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
    private final AnalyticsService analyticsService;
    private final UserService userService;
    private final SubmissionRepository submissionRepository;
    private final AiRecommendationSessionRepository sessionRepo;
    private final AiRecommendationItemRepository itemRepo;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
        You are CodePulse AI — an expert competitive programming coach with deep knowledge of Codeforces, LeetCode, AtCoder, and CodeChef problems.

        Your job: analyze the user's FULL performance data and their specific request, then recommend exactly the right problems.

        ABSOLUTE RULES — violations cause system failure:
        1. Respond with ONLY valid, complete JSON. No markdown. No ```json. No text outside JSON.
        2. Never truncate. Always close all braces and brackets.
        3. Recommend REAL problems with REAL URLs. You know thousands of CF/LC/AC/CC problems from training.
        4. The "reason" must reference specific weaknesses or goals from THIS user's data.
        5. Spread recommendations across platforms when applicable.
        6. Consider the user's recent problems to avoid repeating what they've recently solved.
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

        String rawResponse;
        String modelUsed;

        try {
            // PRIMARY
            rawResponse = directMistralService.chat(
                    SYSTEM_PROMPT,
                    userMessage
            );

            modelUsed = "mistral-large-latest";

            log.info("Primary Mistral model succeeded");

        } catch (Exception mistralException) {

            log.warn(
                    "Primary Mistral failed: {}. Trying OpenRouter fallbacks...",
                    mistralException.getMessage()
            );

            if (!openRouterService.isConfigured()) {

                log.error(
                        "Mistral failed and OpenRouter API key is not configured"
                );

                return buildError(
                        request.getPrompt(),
                        "AI service unavailable."
                );
            }

            try {

                rawResponse = openRouterService.chat(
                        SYSTEM_PROMPT,
                        userMessage
                );

                modelUsed = openRouterService.getLastSuccessfulModel();

                log.info(
                        "Fallback model succeeded: {}",
                        modelUsed
                );

            } catch (Exception fallbackException) {

                log.error(
                        "All fallback models failed: {}",
                        fallbackException.getMessage()
                );

                return buildError(
                        request.getPrompt(),
                        "All AI providers are currently unavailable."
                );
            }
        }

        return parseAndPersist(
                rawResponse,
                userId,
                user,
                request.getPrompt(),
                count,
                modelUsed
        );
    }

    @Override
    @Transactional(readOnly = true)
    public AiPromptResponse getLatestSession(Long userId) {

        List<AiRecommendationSession> sessions =
                sessionRepo.findLatestActiveSessions(userId);

        if (sessions.isEmpty()) {
            return null;
        }

        return toResponse(sessions.get(0));
    }

    @Override
    @Transactional
    public void markItemSolved(Long userId, Long itemId) {

        itemRepo.findByIdWithSessionAndUser(itemId)
                .ifPresent(item -> {

                    if (!item.getSession().getUser().getId().equals(userId)) {
                        return;
                    }

                    item.setSolved(true);

                    itemRepo.save(item);
                });
    }

    @Override
    @Transactional
    public void dismissItem(Long userId, Long itemId) {

        itemRepo.findByIdWithSessionAndUser(itemId)
                .ifPresent(item -> {

                    if (!item.getSession().getUser().getId().equals(userId)) {
                        return;
                    }

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

            // Per-platform breakdown
            if (a.getPlatformBreakdown() != null && !a.getPlatformBreakdown().isEmpty()) {
                sb.append("## Per-Platform Stats\n");
                a.getPlatformBreakdown().forEach((plat, stats) ->
                        sb.append("- ").append(plat).append(": ")
                                .append(stats.getUniqueSolved()).append(" solved, ")
                                .append(stats.getAcceptanceRate()).append("% AC, ")
                                .append(stats.getTotalSubmissions()).append(" total subs\n"));
                sb.append("\n");
            }

            // Weaknesses (sorted by failure rate)
            if (a.getWeaknessScores() != null && !a.getWeaknessScores().isEmpty()) {
                sb.append("## Topic Weakness Analysis (higher % = weaker)\n");
                a.getWeaknessScores().entrySet().stream().limit(8).forEach(e ->
                        sb.append("- ").append(e.getKey())
                                .append(": ").append(Math.round(e.getValue() * 100)).append("% failure rate\n"));
                sb.append("\n");
            }

            // Strengths
            if (a.getTopicBreakdown() != null && !a.getTopicBreakdown().isEmpty()) {
                sb.append("## Topic Strengths (most solved)\n");
                a.getTopicBreakdown().entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(5)
                        .forEach(e -> sb.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append(" solved\n"));
                sb.append("\n");
            }

            // Difficulty distribution
            if (a.getDifficultyBreakdown() != null && !a.getDifficultyBreakdown().isEmpty()) {
                sb.append("## Difficulty Distribution\n");
                a.getDifficultyBreakdown().forEach((d, cnt) ->
                        sb.append("- ").append(d).append(": ").append(cnt).append(" solved\n"));
                sb.append("\n");
            }
        } else {
            sb.append("## Performance Data: Not yet available (no synced submissions)\n\n");
        }

        // Recent problems (last 15 unique accepted problems)
        if (!recent.isEmpty()) {
            sb.append("## Recently Solved Problems (avoid recommending these)\n");
            recent.stream()
                    .filter(s -> s.getVerdict() == Submission.Verdict.ACCEPTED)
                    .map(s -> s.getProblem().getTitle() + " [" + s.getProblem().getPlatform() + "]")
                    .distinct().limit(15)
                    .forEach(t -> sb.append("- ").append(t).append("\n"));
            sb.append("\n");

            // Recent attempts with WA/TLE (topics where user is currently struggling)
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

    // ─── Parse + persist ──────────────────────────────────────────────────────

    @Transactional
    public AiPromptResponse parseAndPersist(String raw, Long userId, User user,
                                            String prompt, int expectedCount, String modelUsed) {
        try {
            String cleaned = raw.trim().replaceAll("(?s)\\s*```$", "").trim();
            JsonNode root = objectMapper.readTree(cleaned);

            // 1. Deactivate previous session
            sessionRepo.deactivateAllForUser(userId);

            // 2. Build session entity
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

            // 3. Build ALL items in memory first
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

            // 4. FAST: Batch insert all items in ONE database call
            List<AiRecommendationItem> savedItems = itemRepo.saveAll(itemsToSave);
            savedSession.setItems(savedItems);

            return toResponse(savedSession);

        } catch (Exception e) {
            log.error("AI parse/persist failed: {}. Raw: {}", e.getMessage(), raw.substring(0, Math.min(200, raw.length())));
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
                .modelUsed("mistral-large-latest")
                .generatedAt(LocalDateTime.now())
                .build();
    }
}