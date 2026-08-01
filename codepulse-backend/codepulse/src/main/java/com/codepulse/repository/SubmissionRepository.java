package com.codepulse.repository;

import com.codepulse.entity.Submission;
import com.codepulse.entity.Submission.Verdict;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * FIX: Every query that needs Problem fields or Topics uses JOIN FETCH so that
 *      Hibernate loads them in the same SQL round-trip.
 *
 *      Without JOIN FETCH, the Problem proxy is left UNINITIALIZED after the
 *      JPA session closes (because spring.jpa.open-in-view=false), causing
 *      LazyInitializationException the moment any code calls
 *      submission.getProblem().getXxx() outside a @Transactional boundary.
 *
 *      Note: JOIN FETCH on a collection (topics) uses a DISTINCT HQL clause
 *      to avoid duplicate Submission rows in the result set.
 */
@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    // ─── Used by AnalyticsServiceImpl, RecommendationServiceImpl, AiService ──

    /**
     * Returns ALL submissions for a user with Problem AND Topics eagerly loaded.
     * Used by AnalyticsServiceImpl.getAnalytics() and the AI context builder.
     */
    @Query("""
           SELECT DISTINCT s FROM Submission s
             JOIN FETCH s.problem p
             LEFT JOIN FETCH p.topics
           WHERE s.user.id = :userId
           """)
    List<Submission> findByUserId(@Param("userId") Long userId);

    /**
     * Returns the N most-recent submissions with Problem AND Topics loaded.
     * Used by SubmissionController.getRecent() and AI context builder.
     */
    @Query("""
           SELECT DISTINCT s FROM Submission s
             JOIN FETCH s.problem p
             LEFT JOIN FETCH p.topics
           WHERE s.user.id = :userId
           ORDER BY s.submittedAt DESC NULLS LAST
           """)
    List<Submission> findRecentByUserId(@Param("userId") Long userId, Pageable pageable);

    // ─── Existence check (no entity loading — safe without JOIN FETCH) ────────

    Optional<Submission> findByPlatformSubmissionId(String platformSubmissionId);

    boolean existsByPlatformSubmissionId(String platformSubmissionId);

    // ─── Scalar aggregates (no entity fields accessed — safe) ─────────────────

    @Query("SELECT COUNT(s) FROM Submission s WHERE s.user.id = :userId AND s.verdict = 'ACCEPTED'")
    long countAcceptedByUserId(@Param("userId") Long userId);

    @Query("""
           SELECT COUNT(DISTINCT s.problem.id) FROM Submission s
           WHERE s.user.id = :userId AND s.verdict = 'ACCEPTED'
           """)
    long countDistinctAcceptedProblemsByUserId(@Param("userId") Long userId);

    /** Returns accepted Problem IDs only — no entity traversal needed. */
    @Query("""
           SELECT s.problem.id FROM Submission s
           WHERE s.user.id = :userId AND s.verdict = 'ACCEPTED'
           """)
    List<Long> findAcceptedProblemIdsByUserId(@Param("userId") Long userId);

    // ─── Pruning ──────────────────────────────────────────────────────────────

    @Query("SELECT COUNT(s) FROM Submission s WHERE s.user.id = :userId")
    long countByUserIdForPruning(@Param("userId") Long userId);

    /**
     * Returns IDs of the oldest submissions — no entity fields accessed,
     * so no JOIN FETCH needed.
     */
    @Query("SELECT s.id FROM Submission s WHERE s.user.id = :userId ORDER BY s.submittedAt ASC NULLS FIRST")
    List<Long> findOldestSubmissionIds(@Param("userId") Long userId, Pageable pageable);

    @Modifying
    @Query("DELETE FROM Submission s WHERE s.id IN :ids")
    void deleteAllByIds(@Param("ids") List<Long> ids);
}