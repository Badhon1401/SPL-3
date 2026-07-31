package com.codepulse.service;
import com.codepulse.dto.request.AiPromptRequest;
import com.codepulse.dto.response.AiPromptResponse;

public interface AiRecommendationService {
    AiPromptResponse generateRecommendations(Long userId, AiPromptRequest request);
    AiPromptResponse getLatestSession(Long userId);
    void markItemSolved(Long userId, Long itemId);
    void dismissItem(Long userId, Long itemId);
}

