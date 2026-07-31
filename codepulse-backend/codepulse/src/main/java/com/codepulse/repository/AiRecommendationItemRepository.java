package com.codepulse.repository;

import com.codepulse.entity.AiRecommendationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiRecommendationItemRepository extends JpaRepository<AiRecommendationItem, Long> {
}