package com.codepulse.service.impl;

import com.codepulse.entity.Problem;
import com.codepulse.entity.Submission;
import com.codepulse.entity.Topic;
import com.codepulse.entity.User;
import com.codepulse.repository.ProblemRepository;
import com.codepulse.repository.SubmissionRepository;
import com.codepulse.repository.TopicRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeetcodeDataService {

    private final WebClient.Builder webClientBuilder;
    private final SubmissionRepository submissionRepository;
    private final ProblemRepository problemRepository;
    private final TopicRepository topicRepository;

    @Value("${leetcode.api.base-url}")
    private String leetcodeBaseUrl;

    @Value("${leetcode.max-submissions}")
    private int maxSubmissions;

    @Async
    @Transactional
    public void syncUserSubmissions(User user) {
        String handle = user.getLeetcodeHandle();
        if (handle == null || handle.isBlank()) {
            log.warn("No LeetCode handle for user {}", user.getUsername());
            return;
        }

        log.info("Syncing LeetCode submissions for handle: {} (max {})", handle, maxSubmissions);

        try {
            // Fetch recent submissions (all verdicts) with pagination, capped at maxSubmissions
            List<LeetCodeSubmission> recentSubs = fetchRecentSubmissions(handle, maxSubmissions);
            if (recentSubs.isEmpty()) {
                log.info("No submissions found for {}", handle);
                return;
            }

            int newCount = 0;
            for (LeetCodeSubmission lcSub : recentSubs) {
                String titleSlug = lcSub.getTitleSlug();
                String platformId = "LC_" + lcSub.getId();
                if (submissionRepository.existsByPlatformSubmissionId(platformId)) continue;

                // Fetch problem details (difficulty, topics, URL)
                LeetCodeProblemDetail detail = fetchProblemDetail(titleSlug);
                if (detail == null) continue;

                Problem problem = getOrCreateProblem(titleSlug, detail);

                Submission submission = Submission.builder()
                        .platformSubmissionId(platformId)
                        .user(user)
                        .problem(problem)
                        .verdict(mapLeetCodeVerdict(lcSub.getStatus()))
                        .language(lcSub.getLang())
                        .submittedAt(LocalDateTime.ofInstant(
                                Instant.ofEpochSecond(lcSub.getTimestamp()),
                                ZoneId.systemDefault()))
                        .build();

                submissionRepository.save(submission);
                newCount++;
            }

            log.info("Synced {} new LeetCode submissions for {}", newCount, handle);

        } catch (Exception e) {
            log.error("Failed to sync LeetCode data for {}: {}", handle, e.getMessage());
        }
    }

    private List<LeetCodeSubmission> fetchRecentSubmissions(String username, int max) {
        int requestLimit = Math.min(max, 200);

        String query = """
        query recentSubmissions($username: String!, $limit: Int) {
            recentSubmissionList(username: $username, limit: $limit) {
                id
                title
                titleSlug
                timestamp
                lang
                status
            }
        }""";

        Map<String, Object> variables = Map.of(
                "username", username,
                "limit", requestLimit
        );

        LeetCodeGraphQLResponse<RecentSubmissionWrapper> response = webClientBuilder.build()
                .post()
                .uri(leetcodeBaseUrl)
                .header("Referer", "https://leetcode.com")
                .header("Content-Type", "application/json")
                .bodyValue(Map.of("query", query, "variables", variables))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<LeetCodeGraphQLResponse<RecentSubmissionWrapper>>() {})
                .block();

        if (response != null && response.getData() != null
                && response.getData().getRecentSubmissionList() != null) {

            return response.getData().getRecentSubmissionList()
                    .stream()
                    .limit(max)
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    // ─── Fetch problem detail ─────────────────────────

    private LeetCodeProblemDetail fetchProblemDetail(String titleSlug) {
        String query = """
            query problemDetail($titleSlug: String!) {
                question(titleSlug: $titleSlug) {
                    questionId
                    title
                    titleSlug
                    difficulty
                    topicTags {
                        name
                        slug
                    }
                }
            }""";

        Map<String, Object> variables = Map.of("titleSlug", titleSlug);

        LeetCodeGraphQLResponse<QuestionWrapper> response = webClientBuilder.build()
                .post()
                .uri(leetcodeBaseUrl)
                .bodyValue(Map.of("query", query, "variables", variables))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<LeetCodeGraphQLResponse<QuestionWrapper>>() {})
                .block();

        if (response != null && response.getData() != null) {
            return response.getData().getQuestion();
        }
        return null;
    }

    // ─── Topic resolution ─────────────────────────────

    private Topic resolveTopic(String name, String slug) {
        Optional<Topic> bySlug = topicRepository.findBySlug(slug);
        if (bySlug.isPresent()) return bySlug.get();

        Optional<Topic> byName = topicRepository.findByName(name);
        if (byName.isPresent()) return byName.get();

        Topic newTopic = Topic.builder()
                .name(name)
                .slug(slug)
                .build();
        return topicRepository.save(newTopic);
    }

    // ─── Problem creation ─────────────────────────────

    private Problem getOrCreateProblem(String titleSlug, LeetCodeProblemDetail detail) {
        return problemRepository.findByPlatformIdAndPlatform(titleSlug, "LEETCODE")
                .orElseGet(() -> {
                    Problem p = Problem.builder()
                            .platformId(titleSlug)
                            .platform("LEETCODE")
                            .title(detail.getTitle())
                            .difficultyLabel(detail.getDifficulty())
                            .problemUrl("https://leetcode.com/problems/" + titleSlug + "/")
                            .topics(detail.getTopicTags().stream()
                                    .map(tag -> resolveTopic(tag.getName(), tag.getSlug()))
                                    .collect(Collectors.toList()))
                            .build();
                    return problemRepository.save(p);
                });
    }

    // ─── Verdict mapping (new helper) ─────────────────────────────

    private Submission.Verdict mapLeetCodeVerdict(String leetcodeStatus) {
        if (leetcodeStatus == null) return Submission.Verdict.SKIPPED;
        return switch (leetcodeStatus) {
            case "Accepted" -> Submission.Verdict.ACCEPTED;
            case "Wrong Answer" -> Submission.Verdict.WRONG_ANSWER;
            case "Time Limit Exceeded" -> Submission.Verdict.TIME_LIMIT_EXCEEDED;
            case "Memory Limit Exceeded" -> Submission.Verdict.MEMORY_LIMIT_EXCEEDED;
            case "Runtime Error", "Output Limit Exceeded" -> Submission.Verdict.RUNTIME_ERROR;
            case "Compile Error" -> Submission.Verdict.COMPILATION_ERROR;
            default -> Submission.Verdict.SKIPPED;
        };
    }

    // ─── POJOs ────────────────────────────────────────

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class LeetCodeGraphQLResponse<T> {
        private T data;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class RecentSubmissionWrapper {
        private List<LeetCodeSubmission> recentSubmissionList;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class LeetCodeSubmission {
        private String id;
        private String title;
        private String titleSlug;
        private long timestamp;
        private String lang;
        private String status;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class QuestionWrapper {
        private LeetCodeProblemDetail question;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class LeetCodeProblemDetail {
        private String questionId;
        private String title;
        private String titleSlug;
        private String difficulty;
        private List<TopicTag> topicTags;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class TopicTag {
        private String name;
        private String slug;
    }
}