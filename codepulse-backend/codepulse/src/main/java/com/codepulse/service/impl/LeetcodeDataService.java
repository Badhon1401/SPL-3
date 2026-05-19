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

    @Async
    @Transactional
    public void syncUserSubmissions(User user) {
        String handle = user.getLeetcodeHandle();
        if (handle == null || handle.isBlank()) {
            log.warn("No LeetCode handle for user {}", user.getUsername());
            return;
        }

        log.info("Syncing LeetCode submissions for handle: {}", handle);

        try {
            // Step 1: Fetch recent AC submissions (limit 20 newest)
            List<LeetCodeAcSubmission> recentAccepted = fetchRecentAcSubmissions(handle);
            if (recentAccepted.isEmpty()) {
                log.info("No AC submissions found for {}", handle);
                return;
            }

            // Step 2: For each AC submission, fetch detailed problem info
            int newCount = 0;
            for (LeetCodeAcSubmission acSub : recentAccepted) {
                String titleSlug = acSub.getTitleSlug();
                // Check if already synced by platform submission id (use LeetCode’s submission id)
                String platformId = "LC_" + acSub.getId(); // prefix to avoid collision with Codeforces
                if (submissionRepository.existsByPlatformSubmissionId(platformId)) continue;

                // Fetch problem details (difficulty, topics, url)
                LeetCodeProblemDetail detail = fetchProblemDetail(titleSlug);
                if (detail == null) continue;

                Problem problem = getOrCreateProblem(titleSlug, detail);

                Submission submission = Submission.builder()
                        .platformSubmissionId(platformId)
                        .user(user)
                        .problem(problem)
                        .verdict(Submission.Verdict.ACCEPTED) // only AC is fetched
                        .language(acSub.getLang())
                        .submittedAt(LocalDateTime.ofInstant(
                                Instant.ofEpochSecond(acSub.getTimestamp()), ZoneId.systemDefault()))
                        .build();

                submissionRepository.save(submission);
                newCount++;
            }

            log.info("Synced {} new LeetCode AC submissions for {}", newCount, handle);

        } catch (Exception e) {
            log.error("Failed to sync LeetCode data for {}: {}", handle, e.getMessage());
        }
    }

    // ─── GraphQL queries ────────────────────────────────────────────

    private List<LeetCodeAcSubmission> fetchRecentAcSubmissions(String username) {
        String query = """
        query recentAcSubmissions($username: String!, $limit: Int) {
          recentAcSubmissionList(username: $username, limit: $limit) {
            id
            title
            titleSlug
            timestamp
            lang
          }
        }""";

        Map<String, Object> variables = Map.of("username", username, "limit", 20);

        LeetCodeGraphQLResponse<RecentAcWrapper> response = webClientBuilder.build()
                .post()
                .uri(leetcodeBaseUrl)
                .bodyValue(Map.of("query", query, "variables", variables))
                .retrieve()
                .bodyToMono(LeetCodeGraphQLResponse.class)
                .block();

        if (response != null && response.getData() != null
                && response.getData().getRecentAcSubmissionList() != null) {
            return response.getData().getRecentAcSubmissionList();
        }
        return List.of();
    }

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
                .bodyToMono(LeetCodeGraphQLResponse.class)
                .block();

        if (response != null && response.getData() != null) {
            return response.getData().getQuestion();
        }
        return null;
    }

    // ─── Problem creation ──────────────────────────────────────────

    private Problem getOrCreateProblem(String titleSlug, LeetCodeProblemDetail detail) {
        return problemRepository.findByPlatformIdAndPlatform(titleSlug, "LEETCODE")
                .orElseGet(() -> {
                    Problem p = Problem.builder()
                            .platformId(titleSlug)
                            .platform("LEETCODE")
                            .title(detail.getTitle())
                            .difficultyLabel(detail.getDifficulty()) // Easy/Medium/Hard
                            .problemUrl("https://leetcode.com/problems/" + titleSlug + "/")
                            .topics(detail.getTopicTags().stream()
                                    .map(tag -> topicRepository.findBySlug(tag.getSlug())
                                            .orElseGet(() -> topicRepository.save(
                                                    Topic.builder().name(tag.getName()).slug(tag.getSlug()).build())))
                                    .collect(Collectors.toList()))
                            .build();
                    return problemRepository.save(p);
                });
    }

    // ─── POJOs ─────────────────────────────────────────────────────

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class LeetCodeGraphQLResponse<T> {
        private T data;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class RecentAcWrapper {
        private List<LeetCodeAcSubmission> recentAcSubmissionList;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class LeetCodeAcSubmission {
        private String id;
        private String title;
        private String titleSlug;
        private long timestamp;
        private String lang;
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