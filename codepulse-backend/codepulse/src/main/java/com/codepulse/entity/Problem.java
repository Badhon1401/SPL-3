package com.codepulse.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "problems")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Problem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "platform_id", nullable = false)
    private String platformId; // e.g., "1234A" for Codeforces

    @Column(nullable = false)
    private String platform; // CODEFORCES, LEETCODE

    @Column(nullable = false)
    private String title;

    @Column(name = "difficulty_rating")
    private Integer difficultyRating; // Codeforces numeric rating

    @Column(name = "difficulty_label")
    private String difficultyLabel; // Easy / Medium / Hard (LeetCode)

    @Column(name = "problem_url")
    private String problemUrl;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "problem_topics",
        joinColumns = @JoinColumn(name = "problem_id"),
        inverseJoinColumns = @JoinColumn(name = "topic_id")
    )
    @Builder.Default
    private List<Topic> topics = new ArrayList<>();

    @OneToMany(mappedBy = "problem", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Submission> submissions = new ArrayList<>();
}
