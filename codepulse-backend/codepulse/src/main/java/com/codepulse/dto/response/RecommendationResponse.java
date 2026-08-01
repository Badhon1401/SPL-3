package com.codepulse.dto.response;

import com.codepulse.entity.Recommendation;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RecommendationResponse {
    private Long id;
    private String problemTitle;
    private String problemUrl;
    private String platform;
    private Integer difficultyRating;
    private String difficultyLabel;
    private List<String> topics;
    private String reason;
    private Double score;

    public static RecommendationResponse fromRecommendation(Recommendation r) {
        return RecommendationResponse.builder()
                .id(r.getId())
                .problemTitle(r.getProblem().getTitle())
                .problemUrl(r.getProblem().getProblemUrl())
                .platform(r.getProblem().getPlatform())
                .difficultyRating(r.getProblem().getDifficultyRating())
                .difficultyLabel(r.getProblem().getDifficultyLabel())
                .topics(r.getProblem().getTopics().stream()
                        .map(t -> t.getName()).toList())
                .reason(r.getReason())
                .score(r.getScore())
                .build();
    }
}
