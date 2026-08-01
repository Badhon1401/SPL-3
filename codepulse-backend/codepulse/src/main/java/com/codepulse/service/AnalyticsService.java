package com.codepulse.service;

import com.codepulse.dto.response.PerformanceAnalyticsResponse;

public interface AnalyticsService {
    PerformanceAnalyticsResponse getAnalytics(Long userId);
    void syncAndRefresh(Long userId);
}
