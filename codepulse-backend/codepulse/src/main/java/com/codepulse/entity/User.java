package com.codepulse.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true) private String username;
    @Column(nullable = false, unique = true) private String email;
    @Column(nullable = false)               private String password;

    // 4 supported platforms
    @Column(name = "codeforces_handle") private String codeforcesHandle;
    @Column(name = "leetcode_handle")   private String leetcodeHandle;
    @Column(name = "atcoder_handle")    private String atcoderHandle;
    @Column(name = "codechef_handle")   private String codechefHandle;

    @Column(name = "full_name")  private String fullName;
    @Column(name = "avatar_url") private String avatarUrl;

    @Enumerated(EnumType.STRING) @Column(nullable = false) @Builder.Default
    private Role role = Role.USER;

    @Column(name = "is_active") @Builder.Default
    private boolean active = true;

    @Column(name = "last_synced_at") private LocalDateTime lastSyncedAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY) @Builder.Default
    private List<Submission> submissions = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY) @Builder.Default
    private List<Recommendation> recommendations = new ArrayList<>();

    @CreationTimestamp @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @UpdateTimestamp   @Column(name = "updated_at")                    private LocalDateTime updatedAt;

    public enum Role { USER, ADMIN }
}