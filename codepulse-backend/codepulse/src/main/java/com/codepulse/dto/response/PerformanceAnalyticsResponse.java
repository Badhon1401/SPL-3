package com.codepulse.dto.response;
import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data @Builder
public class PerformanceAnalyticsResponse {
    // Combined totals across all 4 platforms
    private long totalSubmissions;
    private long acceptedSubmissions;
    private long uniqueProblemsSolved;
    private double acceptanceRate;

    // Streak (combined across all platforms)
    private long currentStreak;
    private long longestStreak;
    private long totalActiveDays;

    // CodePulse unified rating
    private int combinedRating;
    private String ratingTier;
    private Map<String, Integer> platformRatings;  // per-platform estimated rating

    // Combined topic coverage
    private Map<String, Long> topicBreakdown;
    private Map<String, Double> weaknessScores;
    private Map<String, Long> difficultyBreakdown;

    // Activity heatmap (all platforms, yyyy-MM-dd -> count)
    private Map<String, Long> activityHeatmap;

    // Combined verdict distribution (normalised labels)
    private Map<String, Long> verdictDistribution;

    // CF-only rating trend
    private List<RatingPoint> ratingTrend;

    // Per-platform breakdown
    private Map<String, PlatformStats> platformBreakdown;

    @Data @Builder
    public static class RatingPoint {
        private String date;
        private int rating;
    }

    @Data @Builder
    public static class PlatformStats {
        private long totalSubmissions;
        private long acceptedCount;
        private long uniqueSolved;
        private double acceptanceRate;
        private Map<String, Long> topicBreakdown;
        private Map<String, Long> verdictDistribution;
    }
}