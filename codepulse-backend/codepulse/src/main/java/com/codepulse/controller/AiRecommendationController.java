package com.codepulse.controller;

import com.codepulse.dto.request.AiPromptRequest;
import com.codepulse.dto.response.AiPromptResponse;
import com.codepulse.dto.response.ApiResponse;
import com.codepulse.security.UserPrincipal;
import com.codepulse.service.AiRecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiRecommendationController {

    private final AiRecommendationService aiService;

    /** Generate new AI recommendations (replaces previous session). */
    @PostMapping("/recommend")
    public ResponseEntity<ApiResponse<AiPromptResponse>> recommend(
            @AuthenticationPrincipal UserPrincipal p,
            @Valid @RequestBody AiPromptRequest request) {
        return ResponseEntity.ok(ApiResponse.success("AI recommendations generated",
                aiService.generateRecommendations(p.getId(), request)));
    }

    /** Load the user's latest stored AI session. */
    @GetMapping("/sessions/latest")
    public ResponseEntity<ApiResponse<AiPromptResponse>> latestSession(
            @AuthenticationPrincipal UserPrincipal p) {
        AiPromptResponse session = aiService.getLatestSession(p.getId());
        if (session == null) return ResponseEntity.ok(ApiResponse.success("No sessions found", null));
        return ResponseEntity.ok(ApiResponse.success(session));
    }

    /** Mark an AI-recommended item as solved. */
    @PatchMapping("/items/{itemId}/solved")
    public ResponseEntity<ApiResponse<Void>> markSolved(
            @AuthenticationPrincipal UserPrincipal p, @PathVariable Long itemId) {
        aiService.markItemSolved(p.getId(), itemId);
        return ResponseEntity.ok(ApiResponse.success("Marked solved", null));
    }

    /** Dismiss an AI-recommended item. */
    @PatchMapping("/items/{itemId}/dismiss")
    public ResponseEntity<ApiResponse<Void>> dismiss(
            @AuthenticationPrincipal UserPrincipal p, @PathVariable Long itemId) {
        aiService.dismissItem(p.getId(), itemId);
        return ResponseEntity.ok(ApiResponse.success("Dismissed", null));
    }
}