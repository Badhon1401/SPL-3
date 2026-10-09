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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Flow:
 *   1. Build user context (short read-only transaction).
 *   2. Ask providers in order: Groq -> Mistral -> OpenRouter.
 *      A provider only "wins" if its answer contains valid recommendation JSON;
 *      otherwise the next provider is tried.
 *   3. Persist the session + items (short write transaction).
 *
 * The LLM call happens OUTSIDE any DB transaction, so a slow model can no longer
 * hold a Hikari connection for minutes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiRecommendationServiceImpl implements AiRecommendationService {

    private final DirectMistralService directMistralService;
    private final OpenRouterService openRouterService;
    private final GroqService groqService;
    private final AnalyticsService analyticsService;
    private final UserService userService;
    private final SubmissionRepository submissionRepository;
    private final AiRecommendationSessionRepository sessionRepo;
    private final AiRecommendationItemRepository itemRepo;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;
    private final ModelHealthRegistry registry;

    private static final List<String> KNOWN_HOSTS =
            List.of("codeforces.com", "leetcode.com", "atcoder.jp", "codechef.com");

    private static final String SYSTEM_PROMPT = """
        You are CodePulse AI - an expert competitive programming coach with deep knowledge of Codeforces, LeetCode, AtCoder, and CodeChef problems.

        CRITICAL OUTPUT RULE - YOUR ENTIRE RESPONSE MUST BE A SINGLE JSON OBJECT:
        - The FIRST character of your response MUST be '{'.
        - The LAST character of your response MUST be '}'.
        - Do NOT write any text before the JSON. No "Here is", no "Sure", no "Thinking Process", no analysis, no prose.
        - Do NOT write any text after the JSON.
        - Do NOT wrap the JSON in markdown code fences. No ```json. No ```.
        - Output exactly ONE root object. Do not output your reasoning.

        OTHER RULES:
        - Never truncate. Always close all braces and brackets.
        - "estimatedRating" must be an integer. "topics" must be an array of strings.
        - Recommend REAL, well-known problems with REAL direct URLs from Codeforces, LeetCode, AtCoder, or CodeChef.
        - The "reason" field must reference specific weaknesses or goals from THIS user's data.
        - Match difficulty to the user's current level. If the user asks for something more advanced than their level,
          pick the easiest problems of that advanced type and mention the prerequisite idea in "reason".
        - Spread recommendations across platforms when applicable.
        - Avoid problems the user has recently solved.

        If you cannot comply, respond with exactly:
        {"recommendations":[],"coachInsight":"Unable to generate recommendations.","focusAreas":[]}
        """;

    private record ProviderResult(String json, String model) {}

    private record Provider(String name, Supplier<String> call, Supplier<String> model) {}

    private int providerRank(String name) {
        String good = registry.lastGoodProvider();
        if (good != null && good.equalsIgnoreCase(name)) return 0;
        if (registry.isProviderCoolingDown(name)) return 2;
        return 1;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Generate
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    public AiPromptResponse generateRecommendations(Long userId, AiPromptRequest request) {
        int count = request.getCount() != null ? Math.max(1, Math.min(request.getCount(), 10)) : 6;

        // 1) Context (analytics runs in its own transaction; context build in a short read-only one)
        PerformanceAnalyticsResponse analytics = safeGetAnalytics(userId);

        TransactionTemplate readTx = new TransactionTemplate(transactionManager);
        readTx.setReadOnly(true);
        String context = readTx.execute(status -> {
            User user = userService.getUserById(userId);
            List<Submission> recentSubs =
                    submissionRepository.findRecentByUserId(userId, PageRequest.of(0, 50));
            return buildRichContext(user, analytics, recentSubs);
        });

        String userMessage = context
                + "\n\n## User's Request\n\"" + request.getPrompt() + "\""
                + "\n\n## REQUIRED JSON OUTPUT - respond with ONLY this structure:\n"
                + jsonSchema(count);

        log.info("Sending AI request for user {} ({})", userId, request.getPrompt());

        // 2) Providers (NO database transaction is open here).
        //    Order is automatic: last working provider first, providers on cooldown last.
        List<String> failures = new ArrayList<>();
        List<Provider> providers = new ArrayList<>();
        if (groqService.isConfigured()) {
            providers.add(new Provider("Groq",
                    () -> groqService.chat(SYSTEM_PROMPT, userMessage, validator()),
                    groqService::getLastSuccessfulModel));
        }
        if (directMistralService.isConfigured()) {
            providers.add(new Provider("Mistral",
                    () -> directMistralService.chat(SYSTEM_PROMPT, userMessage, validator()),
                    directMistralService::getModel));
        }
        if (openRouterService.isConfigured()) {
            providers.add(new Provider("OpenRouter",
                    () -> openRouterService.chat(SYSTEM_PROMPT, userMessage, validator()),
                    openRouterService::getLastSuccessfulModel));
        }
        providers.sort(Comparator.comparingInt(p -> providerRank(p.name())));
        log.info("AI provider order for this request: {}",
                providers.stream().map(Provider::name).toList());

        ProviderResult result = null;
        for (Provider p : providers) {
            result = attempt(p.name(), failures, p.call(), p.model());
            if (result != null) break;
        }

        if (result == null) {
            log.error("All AI providers failed for user {}: {}", userId, failures);
            return buildError(request.getPrompt(),
                    "The AI providers are busy or unavailable right now. Please try again in a minute.");
        }

        // 3) Persist
        final ProviderResult winner = result;
        TransactionTemplate writeTx = new TransactionTemplate(transactionManager);
        try {
            return writeTx.execute(status ->
                    persist(winner.json(), userId, request.getPrompt(), winner.model()));
        } catch (Exception e) {
            log.error("AI result could not be saved: {}", e.getMessage(), e);
            return buildError(request.getPrompt(),
                    "The AI answered, but the result could not be saved. Please try again.");
        }
    }

    private Predicate<String> validator() {
        return raw -> AiJsonUtil.extractRecommendationJson(objectMapper, raw).isPresent();
    }

    private ProviderResult attempt(String name, List<String> failures,
                                   Supplier<String> call, Supplier<String> modelName) {
        long t0 = System.currentTimeMillis();
        try {
            String raw = call.get();
            Optional<String> json = AiJsonUtil.extractRecommendationJson(objectMapper, raw);
            if (json.isEmpty()) {
                failures.add(name + ": response was not valid recommendation JSON");
                registry.markProviderFailure(name, 2 * ModelHealthRegistry.MIN);
                return null;
            }
            registry.markProviderSuccess(name);
            log.info("✅ {} succeeded with model {} in {} ms", name, modelName.get(),
                    System.currentTimeMillis() - t0);
            return new ProviderResult(json.get(), modelName.get());
        } catch (Exception e) {
            log.warn("{} failed after {} ms: {}", name, System.currentTimeMillis() - t0,
                    AiJsonUtil.truncate(e.getMessage(), 300));
            failures.add(name + ": " + AiJsonUtil.truncate(e.getMessage(), 200));
            registry.markProviderFailure(name, 2 * ModelHealthRegistry.MIN);
            return null;
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Read / update
    // ═════════════════════════════════════════════════════════════════════════

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

    // ═════════════════════════════════════════════════════════════════════════
    //  Context builder
    // ═════════════════════════════════════════════════════════════════════════

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

    // ═════════════════════════════════════════════════════════════════════════
    //  Persist (runs inside the write TransactionTemplate)
    // ═════════════════════════════════════════════════════════════════════════

    private AiPromptResponse persist(String json, Long userId, String prompt, String modelUsed) {
        try {
            JsonNode root = objectMapper.readTree(json);
            User user = userService.getUserById(userId);

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
                    String title = n.path("title").asText("").trim();
                    if (title.isBlank()) continue;

                    List<String> topics = new ArrayList<>();
                    n.path("topics").forEach(t -> topics.add(t.asText()));

                    String platform = normalizePlatform(n.path("platform").asText(""));

                    AiRecommendationItem item = AiRecommendationItem.builder()
                            .session(savedSession)
                            .title(cut(title, 255))
                            .platform(cut(platform, 255))
                            .url(sanitizeUrl(n.path("url").asText(""), platform, title))
                            .difficulty(cut(n.path("difficulty").asText("Medium"), 255))
                            .estimatedRating(n.path("estimatedRating").asInt(0))
                            .topicsJson(objectMapper.writeValueAsString(topics))
                            .reason(n.path("reason").asText(""))
                            .timeEstimate(cut(n.path("timeEstimate").asText(""), 255))
                            .build();
                    itemsToSave.add(item);
                }
            }

            if (itemsToSave.isEmpty()) {
                throw new IllegalStateException("AI JSON contained no usable recommendation items");
            }

            List<AiRecommendationItem> savedItems = itemRepo.saveAll(itemsToSave);
            savedSession.setItems(savedItems);

            return toResponse(savedSession);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("AI parse/persist failed: " + e.getMessage(), e);
        }
    }

    private static String cut(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String normalizePlatform(String raw) {
        String p = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (p.contains("CODEFORCES") || p.equals("CF")) return "CODEFORCES";
        if (p.contains("LEETCODE") || p.equals("LC"))   return "LEETCODE";
        if (p.contains("ATCODER") || p.equals("AC"))    return "ATCODER";
        if (p.contains("CODECHEF") || p.equals("CC"))   return "CODECHEF";
        return p.isBlank() ? "OTHER" : p;
    }

    /** Keeps the model's link only if it points at one of the 4 real platforms; otherwise a search link. */
    private static String sanitizeUrl(String url, String platform, String title) {
        String u = url == null ? "" : url.trim();
        if (!u.isEmpty() && u.length() <= 512) {
            try {
                URI uri = URI.create(u);
                String host = uri.getHost();
                if ("https".equalsIgnoreCase(uri.getScheme()) && host != null
                        && KNOWN_HOSTS.stream().anyMatch(h -> host.equals(h) || host.endsWith("." + h))) {
                    return u;
                }
            } catch (Exception ignored) {
                // fall through to search link
            }
        }
        return "https://www.google.com/search?q="
                + URLEncoder.encode(platform + " " + title, StandardCharsets.UTF_8);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Mapping
    // ═════════════════════════════════════════════════════════════════════════

    private AiPromptResponse toResponse(AiRecommendationSession session) {
        List<AiItem> items = session.getItems().stream()
                .filter(i -> !i.isDismissed())
                .map(i -> {
                    List<String> topics = new ArrayList<>();
                    try {
                        topics = objectMapper.readValue(
                                i.getTopicsJson() != null ? i.getTopicsJson() : "[]",
                                new TypeReference<List<String>>() {});
                    } catch (Exception ignored) {}
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
        try {
            if (session.getFocusAreasJson() != null)
                focusAreas = objectMapper.readValue(session.getFocusAreasJson(),
                        new TypeReference<List<String>>() {});
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
        try {
            return analyticsService.getAnalytics(userId);
        } catch (Exception e) {
            log.warn("Analytics for AI context: {}", e.getMessage());
            return null;
        }
    }

    private AiPromptResponse buildError(String prompt, String msg) {
        return AiPromptResponse.builder()
                .recommendations(List.of())
                .coachInsight(msg != null ? msg : "The AI service is temporarily unavailable. Please try again.")
                .focusAreas(List.of())
                .originalPrompt(prompt)
                .modelUsed("none")
                .generatedAt(LocalDateTime.now())
                .build();
    }
}