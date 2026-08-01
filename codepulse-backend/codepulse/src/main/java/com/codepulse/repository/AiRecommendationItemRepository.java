package com.codepulse.repository;

import com.codepulse.entity.AiRecommendationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * FIX:
 *
 * markItemSolved() and dismissItem() access
 *
 * item.getSession().getUser()
 *
 * Both session and user are LAZY.
 *
 * JOIN FETCH loads them together.
 */
@Repository
public interface AiRecommendationItemRepository
        extends JpaRepository<AiRecommendationItem, Long> {

    @Query("""
        SELECT i
        FROM AiRecommendationItem i
        JOIN FETCH i.session s
        JOIN FETCH s.user
        WHERE i.id = :itemId
    """)
    Optional<AiRecommendationItem> findByIdWithSessionAndUser(
            @Param("itemId") Long itemId);

}