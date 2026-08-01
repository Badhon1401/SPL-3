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

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    List<Submission> findByUserId(Long userId);

    Optional<Submission> findByPlatformSubmissionId(String platformSubmissionId);

    @Query("SELECT s FROM Submission s WHERE s.user.id = :userId AND s.verdict = :verdict")
    List<Submission> findByUserIdAndVerdict(@Param("userId") Long userId,
                                            @Param("verdict") Verdict verdict);

    @Query("SELECT COUNT(s) FROM Submission s WHERE s.user.id = :userId AND s.verdict = 'ACCEPTED'")
    long countAcceptedByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(DISTINCT s.problem.id) FROM Submission s " +
            "WHERE s.user.id = :userId AND s.verdict = 'ACCEPTED'")
    long countDistinctAcceptedProblemsByUserId(@Param("userId") Long userId);

    @Query("SELECT s FROM Submission s WHERE s.user.id = :userId " +
            "AND s.submittedAt BETWEEN :from AND :to ORDER BY s.submittedAt DESC")
    List<Submission> findByUserIdAndDateRange(@Param("userId") Long userId,
                                              @Param("from") LocalDateTime from,
                                              @Param("to") LocalDateTime to);

    @Query("SELECT s.problem.id FROM Submission s WHERE s.user.id = :userId AND s.verdict = 'ACCEPTED'")
    List<Long> findAcceptedProblemIdsByUserId(@Param("userId") Long userId);

    boolean existsByPlatformSubmissionId(String platformSubmissionId);

    // ─── Pruning ──────────────────────────────────────────────────────────────

    @Query("SELECT COUNT(s) FROM Submission s WHERE s.user.id = :userId")
    long countByUserIdForPruning(@Param("userId") Long userId);

    /** Returns IDs of the oldest submissions for a user, up to the page size. */
    @Query("SELECT s.id FROM Submission s WHERE s.user.id = :userId ORDER BY s.submittedAt ASC NULLS FIRST")
    List<Long> findOldestSubmissionIds(@Param("userId") Long userId, Pageable pageable);

    @Modifying
    @Query("DELETE FROM Submission s WHERE s.id IN :ids")
    void deleteAllByIds(@Param("ids") List<Long> ids);

    // ─── Recent submissions for dashboard ────────────────────────────────────
    @Query("SELECT s FROM Submission s WHERE s.user.id = :userId ORDER BY s.submittedAt DESC")
    List<Submission> findRecentByUserId(@Param("userId") Long userId, Pageable pageable);
}