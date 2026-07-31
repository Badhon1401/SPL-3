package com.codepulse.service.impl;

import com.codepulse.repository.UserRepository;
import com.codepulse.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledSyncService {

    private final UserRepository userRepository;
    private final AnalyticsService analyticsService;

    @Value("${codepulse.sync.enabled:true}")
    private boolean syncEnabled;

    /**
     * Runs every 6 hours. Syncs all active users that have at least one
     * platform handle configured.
     */
    @Scheduled(cron = "${codepulse.sync.cron:0 0 */6 * * *}")
    public void syncAllUsers() {
        if (!syncEnabled) return;

        log.info("Starting scheduled sync for all active users...");
        var users = userRepository.findByActiveTrue();
        int synced = 0;

        for (var user : users) {
            boolean hasHandle = user.getCodeforcesHandle() != null
                    || user.getLeetcodeHandle() != null
                    || user.getAtcoderHandle() != null
                    || user.getCodechefHandle() != null;
            if (!hasHandle) continue;

            try {
                analyticsService.syncAndRefresh(user.getId());
                synced++;
            } catch (Exception e) {
                log.warn("Scheduled sync failed for user {}: {}", user.getId(), e.getMessage());
            }
        }
        log.info("Scheduled sync complete — synced {} users", synced);
    }
}