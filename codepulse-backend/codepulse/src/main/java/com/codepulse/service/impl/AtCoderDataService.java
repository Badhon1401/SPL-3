package com.codepulse.service.impl;

import com.codepulse.entity.Problem;
import com.codepulse.entity.Submission;
import com.codepulse.entity.Topic;
import com.codepulse.entity.User;
import com.codepulse.repository.ProblemRepository;
import com.codepulse.repository.SubmissionRepository;
import com.codepulse.repository.TopicRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class AtCoderDataService {

    private final WebClient.Builder webClientBuilder;
    private final SubmissionRepository submissionRepository;
    private final ProblemRepository problemRepository;
    private final TopicRepository topicRepository;

    @Value("${atcoder.api.base-url}")
    private String atcoderBaseUrl;

    @Value("${atcoder.difficulties.url}")
    private String difficultiesUrl;

    /** Cached difficulty map: problemId → difficulty model */
    private final Map<String, AtCoderDifficulty> difficultyCache = new HashMap<>();

    @Async
    @Transactional
    public void syncUserSubmissions(User user) {
        String handle = user.getAtcoderHandle();
        if (handle == null || handle.isBlank()) return;

        log.info("Syncing AtCoder submissions for handle: {}", handle);
        ensureDifficultyCacheLoaded();

        try {
            List<AtCoderSubmission> subs = webClientBuilder.build()
                    .get()
                    .uri(atcoderBaseUrl + "/atcoder-api/v3/user/submissions?user=" + handle + "&from_second=0")
                    .header("Accept-Encoding", "gzip")
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<AtCoderSubmission>>() {})
                    .block();

            if (subs == null) return;

            int newCount = 0;
            for (AtCoderSubmission s : subs) {
                String platformId = "AC_" + s.getId();
                if (submissionRepository.existsByPlatformSubmissionId(platformId)) continue;

                Problem problem = getOrCreateProblem(s);
                Submission submission = Submission.builder()
                        .platformSubmissionId(platformId)
                        .user(user)
                        .problem(problem)
                        .verdict(mapVerdict(s.getResult()))
                        .language(s.getLanguage())
                        .timeConsumedMillis(s.getExecutionTime())
                        .submittedAt(LocalDateTime.ofInstant(
                                Instant.ofEpochSecond(s.getEpochSecond()),
                                ZoneId.systemDefault()))
                        .build();

                submissionRepository.save(submission);
                newCount++;
            }
            log.info("Synced {} new AtCoder submissions for {}", newCount, handle);

        } catch (Exception e) {
            log.error("AtCoder sync failed for {}: {}", handle, e.getMessage());
        }
    }

    private void ensureDifficultyCacheLoaded() {
        if (!difficultyCache.isEmpty()) return;
        try {
            Map<String, AtCoderDifficulty> map = webClientBuilder.build()
                    .get()
                    .uri(difficultiesUrl)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, AtCoderDifficulty>>() {})
                    .block();
            if (map != null) {
                difficultyCache.putAll(map);
                log.info("Loaded {} AtCoder difficulty models", map.size());
            }
        } catch (Exception e) {
            log.warn("Could not load AtCoder difficulties: {}", e.getMessage());
        }
    }

    private Problem getOrCreateProblem(AtCoderSubmission s) {
        String platformId = s.getProblemId();
        return problemRepository.findByPlatformIdAndPlatform(platformId, "ATCODER")
                .orElseGet(() -> {
                    Integer rating = null;
                    AtCoderDifficulty diff = difficultyCache.get(platformId);
                    if (diff != null && diff.getDifficulty() != null) {
                        rating = diff.getDifficulty().intValue();
                    }
                    String contestId = s.getContestId();
                    Problem p = Problem.builder()
                            .platformId(platformId)
                            .platform("ATCODER")
                            .title(platformId) // no title in submission response
                            .difficultyRating(rating)
                            .problemUrl("https://atcoder.jp/contests/" + contestId + "/tasks/" + platformId)
                            .topics(inferAtCoderTopics(contestId, rating))
                            .build();
                    return problemRepository.save(p);
                });
    }

    /** Infer rough topics from contest name and difficulty */
    private List<Topic> inferAtCoderTopics(String contestId, Integer rating) {
        List<Topic> topics = new ArrayList<>();
        if (contestId == null) return topics;

        String slug = "competitive-programming";
        if (contestId.startsWith("arc")) slug = "advanced-algorithm";
        else if (contestId.startsWith("agc")) slug = "graph";

        String finalSlug = slug;
        Topic t = topicRepository.findBySlug(finalSlug)
                .orElseGet(() -> topicRepository.save(
                        Topic.builder().name(finalSlug.replace("-", " ")).slug(finalSlug).build()));
        topics.add(t);
        return topics;
    }

    private Submission.Verdict mapVerdict(String result) {
        if (result == null) return Submission.Verdict.SKIPPED;
        return switch (result) {
            case "AC"  -> Submission.Verdict.ACCEPTED;
            case "WA"  -> Submission.Verdict.WRONG_ANSWER;
            case "TLE" -> Submission.Verdict.TIME_LIMIT_EXCEEDED;
            case "MLE" -> Submission.Verdict.MEMORY_LIMIT_EXCEEDED;
            case "RE"  -> Submission.Verdict.RUNTIME_ERROR;
            case "CE"  -> Submission.Verdict.COMPILATION_ERROR;
            default    -> Submission.Verdict.SKIPPED;
        };
    }

    // ─── POJOs ────────────────────────────────────────────────────────────────

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    static class AtCoderSubmission {
        private long id;
        @JsonProperty("epoch_second") private long epochSecond;
        @JsonProperty("problem_id")   private String problemId;
        @JsonProperty("contest_id")   private String contestId;
        private String language;
        private String result;
        @JsonProperty("execution_time") private Integer executionTime;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    static class AtCoderDifficulty {
        private Double difficulty;
    }
}