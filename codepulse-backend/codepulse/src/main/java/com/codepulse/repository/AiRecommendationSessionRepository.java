package com.codepulse.repository;

import com.codepulse.entity.AiRecommendationSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiRecommendationSessionRepository extends JpaRepository<AiRecommendationSession, Long> {

    Optional<AiRecommendationSession> findTopByUserIdAndActiveTrueOrderByCreatedAtDesc(Long userId);

    @Modifying
    @Query("UPDATE AiRecommendationSession s SET s.active = false WHERE s.user.id = :userId")
    void deactivateAllForUser(@Param("userId") Long userId);
}