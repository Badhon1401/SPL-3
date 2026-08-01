package com.codepulse.repository;

import com.codepulse.entity.AiRecommendationSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * FIX:
 *
 * spring.jpa.open-in-view=false
 *
 * AiRecommendationSession.items is LAZY.
 *
 * toResponse() iterates over session.getItems().
 *
 * Without JOIN FETCH Hibernate closes the session before the mapping,
 * causing LazyInitializationException.
 *
 * The query eagerly loads the complete recommendation list.
 */
@Repository
public interface AiRecommendationSessionRepository
        extends JpaRepository<AiRecommendationSession, Long> {

    @Query("""
        SELECT DISTINCT s
        FROM AiRecommendationSession s
        LEFT JOIN FETCH s.items
        WHERE s.user.id = :userId
          AND s.active = true
        ORDER BY s.createdAt DESC
    """)
    List<AiRecommendationSession> findLatestActiveSessions(
            @Param("userId") Long userId);

    @Modifying
    @Query("""
        UPDATE AiRecommendationSession s
        SET s.active = false
        WHERE s.user.id = :userId
    """)
    void deactivateAllForUser(@Param("userId") Long userId);

}