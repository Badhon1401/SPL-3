package com.codepulse.service.impl;

import com.codepulse.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Enforces a per-user submission ceiling (default 300).
 * When exceeded, the oldest submissions are deleted so that analytics
 * and recommendations always operate on the most recent data.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubmissionPruningService {

    private final SubmissionRepository submissionRepository;

    @Value("${codepulse.submissions.max-per-user:300}")
    private int maxSubmissionsPerUser;

    @Transactional
    public void pruneIfNeeded(Long userId) {
        long current = submissionRepository.countByUserIdForPruning(userId);
        if (current <= maxSubmissionsPerUser) return;

        long toDelete = current - maxSubmissionsPerUser;
        List<Long> idsToDelete = submissionRepository.findOldestSubmissionIds(
                userId, PageRequest.of(0, (int) toDelete));

        if (!idsToDelete.isEmpty()) {
            submissionRepository.deleteAllByIds(idsToDelete);
            log.info("Pruned {} old submissions for user {} (limit={})", toDelete, userId, maxSubmissionsPerUser);
        }
    }
}