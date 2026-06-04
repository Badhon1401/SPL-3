package com.codepulse.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

/**
 * Core application config.
 *
 * NOTE: Spring AI (spring-ai-mistral-ai-spring-boot-starter) has been removed
 * from pom.xml.  MistralAiApi creates an internal ObjectMapper that cannot be
 * replaced via any Spring customizer, causing "prompt_tokens_details
 * UnrecognizedPropertyException" on every call.
 *
 * We now use DirectMistralService (direct HTTP + JsonNode) and
 * OpenRouterService (fallback) — no Spring AI SDK involved.
 *
 * REQUIRED CLEANUP: delete the following files if they still exist in your project:
 *   - MistralRestClientConfig.java
 *   - MistralFixConfig.java
 * Both reference classes from the removed Spring AI artifact and will cause
 * compilation errors.
 */
@Configuration
@EnableScheduling
@EnableAsync
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}