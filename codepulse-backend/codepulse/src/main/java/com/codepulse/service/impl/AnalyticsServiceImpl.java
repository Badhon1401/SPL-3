package com.codepulse.service.impl;

import com.codepulse.dto.response.PerformanceAnalyticsResponse;
import com.codepulse.dto.response.PerformanceAnalyticsResponse.*;
import com.codepulse.entity.Submission;
import com.codepulse.entity.User;
import com.codepulse.repository.SubmissionRepository;
import com.codepulse.service.AnalyticsService;
import com.codepulse.service.UserService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * FIX: Added @Transactional(readOnly = true) on getAnalytics().
 *
 * Root cause of the LazyInitializationException in AnalyticsServiceImpl:
 *   CombinedRatingCalculator.countByPlatform() iterated over the submissions list
 *   and called s.getProblem().getPlatform() AFTER the JPA session had already
 *   closed (because there was no @Transactional around getAnalytics()).
 *
 * With spring.jpa.open-in-view=false, the session lifetime equals the
 * repository method call — it closes the moment findByUserId() returns.
 *
 * Two-pronged fix applied:
 *   1. SubmissionRepository.findByUserId() now uses JOIN FETCH to eagerly
 *      load Problem and Topics in the same SQL query.
 *   2. @Transactional(readOnly=true) here keeps the session open as a
 *      safety net for any subsequent lazy access that JOIN FETCH might miss
 *      (e.g., nested collections resolved later in the method).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsServiceImpl implements AnalyticsService {

    private final SubmissionRepository submissionRepository;
    private final UserService userService;
    private final CodeforcesDataService codeforcesDataService;
    private final LeetcodeDataService leetcodeDataService;
    private final AtCoderDataService atCoderDataService;
    private final CodeChefDataService codeChefDataService;
    private final SubmissionPruningService pruningService;
    private final CombinedRatingCalculator ratingCalculator;
    private final WebClient.Builder webClientBuilder;

    @Value("${codeforces.api.base-url}")
    private String cfBaseUrl;

    private static final Map<String, String> VERDICT_LABEL = Map.of(
            "ACCEPTED",              "Accepted",
            "WRONG_ANSWER",          "Wrong Answer",
            "TIME_LIMIT_EXCEEDED",   "Time Limit Exceeded",
            "MEMORY_LIMIT_EXCEEDED", "Memory Limit Exceeded",
            "RUNTIME_ERROR",         "Runtime Error",
            "COMPILATION_ERROR",     "Compilation Error",
            "PARTIAL",               "Partial",
            "SKIPPED",               "Skipped"
    );

    @Override
    @Transactional(readOnly = true)   // ← THE KEY FIX: session stays open
    public PerformanceAnalyticsResponse getAnalytics(Long userId) {
        User user = userService.getUserById(userId);

        // findByUserId uses JOIN FETCH — Problem + Topics are fully initialised
        List<Submission> all = submissionRepository.findByUserId(userId);

        long total    = all.size();
        long accepted = all.stream()
                .filter(s -> s.getVerdict() == Submission.Verdict.ACCEPTED)
                .count();
        long unique = submissionRepository.countDistinctAcceptedProblemsByUserId(userId);
        double rate = total == 0 ? 0.0
                : Math.round((double) accepted / total * 10000.0) / 100.0;

        long[] streaks = calculateStreaks(all);

        // CombinedRatingCalculator calls s.getProblem().getPlatform() etc.
        // Problem is JOIN FETCH-ed → no lazy proxy issue.
        CombinedRatingCalculator.CombinedRating cr = ratingCalculator.calculate(user, all);

        // Combined topic breakdown (accepted only)
        Map<String, Long> topicBreakdown = new LinkedHashMap<>();
        all.stream()
                .filter(s -> s.getVerdict() == Submission.Verdict.ACCEPTED)
                .forEach(s -> s.getProblem().getTopics()
                        .forEach(t -> topicBreakdown.merge(t.getName(), 1L, Long::sum)));

        Map<String, Double> weaknessScores  = computeWeaknessScores(all);
        Map<String, Long>   diffBreakdown   = buildDifficultyBreakdown(all);
        Map<String, Long>   activityHeatmap = buildActivityHeatmap(all);
        Map<String, Long>   verdictDist     = buildVerdictDistribution(all);
        Map<String, PlatformStats> platformBreakdown = buildPlatformBreakdown(all);
        List<RatingPoint> ratingTrend = fetchCfRatingTrend(user);

        return PerformanceAnalyticsResponse.builder()
                .totalSubmissions(total)
                .acceptedSubmissions(accepted)
                .uniqueProblemsSolved(unique)
                .acceptanceRate(rate)
                .currentStreak(streaks[0])
                .longestStreak(streaks[1])
                .totalActiveDays(streaks[2])
                .combinedRating(cr.overall())
                .ratingTier(cr.tier())
                .platformRatings(cr.perPlatform())
                .topicBreakdown(topicBreakdown)
                .weaknessScores(weaknessScores)
                .difficultyBreakdown(diffBreakdown)
                .activityHeatmap(activityHeatmap)
                .verdictDistribution(verdictDist)
                .ratingTrend(ratingTrend)
                .platformBreakdown(platformBreakdown)
                .build();
    }

    @Override
    public void syncAndRefresh(Long userId) {
        User user = userService.getUserById(userId);
        if (user.getCodeforcesHandle() != null) codeforcesDataService.syncUserSubmissions(user);
        if (user.getLeetcodeHandle()   != null) leetcodeDataService.syncUserSubmissions(user);
        if (user.getAtcoderHandle()    != null) atCoderDataService.syncUserSubmissions(user);
        if (user.getCodechefHandle()   != null) codeChefDataService.syncUserSubmissions(user);
        pruningService.pruneIfNeeded(userId);
        user.setLastSyncedAt(LocalDateTime.now());
    }

    // ─── private helpers ─────────────────────────────────────────────────────

    private long[] calculateStreaks(List<Submission> submissions) {
        Set<LocalDate> days = submissions.stream()
                .filter(s -> s.getSubmittedAt() != null)
                .map(s -> s.getSubmittedAt().toLocalDate())
                .collect(Collectors.toSet());
        if (days.isEmpty()) return new long[]{0, 0, 0};

        List<LocalDate> sorted = days.stream().sorted().toList();
        long longest = 1, temp = 1;
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).equals(sorted.get(i - 1).plusDays(1))) {
                temp++;
                longest = Math.max(longest, temp);
            } else {
                temp = 1;
            }
        }
        long current = 0;
        LocalDate check = LocalDate.now();
        if (!days.contains(check)) check = check.minusDays(1);
        while (days.contains(check)) { current++; check = check.minusDays(1); }
        return new long[]{current, longest, days.size()};
    }

    private Map<String, Double> computeWeaknessScores(List<Submission> submissions) {
        Map<String, Long> total  = new HashMap<>();
        Map<String, Long> failed = new HashMap<>();
        for (Submission s : submissions) {
            s.getProblem().getTopics().forEach(t -> {
                total.merge(t.getName(), 1L, Long::sum);
                if (s.getVerdict() != Submission.Verdict.ACCEPTED)
                    failed.merge(t.getName(), 1L, Long::sum);
            });
        }
        Map<String, Double> scores = new LinkedHashMap<>();
        total.forEach((topic, cnt) -> {
            long f = failed.getOrDefault(topic, 0L);
            scores.put(topic, cnt == 0 ? 0.0 : Math.round((double) f / cnt * 100.0) / 100.0);
        });
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (e1, e2) -> e1, LinkedHashMap::new));
    }

    private Map<String, Long> buildDifficultyBreakdown(List<Submission> all) {
        return all.stream()
                .filter(s -> s.getVerdict() == Submission.Verdict.ACCEPTED)
                .collect(Collectors.groupingBy(
                        s -> normaliseDifficulty(s.getProblem()),
                        Collectors.counting()));
    }

    private Map<String, Long> buildActivityHeatmap(List<Submission> all) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return all.stream()
                .filter(s -> s.getSubmittedAt() != null
                        && s.getSubmittedAt().isAfter(LocalDateTime.now().minusDays(365)))
                .collect(Collectors.groupingBy(
                        s -> s.getSubmittedAt().format(fmt),
                        Collectors.counting()));
    }

    private Map<String, Long> buildVerdictDistribution(List<Submission> all) {
        return all.stream()
                .collect(Collectors.groupingBy(
                        s -> VERDICT_LABEL.getOrDefault(s.getVerdict().name(), s.getVerdict().name()),
                        Collectors.counting()));
    }

    private Map<String, PlatformStats> buildPlatformBreakdown(List<Submission> all) {
        Map<String, List<Submission>> byPlatform = all.stream()
                .collect(Collectors.groupingBy(s -> s.getProblem().getPlatform()));
        Map<String, PlatformStats> result = new LinkedHashMap<>();
        byPlatform.forEach((platform, subs) -> {
            long tot  = subs.size();
            long ac   = subs.stream().filter(s -> s.getVerdict() == Submission.Verdict.ACCEPTED).count();
            long uniq = subs.stream()
                    .filter(s -> s.getVerdict() == Submission.Verdict.ACCEPTED)
                    .map(s -> s.getProblem().getId()).distinct().count();
            double r  = tot == 0 ? 0.0 : Math.round((double) ac / tot * 10000.0) / 100.0;
            Map<String, Long> topics = new LinkedHashMap<>();
            subs.stream().filter(s -> s.getVerdict() == Submission.Verdict.ACCEPTED)
                    .forEach(s -> s.getProblem().getTopics()
                            .forEach(t -> topics.merge(t.getName(), 1L, Long::sum)));
            Map<String, Long> verdicts = subs.stream().collect(Collectors.groupingBy(
                    s -> VERDICT_LABEL.getOrDefault(s.getVerdict().name(), s.getVerdict().name()),
                    Collectors.counting()));
            result.put(platform, PlatformStats.builder()
                    .totalSubmissions(tot).acceptedCount(ac).uniqueSolved(uniq)
                    .acceptanceRate(r).topicBreakdown(topics).verdictDistribution(verdicts)
                    .build());
        });
        return result;
    }

    private String normaliseDifficulty(com.codepulse.entity.Problem p) {
        if (p.getDifficultyLabel() != null)
            return switch (p.getDifficultyLabel()) {
                case "Easy"   -> "Easy";
                case "Medium" -> "Medium";
                case "Hard"   -> "Hard";
                default       -> "Medium";
            };
        Integer r = p.getDifficultyRating();
        if (r == null) return "Unknown";
        if (r < 1200) return "Beginner";
        if (r < 1600) return "Easy";
        if (r < 2100) return "Medium";
        if (r < 2400) return "Hard";
        return "Expert";
    }

    private List<RatingPoint> fetchCfRatingTrend(User user) {
        try {
            if (user.getCodeforcesHandle() == null) return List.of();
            CfRatingResponse resp = webClientBuilder.build()
                    .get()
                    .uri(cfBaseUrl + "/user.rating?handle=" + user.getCodeforcesHandle())
                    .retrieve()
                    .bodyToMono(CfRatingResponse.class)
                    .block();
            if (resp == null || !"OK".equals(resp.getStatus())) return List.of();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            return resp.getResult().stream()
                    .map(e -> RatingPoint.builder()
                            .date(LocalDateTime.ofEpochSecond(
                                    ((Number) e.get("ratingUpdateTimeSeconds")).longValue(),
                                    0, java.time.ZoneOffset.UTC).format(fmt))
                            .rating(((Number) e.get("newRating")).intValue())
                            .build())
                    .toList();
        } catch (Exception e) {
            log.warn("CF rating trend fetch failed: {}", e.getMessage());
            return List.of();
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class CfRatingResponse {
        private String status;
        private List<Map<String, Object>> result;
    }
}