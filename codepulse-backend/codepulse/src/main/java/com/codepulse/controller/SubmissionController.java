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
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionRepository submissionRepository;

    /**
     * GET /api/submissions/recent?limit=10
     * Returns the N most recent submissions across all platforms for the current user.
     */
    @GetMapping("/recent")
    public ResponseEntity<ApiResponse<List<RecentSubmissionResponse>>> getRecent(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "10") int limit) {

        int cap = Math.min(limit, 50);
        List<Submission> subs = submissionRepository.findRecentByUserId(
                principal.getId(), PageRequest.of(0, cap));

        List<RecentSubmissionResponse> result = subs.stream()
                .map(RecentSubmissionResponse::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(result));
    }
}