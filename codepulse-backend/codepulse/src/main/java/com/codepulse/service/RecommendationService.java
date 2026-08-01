package com.codepulse.service;

import com.codepulse.dto.response.RecommendationResponse;

import java.util.List;

public interface RecommendationService {
    List<RecommendationResponse> getRecommendations(Long userId);
    void generateRecommendations(Long userId);
    void markSolved(Long userId, Long recommendationId);
    void dismiss(Long userId, Long recommendationId);
}
