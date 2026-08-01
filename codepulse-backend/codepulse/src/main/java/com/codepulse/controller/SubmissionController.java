package com.codepulse.controller;

import com.codepulse.dto.response.ApiResponse;
import com.codepulse.dto.response.RecentSubmissionResponse;
import com.codepulse.entity.Submission;
import com.codepulse.repository.SubmissionRepository;
import com.codepulse.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * FIX: Added @Transactional(readOnly = true) so the JPA session stays open
 *      for the full duration of the method — including the stream().map() call
 *      that accesses sub.getProblem().getTitle() / .getTopics() in
 *      RecentSubmissionResponse.from().
 *
 *      With spring.jpa.open-in-view=false (correct setting) and no @Transactional,
 *      the session closed immediately after findRecentByUserId(), leaving
 *      uninitialized LAZY proxies — hence the LazyInitializationException.
 *
 *      The repository's JOIN FETCH query already loads Problem + Topics eagerly,
 *      but @Transactional(readOnly=true) is kept here as a safety net and
 *      allows Hibernate to use a single connection for the whole request.
 */
@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionRepository submissionRepository;

    /**
     * GET /api/submissions/recent?limit=10
     * Returns the N most recent submissions (all platforms) for the current user,
     * with problem title, URL, platform, verdict, topics, and time-ago already
     * mapped into a flat DTO — no further lazy loading needed on the frontend side.
     */
    @GetMapping("/recent")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<RecentSubmissionResponse>>> getRecent(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "10") int limit) {

        int cap = Math.min(limit, 50);

        List<Submission> subs = submissionRepository.findRecentByUserId(
                principal.getId(), PageRequest.of(0, cap));

        // Session is still open here (inside @Transactional), so accessing
        // problem fields is safe even if any proxy wasn't JOIN FETCH-ed.
        List<RecentSubmissionResponse> result = subs.stream()
                .map(RecentSubmissionResponse::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(result));
    }
}