package com.codepulse.service.impl;

import com.codepulse.dto.response.RecommendationResponse;
import com.codepulse.entity.Problem;
import com.codepulse.entity.Recommendation;
import com.codepulse.entity.Submission;
import com.codepulse.entity.Topic;
import com.codepulse.entity.User;
import com.codepulse.exception.ResourceNotFoundException;
import com.codepulse.repository.ProblemRepository;
import com.codepulse.repository.RecommendationRepository;
import com.codepulse.repository.SubmissionRepository;
import com.codepulse.repository.TopicRepository;
import com.codepulse.service.RecommendationService;
import com.codepulse.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationServiceImpl implements RecommendationService {

    private final RecommendationRepository recommendationRepository;
    private final SubmissionRepository submissionRepository;
    private final ProblemRepository problemRepository;
    private final TopicRepository topicRepository;
    private final UserService userService;

    private static final int MAX_RECOMMENDATIONS = 10;

    @Override
    public List<RecommendationResponse> getRecommendations(Long userId) {
        return recommendationRepository.findActiveByUserId(userId).stream()
                .map(RecommendationResponse::fromRecommendation)
                .toList();
    }

    @Override
    @Transactional
    public void generateRecommendations(Long userId) {
        User user = userService.getUserById(userId);

        // Clear old recommendations
        recommendationRepository.deleteByUserId(userId);

        List<Submission> submissions = submissionRepository.findByUserId(userId);
        if (submissions.isEmpty()) {
            log.info("No submissions found for user {}. Skipping recommendation generation.", userId);
            return;
        }

        // Get solved problem IDs to exclude them
        List<Long> solvedProblemIds = submissionRepository.findAcceptedProblemIdsByUserId(userId);
        List<Long> excludeIds = solvedProblemIds.isEmpty() ? List.of(-1L) : solvedProblemIds;

        // Compute topic weakness scores
        Map<String, Double> weaknessScores = computeWeaknessScores(submissions);

        // Estimate user rating level
        int estimatedRating = estimateUserRating(submissions);
        int ratingMin = Math.max(800, estimatedRating - 200);
        int ratingMax = estimatedRating + 300;

        List<Recommendation> recommendations = new ArrayList<>();

        // Generate recommendations for weakest topics (top 3)
        weaknessScores.entrySet().stream()
                .filter(e -> e.getValue() > 0.3) // weakness threshold
                .limit(3)
                .forEach(entry -> {
                    String topicName = entry.getKey();
                    double weakness = entry.getValue();

                    topicRepository.findBySlug(topicName.toLowerCase().replace(" ", "-"))
                            .ifPresent(topic -> {
                                List<Problem> candidates = problemRepository
                                        .findUnsolvedByTopicId(topic.getId(), excludeIds);

                                candidates.stream()
                                        .filter(p -> p.getDifficultyRating() != null
                                                && p.getDifficultyRating() >= ratingMin
                                                && p.getDifficultyRating() <= ratingMax)
                                        .limit(3)
                                        .forEach(problem -> recommendations.add(
                                                Recommendation.builder()
                                                        .user(user)
                                                        .problem(problem)
                                                        .reason("Weakness detected in " + topicName)
                                                        .score(weakness)
                                                        .build()));
                            });
                });

        // Fill remaining slots with difficulty-range-based suggestions
        int remaining = MAX_RECOMMENDATIONS - recommendations.size();
        if (remaining > 0) {
            Set<Long> alreadyPicked = recommendations.stream()
                    .map(r -> r.getProblem().getId())
                    .collect(Collectors.toSet());

            List<Long> fullExclude = new ArrayList<>(excludeIds);
            fullExclude.addAll(alreadyPicked);

            problemRepository.findUnsolvedByDifficultyRange(ratingMin, ratingMax, fullExclude)
                    .stream()
                    .limit(remaining)
                    .forEach(problem -> recommendations.add(
                            Recommendation.builder()
                                    .user(user)
                                    .problem(problem)
                                    .reason("Matches your current level (" + estimatedRating + ")")
                                    .score(0.5)
                                    .build()));
        }

        recommendationRepository.saveAll(recommendations);
        log.info("Generated {} recommendations for user {}", recommendations.size(), userId);
    }

    @Override
    @Transactional
    public void markSolved(Long userId, Long recommendationId) {
        Recommendation rec = getRecommendationForUser(userId, recommendationId);
        rec.setSolved(true);
        recommendationRepository.save(rec);
    }

    @Override
    @Transactional
    public void dismiss(Long userId, Long recommendationId) {
        Recommendation rec = getRecommendationForUser(userId, recommendationId);
        rec.setDismissed(true);
        recommendationRepository.save(rec);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private Recommendation getRecommendationForUser(Long userId, Long recId) {
        return recommendationRepository.findById(recId)
                .filter(r -> r.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Recommendation not found"));
    }

    private Map<String, Double> computeWeaknessScores(List<Submission> submissions) {
        Map<String, Long> totalPerTopic = new HashMap<>();
        Map<String, Long> failedPerTopic = new HashMap<>();

        for (Submission s : submissions) {
            s.getProblem().getTopics().forEach(t -> {
                totalPerTopic.merge(t.getName(), 1L, Long::sum);
                if (s.getVerdict() != Submission.Verdict.ACCEPTED) {
                    failedPerTopic.merge(t.getName(), 1L, Long::sum);
                }
            });
        }

        Map<String, Double> scores = new HashMap<>();
        totalPerTopic.forEach((topic, total) -> {
            long failed = failedPerTopic.getOrDefault(topic, 0L);
            scores.put(topic, total == 0 ? 0.0 : (double) failed / total);
        });

        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue,
                        (e1, e2) -> e1, LinkedHashMap::new));
    }

    private int estimateUserRating(List<Submission> submissions) {
        OptionalDouble avg = submissions.stream()
                .filter(s -> s.getVerdict() == Submission.Verdict.ACCEPTED)
                .filter(s -> s.getProblem().getDifficultyRating() != null)
                .mapToInt(s -> s.getProblem().getDifficultyRating())
                .average();
        return (int) avg.orElse(1200);
    }
}
