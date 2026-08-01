package com.codepulse.repository;

import com.codepulse.entity.Problem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProblemRepository extends JpaRepository<Problem, Long> {

    Optional<Problem> findByPlatformIdAndPlatform(String platformId, String platform);

    @Query("SELECT p FROM Problem p JOIN p.topics t WHERE t.id = :topicId " +
           "AND p.id NOT IN :solvedIds ORDER BY p.difficultyRating ASC")
    List<Problem> findUnsolvedByTopicId(@Param("topicId") Long topicId,
                                         @Param("solvedIds") List<Long> solvedIds);

    @Query("SELECT p FROM Problem p WHERE p.difficultyRating BETWEEN :min AND :max " +
           "AND p.id NOT IN :solvedIds ORDER BY p.difficultyRating ASC")
    List<Problem> findUnsolvedByDifficultyRange(@Param("min") int min,
                                                 @Param("max") int max,
                                                 @Param("solvedIds") List<Long> solvedIds);

    boolean existsByPlatformIdAndPlatform(String platformId, String platform);
}
