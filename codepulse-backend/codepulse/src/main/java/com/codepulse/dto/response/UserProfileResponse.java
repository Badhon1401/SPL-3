package com.codepulse.dto.response;
import com.codepulse.entity.User;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Data @Builder
public class UserProfileResponse {
    private Long id;
    private String username;
    private String email;
    private String fullName;
    private String avatarUrl;
    private String role;
    private String codeforcesHandle;
    private String leetcodeHandle;
    private String atcoderHandle;
    private String codechefHandle;
    private Map<String, Boolean> platformConnections;
    private LocalDateTime lastSyncedAt;
    private LocalDateTime createdAt;

    public static UserProfileResponse fromUser(User u) {
        Map<String, Boolean> c = new LinkedHashMap<>();
        c.put("CODEFORCES", u.getCodeforcesHandle() != null && !u.getCodeforcesHandle().isBlank());
        c.put("LEETCODE",   u.getLeetcodeHandle()   != null && !u.getLeetcodeHandle().isBlank());
        c.put("ATCODER",    u.getAtcoderHandle()    != null && !u.getAtcoderHandle().isBlank());
        c.put("CODECHEF",   u.getCodechefHandle()   != null && !u.getCodechefHandle().isBlank());
        return UserProfileResponse.builder()
                .id(u.getId()).username(u.getUsername()).email(u.getEmail())
                .fullName(u.getFullName()).avatarUrl(u.getAvatarUrl()).role(u.getRole().name())
                .codeforcesHandle(u.getCodeforcesHandle())
                .leetcodeHandle(u.getLeetcodeHandle())
                .atcoderHandle(u.getAtcoderHandle())
                .codechefHandle(u.getCodechefHandle())
                .platformConnections(c).lastSyncedAt(u.getLastSyncedAt()).createdAt(u.getCreatedAt())
                .build();
    }
}