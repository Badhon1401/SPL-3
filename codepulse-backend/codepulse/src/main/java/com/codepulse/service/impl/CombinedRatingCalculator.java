package com.codepulse.service.impl;

import com.codepulse.entity.Submission;
import com.codepulse.entity.User;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Calculates a unified "CodePulse Rating" by combining performance signals
 * from Codeforces, LeetCode, AtCoder, and CodeChef into a single Elo-like number.
 *
 * Algorithm:
 *   1. Estimate a rating for each connected platform.
 *   2. Weight each platform by sqrt(submission count on that platform).
 *   3. Final = weighted average.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CombinedRatingCalculator {

    private final WebClient.Builder webClientBuilder;

    @Value("${codeforces.api.base-url}")
    private String cfBaseUrl;

    public record CombinedRating(
            int overall,
            String tier,
            Map<String, Integer> perPlatform,
            Map<String, Long> submissionCounts
    ) {}

    public CombinedRating calculate(User user, List<Submission> allSubmissions) {
        Map<String, Long> counts = countByPlatform(allSubmissions);

        Map<String, Integer> ratings = new java.util.LinkedHashMap<>();

        // ── Codeforces ────────────────────────────────────────────────────────
        if (user.getCodeforcesHandle() != null) {
            int cfR = fetchCfRating(user.getCodeforcesHandle());
            if (cfR > 0) ratings.put("CODEFORCES", cfR);
            else         ratings.put("CODEFORCES", estimateFromSubs(allSubmissions, "CODEFORCES"));
        }

        // ── LeetCode ──────────────────────────────────────────────────────────
        if (user.getLeetcodeHandle() != null) {
            ratings.put("LEETCODE", estimateLcRating(allSubmissions));
        }

        // ── AtCoder ───────────────────────────────────────────────────────────
        if (user.getAtcoderHandle() != null) {
            ratings.put("ATCODER", estimateAcRating(allSubmissions));
        }

        // ── CodeChef ──────────────────────────────────────────────────────────
        if (user.getCodechefHandle() != null) {
            ratings.put("CODECHEF", estimateCcRating(allSubmissions));
        }

        if (ratings.isEmpty()) return new CombinedRating(800, "Beginner", ratings, counts);

        // Weighted average: weight = sqrt(submission count on platform)
        double weightedSum = 0;
        double totalWeight = 0;
        for (Map.Entry<String, Integer> e : ratings.entrySet()) {
            double w = Math.sqrt(counts.getOrDefault(e.getKey(), 1L));
            weightedSum += e.getValue() * w;
            totalWeight += w;
        }

        int overall = (int) Math.round(weightedSum / totalWeight);
        overall = Math.max(800, Math.min(overall, 3800));

        return new CombinedRating(overall, tier(overall), ratings, counts);
    }

    // ─── Per-platform estimators ──────────────────────────────────────────────

    private int fetchCfRating(String handle) {
        try {
            CfRatingResponse resp = webClientBuilder.build()
                    .get()
                    .uri(cfBaseUrl + "/user.rating?handle=" + handle)
                    .retrieve()
                    .bodyToMono(CfRatingResponse.class)
                    .block();
            if (resp == null || !"OK".equals(resp.getStatus()) || resp.getResult().isEmpty()) return 0;
            Object last = resp.getResult().get(resp.getResult().size() - 1);
            if (last instanceof Map<?,?> m) {
                Object nr = m.get("newRating");
                if (nr instanceof Number n) return n.intValue();
            }
            return 0;
        } catch (Exception e) {
            log.warn("CF rating fetch failed: {}", e.getMessage());
            return 0;
        }
    }

    /** LC has Easy/Medium/Hard labels — convert to rating points */
    private int estimateLcRating(List<Submission> subs) {
        long easy   = subs.stream().filter(s -> "LEETCODE".equals(s.getProblem().getPlatform())
                && s.getVerdict() == Submission.Verdict.ACCEPTED
                && "Easy".equalsIgnoreCase(s.getProblem().getDifficultyLabel())).count();
        long medium = subs.stream().filter(s -> "LEETCODE".equals(s.getProblem().getPlatform())
                && s.getVerdict() == Submission.Verdict.ACCEPTED
                && "Medium".equalsIgnoreCase(s.getProblem().getDifficultyLabel())).count();
        long hard   = subs.stream().filter(s -> "LEETCODE".equals(s.getProblem().getPlatform())
                && s.getVerdict() == Submission.Verdict.ACCEPTED
                && "Hard".equalsIgnoreCase(s.getProblem().getDifficultyLabel())).count();

        // LC acceptance rate bonus
        long total  = subs.stream().filter(s -> "LEETCODE".equals(s.getProblem().getPlatform())).count();
        long ac     = subs.stream().filter(s -> "LEETCODE".equals(s.getProblem().getPlatform())
                && s.getVerdict() == Submission.Verdict.ACCEPTED).count();
        double arBonus = total > 0 ? ((double) ac / total) * 100 : 0;

        int rating = (int)(1200 + (easy * 2) + (medium * 9) + (hard * 22) + arBonus);
        return Math.min(rating, 3200);
    }

    /** AtCoder: average difficulty of solved problems */
    private int estimateAcRating(List<Submission> subs) {
        OptionalDouble avg = subs.stream()
                .filter(s -> "ATCODER".equals(s.getProblem().getPlatform())
                        && s.getVerdict() == Submission.Verdict.ACCEPTED
                        && s.getProblem().getDifficultyRating() != null)
                .mapToInt(s -> s.getProblem().getDifficultyRating())
                .average();
        if (avg.isEmpty()) {
            long solved = subs.stream().filter(s -> "ATCODER".equals(s.getProblem().getPlatform())
                    && s.getVerdict() == Submission.Verdict.ACCEPTED).count();
            return (int)(800 + Math.min(solved * 8, 1600));
        }
        return (int)(avg.getAsDouble() * 1.05); // slight upward adjustment
    }

    /** CodeChef: estimate from difficulty labels */
    private int estimateCcRating(List<Submission> subs) {
        long expert  = subs.stream().filter(s -> "CODECHEF".equals(s.getProblem().getPlatform())
                && s.getVerdict() == Submission.Verdict.ACCEPTED
                && "Expert".equalsIgnoreCase(s.getProblem().getDifficultyLabel())).count();
        long hard    = subs.stream().filter(s -> "CODECHEF".equals(s.getProblem().getPlatform())
                && s.getVerdict() == Submission.Verdict.ACCEPTED
                && "Hard".equalsIgnoreCase(s.getProblem().getDifficultyLabel())).count();
        long medium  = subs.stream().filter(s -> "CODECHEF".equals(s.getProblem().getPlatform())
                && s.getVerdict() == Submission.Verdict.ACCEPTED
                && "Medium".equalsIgnoreCase(s.getProblem().getDifficultyLabel())).count();
        long easy    = subs.stream().filter(s -> "CODECHEF".equals(s.getProblem().getPlatform())
                && s.getVerdict() == Submission.Verdict.ACCEPTED).count() - expert - hard - medium;

        int rating = (int)(1000 + (easy * 3) + (medium * 10) + (hard * 20) + (expert * 35));
        return Math.min(rating, 3000);
    }

    /** Fallback: estimate from solved difficulty ratings */
    private int estimateFromSubs(List<Submission> subs, String platform) {
        OptionalDouble avg = subs.stream()
                .filter(s -> platform.equals(s.getProblem().getPlatform())
                        && s.getVerdict() == Submission.Verdict.ACCEPTED
                        && s.getProblem().getDifficultyRating() != null)
                .mapToInt(s -> s.getProblem().getDifficultyRating())
                .average();
        return (int) avg.orElse(1000);
    }

    private Map<String, Long> countByPlatform(List<Submission> subs) {
        Map<String, Long> m = new java.util.HashMap<>();
        subs.forEach(s -> m.merge(s.getProblem().getPlatform(), 1L, Long::sum));
        return m;
    }

    public static String tier(int r) {
        if (r < 1000) return "Beginner";
        if (r < 1200) return "Pupil";
        if (r < 1400) return "Apprentice";
        if (r < 1600) return "Specialist";
        if (r < 1900) return "Expert";
        if (r < 2100) return "Candidate Master";
        if (r < 2400) return "Master";
        if (r < 2600) return "International Master";
        if (r < 3000) return "Grandmaster";
        return "Legendary Grandmaster";
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    static class CfRatingResponse {
        private String status;
        private List<Object> result;
    }
}