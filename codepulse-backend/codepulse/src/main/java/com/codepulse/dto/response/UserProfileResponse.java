package com.codepulse.dto.response;

import com.codepulse.entity.User;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
public class UserProfileResponse {
    private Long id;
    private String username;
    private String email;
    private String fullName;
    private String avatarUrl;
    private String role;

    // ─── Platform handles ─────────────────────────────────────────────────────
    private String codeforcesHandle;
    private String leetcodeHandle;
    private String atcoderHandle;
    private String codechefHandle;
    private String hackerrankHandle;

    // Shows which platforms have an active handle configured
    private Map<String, Boolean> platformConnections;

    private LocalDateTime lastSyncedAt;
    private LocalDateTime createdAt;

    public static UserProfileResponse fromUser(User user) {
        Map<String, Boolean> connections = new LinkedHashMap<>();
        connections.put("CODEFORCES", user.getCodeforcesHandle() != null && !user.getCodeforcesHandle().isBlank());
        connections.put("LEETCODE", user.getLeetcodeHandle() != null && !user.getLeetcodeHandle().isBlank());
        connections.put("ATCODER", user.getAtcoderHandle() != null && !user.getAtcoderHandle().isBlank());
        connections.put("CODECHEF", user.getCodechefHandle() != null && !user.getCodechefHandle().isBlank());

        return UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole().name())
                .codeforcesHandle(user.getCodeforcesHandle())
                .leetcodeHandle(user.getLeetcodeHandle())
                .atcoderHandle(user.getAtcoderHandle())
                .codechefHandle(user.getCodechefHandle())
                .platformConnections(connections)
                .lastSyncedAt(user.getLastSyncedAt())
                .createdAt(user.getCreatedAt())
                .build();
    }
}