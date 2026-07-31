package com.codepulse.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ai_recommendation_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AiRecommendationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private AiRecommendationSession session;

    @Column(nullable = false) private String title;
    @Column(nullable = false) private String platform;  // CODEFORCES | LEETCODE | ATCODER | CODECHEF
    @Column(length = 512)     private String url;
    @Column                   private String difficulty;
    @Column(name = "estimated_rating") private Integer estimatedRating;

    /** JSON array of topic strings */
    @Column(columnDefinition = "TEXT", name = "topics_json")
    private String topicsJson;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "time_estimate")
    private String timeEstimate;

    @Column(name = "is_solved")
    @Builder.Default
    private boolean solved = false;

    @Column(name = "is_dismissed")
    @Builder.Default
    private boolean dismissed = false;
}