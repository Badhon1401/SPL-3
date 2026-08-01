package com.codepulse.repository;

import com.codepulse.entity.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

    @Query("SELECT r FROM Recommendation r WHERE r.user.id = :userId " +
           "AND r.solved = false AND r.dismissed = false ORDER BY r.score DESC")
    List<Recommendation> findActiveByUserId(@Param("userId") Long userId);

    void deleteByUserId(Long userId);
}
