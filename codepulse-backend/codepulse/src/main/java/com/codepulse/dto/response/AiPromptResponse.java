package com.codepulse.dto.response;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder
public class AiPromptResponse {
    private Long sessionId;
    private List<AiItem> recommendations;
    private String coachInsight;
    private List<String> focusAreas;
    private String originalPrompt;
    private String modelUsed;
    private LocalDateTime generatedAt;

    @Data @Builder
    public static class AiItem {
        private Long itemId;
        private String title;
        private String platform;
        private String url;
        private String difficulty;
        private Integer estimatedRating;
        private List<String> topics;
        private String reason;
        private String timeEstimate;
        private boolean solved;
        private boolean dismissed;
    }
}