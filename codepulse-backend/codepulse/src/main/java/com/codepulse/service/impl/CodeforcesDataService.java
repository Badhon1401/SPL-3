package com.codepulse.service.impl;

import com.codepulse.entity.Problem;
import com.codepulse.entity.Submission;
import com.codepulse.entity.Topic;
import com.codepulse.entity.User;
import com.codepulse.exception.BadRequestException;
import com.codepulse.repository.ProblemRepository;
import com.codepulse.repository.SubmissionRepository;
import com.codepulse.repository.TopicRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CodeforcesDataService {

    private final WebClient.Builder webClientBuilder;
    private final SubmissionRepository submissionRepository;
    private final ProblemRepository problemRepository;
    private final TopicRepository topicRepository;

    @Value("${codeforces.api.base-url}")
    private String cfBaseUrl;

    @Async
    @Transactional
    public void syncUserSubmissions(User user) {
        String handle = user.getCodeforcesHandle();
        if (handle == null || handle.isBlank()) {
            throw new BadRequestException("Codeforces handle not set for user");
        }

        log.info("Syncing Codeforces submissions for handle: {}", handle);

        try {
            CfResponse response = webClientBuilder.build()
                    .get()
                    .uri(cfBaseUrl + "/user.status?handle=" + handle + "&from=1&count=200")
                    .retrieve()
                    .bodyToMono(CfResponse.class)
                    .block();

            if (response == null || !"OK".equals(response.getStatus())) {
                log.error("Codeforces API returned error for handle: {}", handle);
                return;
            }

            int newCount = 0;
            for (CfSubmission cfSub : response.getResult()) {
                String platformId = String.valueOf(cfSub.getId());
                if (submissionRepository.existsByPlatformSubmissionId(platformId)) continue;

                Problem problem = getOrCreateProblem(cfSub);
                Submission submission = Submission.builder()
                        .platformSubmissionId(platformId)
                        .user(user)
                        .problem(problem)
                        .verdict(mapVerdict(cfSub.getVerdict()))
                        .language(cfSub.getProgrammingLanguage())
                        .timeConsumedMillis(cfSub.getTimeConsumedMillis())
                        .memoryConsumedBytes(cfSub.getMemoryConsumedBytes())
                        .submittedAt(LocalDateTime.ofInstant(
                                Instant.ofEpochSecond(cfSub.getCreationTimeSeconds()),
                                ZoneId.systemDefault()))
                        .build();

                submissionRepository.save(submission);
                newCount++;
            }

            log.info("Synced {} new submissions for handle: {}", newCount, handle);

        } catch (Exception e) {
            log.error("Failed to sync Codeforces data for {}: {}", handle, e.getMessage());
        }
    }

    private Problem getOrCreateProblem(CfSubmission cfSub) {
        CfProblem cfProblem = cfSub.getProblem();
        String platformId = cfProblem.getContestId() + cfProblem.getIndex();

        return problemRepository.findByPlatformIdAndPlatform(platformId, "CODEFORCES")
                .orElseGet(() -> {
                    Problem p = Problem.builder()
                            .platformId(platformId)
                            .platform("CODEFORCES")
                            .title(cfProblem.getName())
                            .difficultyRating(cfProblem.getRating())
                            .problemUrl("https://codeforces.com/problemset/problem/"
                                    + cfProblem.getContestId() + "/" + cfProblem.getIndex())
                            .topics(resolveTopics(cfProblem.getTags()))
                            .build();
                    return problemRepository.save(p);
                });
    }

    private List<Topic> resolveTopics(List<String> tags) {
        List<Topic> topics = new ArrayList<>();
        if (tags == null) return topics;
        for (String tag : tags) {
            String slug = tag.toLowerCase().replace(" ", "-");
            Topic topic = topicRepository.findBySlug(slug)
                    .orElseGet(() -> topicRepository.save(
                            Topic.builder().name(tag).slug(slug).build()));
            topics.add(topic);
        }
        return topics;
    }

    private Submission.Verdict mapVerdict(String cfVerdict) {
        if (cfVerdict == null) return Submission.Verdict.SKIPPED;
        return switch (cfVerdict) {
            case "OK" -> Submission.Verdict.ACCEPTED;
            case "WRONG_ANSWER" -> Submission.Verdict.WRONG_ANSWER;
            case "TIME_LIMIT_EXCEEDED" -> Submission.Verdict.TIME_LIMIT_EXCEEDED;
            case "MEMORY_LIMIT_EXCEEDED" -> Submission.Verdict.MEMORY_LIMIT_EXCEEDED;
            case "RUNTIME_ERROR" -> Submission.Verdict.RUNTIME_ERROR;
            case "COMPILATION_ERROR" -> Submission.Verdict.COMPILATION_ERROR;
            case "PARTIAL" -> Submission.Verdict.PARTIAL;
            default -> Submission.Verdict.SKIPPED;
        };
    }

    // ─── Codeforces API response POJOs ───────────────────────────────────────

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CfResponse {
        private String status;
        private List<CfSubmission> result;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CfSubmission {
        private long id;
        private long creationTimeSeconds;
        private CfProblem problem;
        private String verdict;
        private String programmingLanguage;
        private int timeConsumedMillis;
        private int memoryConsumedBytes;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CfProblem {
        private Integer contestId;
        private String index;
        private String name;
        private Integer rating;
        private List<String> tags;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CfRatingResponse {
        private String status;
        private List<Map<String, Object>> result;
    }
}
