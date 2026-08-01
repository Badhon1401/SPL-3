package com.codepulse.controller;

import com.codepulse.dto.response.ApiResponse;
import com.codepulse.dto.response.PerformanceAnalyticsResponse;
import com.codepulse.security.UserPrincipal;
import com.codepulse.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /**
     * GET /api/analytics/me
     * Returns full performance analytics for the currently authenticated user.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PerformanceAnalyticsResponse>> getMyAnalytics(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
                ApiResponse.success(analyticsService.getAnalytics(principal.getId())));
    }

    /**
     * GET /api/analytics/{userId}
     * Returns analytics for a specific user (admin or self).
     */
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<PerformanceAnalyticsResponse>> getAnalytics(
            @PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getAnalytics(userId)));
    }

    /**
     * POST /api/analytics/sync
     * Triggers a background sync of coding data from Codeforces and LeetCode for the current user.
     */
    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<Void>> syncData(
            @AuthenticationPrincipal UserPrincipal principal) {
        analyticsService.syncAndRefresh(principal.getId());
        return ResponseEntity.ok(
                ApiResponse.success("Data sync initiated. Results will be available shortly.", null));
    }
}
