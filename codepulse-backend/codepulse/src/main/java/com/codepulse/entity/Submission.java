package com.codepulse.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "submissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "platform_submission_id", unique = true)
    private String platformSubmissionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Verdict verdict; // ACCEPTED, WRONG_ANSWER, etc.

    @Column
    private String language;

    @Column(name = "time_consumed_millis")
    private Integer timeConsumedMillis;

    @Column(name = "memory_consumed_bytes")
    private Integer memoryConsumedBytes;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @CreationTimestamp
    @Column(name = "synced_at", updatable = false)
    private LocalDateTime syncedAt;

    public enum Verdict {
        ACCEPTED,
        WRONG_ANSWER,
        TIME_LIMIT_EXCEEDED,
        MEMORY_LIMIT_EXCEEDED,
        RUNTIME_ERROR,
        COMPILATION_ERROR,
        PARTIAL,
        SKIPPED
    }
}
