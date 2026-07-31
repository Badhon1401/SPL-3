package com.codepulse.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiPromptRequest {

    @NotBlank(message = "Prompt cannot be empty")
    @Size(min = 5, max = 1000, message = "Prompt must be between 5 and 1000 characters")
    private String prompt;

    /** Optional: number of recommendations to return (default 6) */
    private Integer count = 6;
}