package com.codepulse.dto.response;

import com.codepulse.entity.Submission;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Data
@Builder
public class RecentSubmissionResponse {
    private Long id;
    private String problemTitle;
    private String problemUrl;
    private String platform;
    private String verdict;
    private String verdictLabel;
    private String language;
    private LocalDateTime submittedAt;
    private String timeAgo;
    private Integer difficultyRating;
    private String difficultyLabel;
    private List<String> topics;

    public static RecentSubmissionResponse from(Submission s) {
        List<String> topics = s.getProblem().getTopics().stream()
                .map(t -> t.getName()).limit(3).toList();

        return RecentSubmissionResponse.builder()
                .id(s.getId())
                .problemTitle(s.getProblem().getTitle())
                .problemUrl(s.getProblem().getProblemUrl())
                .platform(s.getProblem().getPlatform())
                .verdict(s.getVerdict().name())
                .verdictLabel(verdictLabel(s.getVerdict()))
                .language(s.getLanguage())
                .submittedAt(s.getSubmittedAt())
                .timeAgo(timeAgo(s.getSubmittedAt()))
                .difficultyRating(s.getProblem().getDifficultyRating())
                .difficultyLabel(s.getProblem().getDifficultyLabel())
                .topics(topics)
                .build();
    }

    private static String verdictLabel(Submission.Verdict v) {
        return switch (v) {
            case ACCEPTED             -> "Accepted";
            case WRONG_ANSWER         -> "Wrong Answer";
            case TIME_LIMIT_EXCEEDED  -> "TLE";
            case MEMORY_LIMIT_EXCEEDED-> "MLE";
            case RUNTIME_ERROR        -> "Runtime Error";
            case COMPILATION_ERROR    -> "Compile Error";
            case PARTIAL              -> "Partial";
            case SKIPPED              -> "Skipped";
        };
    }

    private static String timeAgo(LocalDateTime dt) {
        if (dt == null) return "";
        long mins = ChronoUnit.MINUTES.between(dt, LocalDateTime.now());
        if (mins < 60) return mins + "m ago";
        long hrs = mins / 60;
        if (hrs < 24) return hrs + "h ago";
        long days = hrs / 24;
        if (days < 30) return days + "d ago";
        return (days / 30) + "mo ago";
    }
}