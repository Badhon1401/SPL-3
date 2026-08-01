package com.codepulse.repository;

import com.codepulse.entity.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * FIX: The original findActiveByUserId() returned Recommendation entities
 *      with a LAZY-loaded Problem proxy. When RecommendationResponse.fromRecommendation()
 *      called rec.getProblem().getTitle() (or .getTopics()) after the session
 *      had closed, Hibernate threw LazyInitializationException.
 *
 *      The JOIN FETCH here loads Problem and its Topics in the same query.
 */
@Repository
public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

    /**
     * Loads active recommendations with Problem and Topics in one round-trip.
     * DISTINCT prevents duplicate Recommendation rows when a Problem has
     * multiple Topics (each topic join would otherwise duplicate the row).
     */
    @Query("""
           SELECT DISTINCT r FROM Recommendation r
             JOIN FETCH r.problem p
             LEFT JOIN FETCH p.topics
           WHERE r.user.id = :userId
             AND r.solved    = false
             AND r.dismissed = false
           ORDER BY r.score DESC
           """)
    List<Recommendation> findActiveByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM Recommendation r WHERE r.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}