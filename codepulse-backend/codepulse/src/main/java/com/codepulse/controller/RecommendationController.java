package com.codepulse.controller;

import com.codepulse.dto.response.ApiResponse;
import com.codepulse.dto.response.RecommendationResponse;
import com.codepulse.security.UserPrincipal;
import com.codepulse.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    /**
     * GET /api/recommendations
     * Returns current active recommendations for the authenticated user.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<RecommendationResponse>>> getRecommendations(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
                ApiResponse.success(recommendationService.getRecommendations(principal.getId())));
    }

    /**
     * POST /api/recommendations/generate
     * Triggers the recommendation engine to (re)generate recommendations.
     */
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<Void>> generate(
            @AuthenticationPrincipal UserPrincipal principal) {
        recommendationService.generateRecommendations(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Recommendations generated", null));
    }

    /**
     * PATCH /api/recommendations/{id}/solved
     * Marks a recommendation as solved (feedback loop).
     */
    @PatchMapping("/{id}/solved")
    public ResponseEntity<ApiResponse<Void>> markSolved(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        recommendationService.markSolved(principal.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Marked as solved", null));
    }

    /**
     * PATCH /api/recommendations/{id}/dismiss
     * Dismisses a recommendation.
     */
    @PatchMapping("/{id}/dismiss")
    public ResponseEntity<ApiResponse<Void>> dismiss(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        recommendationService.dismiss(principal.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Recommendation dismissed", null));
    }
}
