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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CodeChefDataService {

    private final WebClient.Builder webClientBuilder;
    private final SubmissionRepository submissionRepository;
    private final ProblemRepository problemRepository;
    private final TopicRepository topicRepository;

    @Value("${codechef.api.base-url}")
    private String codechefBaseUrl;

    @Async
    @Transactional
    public void syncUserSubmissions(User user) {
        String handle = user.getCodechefHandle();
        if (handle == null || handle.isBlank()) return;

        log.info("Syncing CodeChef data for handle: {}", handle);
        try {
            CodeChefProfile profile = webClientBuilder.build()
                    .get()
                    .uri(codechefBaseUrl + "/" + handle)
                    .retrieve()
                    .bodyToMono(CodeChefProfile.class)
                    .block();

            if (profile == null || profile.getProblems() == null) {
                log.warn("No CodeChef data for {}", handle);
                return;
            }

            int newCount = 0;
            for (String problemCode : profile.getProblems()) {
                String platformId = "CC_SOLVED_" + problemCode;
                if (submissionRepository.existsByPlatformSubmissionId(platformId)) continue;

                Problem problem = getOrCreateProblem(problemCode, profile.getDivision());
                Submission submission = Submission.builder()
                        .platformSubmissionId(platformId)
                        .user(user)
                        .problem(problem)
                        .verdict(Submission.Verdict.ACCEPTED)
                        .submittedAt(LocalDateTime.now()) // exact time not available via profile API
                        .build();

                submissionRepository.save(submission);
                newCount++;
            }
            log.info("Synced {} CodeChef solved problems for {}", newCount, handle);

        } catch (Exception e) {
            log.error("CodeChef sync failed for {}: {}", handle, e.getMessage());
        }
    }

    private Problem getOrCreateProblem(String code, String division) {
        return problemRepository.findByPlatformIdAndPlatform(code, "CODECHEF")
                .orElseGet(() -> {
                    Problem p = Problem.builder()
                            .platformId(code)
                            .platform("CODECHEF")
                            .title(code)
                            .difficultyLabel(mapDivisionToDifficulty(division))
                            .problemUrl("https://www.codechef.com/problems/" + code)
                            .topics(resolveDefaultTopics())
                            .build();
                    return problemRepository.save(p);
                });
    }

    private String mapDivisionToDifficulty(String division) {
        if (division == null) return "Medium";
        return switch (division) {
            case "div1" -> "Expert";
            case "div2" -> "Hard";
            case "div3" -> "Medium";
            case "div4" -> "Easy";
            default -> "Medium";
        };
    }

    private List<Topic> resolveDefaultTopics() {
        return List.of(topicRepository.findBySlug("competitive-programming")
                .orElseGet(() -> topicRepository.save(
                        Topic.builder().name("Competitive Programming").slug("competitive-programming").build())));
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    static class CodeChefProfile {
        private String username;
        private String division;
        private List<String> problems; // solved problem codes
    }
}