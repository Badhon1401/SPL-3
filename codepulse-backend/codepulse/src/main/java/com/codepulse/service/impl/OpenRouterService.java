package com.codepulse.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenRouterService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.openrouter.api-key:}")
    private String apiKey;

    @Value("${app.openrouter.base-url:https://openrouter.ai/api/v1/chat/completions}")
    private String baseUrl;

    // Commas-separated list of backup models. Defaults to our top free choices if properties are omitted.
    @Value("${app.openrouter.models:google/gemini-2.5-flash:free,meta-llama/llama-3.3-70b-instruct:free,qwen/qwen-2.5-72b-instruct:free,mistralai/mistral-7b-instruct:free}")
    private String modelsString;

    @Value("${app.openrouter.max-tokens:2048}")
    private int maxTokens;

    @Value("${app.openrouter.temperature:0.7}")
    private double temperature;

    // Track which fallback model actually succeeded so we can save it in the DB session
    private String lastSuccessfulModel;

    /**
     * @return true if an API key is configured (safe to call {@link #chat})
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String getLastSuccessfulModel() {
        return lastSuccessfulModel != null ? lastSuccessfulModel : "unknown-fallback";
    }

    /**
     * Sends a system + user message pair to OpenRouter, trying each model sequentially until one succeeds.
     */
    public String chat(String systemPrompt, String userMessage) {
        if (!isConfigured()) {
            throw new RuntimeException("OpenRouter API key not configured (app.openrouter.api-key)");
        }

        // Convert the comma-separated string to a list of models
        List<String> modelList = Arrays.stream(modelsString.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        if (modelList.isEmpty()) {
            throw new RuntimeException("No fallback models found in property 'app.openrouter.models'");
        }

        Exception lastException = null;

        // Loop through each model until one responds successfully
        for (String currentModel : modelList) {
            log.info("Attempting OpenRouter chat with model: {}", currentModel);
            try {
                String response = executeChat(currentModel, systemPrompt, userMessage);
                this.lastSuccessfulModel = currentModel; // Cache the successful model
                log.info("Successfully got response from fallback model: {}", currentModel);
                return response;
            } catch (Exception e) {
                log.warn("OpenRouter model failed: {}. Error: {}. Moving to next fallback...", currentModel, e.getMessage());
                lastException = e;
            }
        }

        // Throw an exception only if all options have failed
        log.error("All fallback OpenRouter models failed!");
        throw new RuntimeException("All OpenRouter models failed. Last model error: "
                + (lastException != null ? lastException.getMessage() : "Unknown"), lastException);
    }

    private String executeChat(String targetModel, String systemPrompt, String userMessage) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", targetModel);
        body.put("max_tokens", maxTokens);
        body.put("temperature", temperature);

        ArrayNode messages = objectMapper.createArrayNode();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            ObjectNode sys = objectMapper.createObjectNode();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
            messages.add(sys);
        }
        ObjectNode userMsg = objectMapper.createObjectNode();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);
        body.set("messages", messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        headers.set("HTTP-Referer", "https://codepulse.app");
        headers.set("X-Title", "CodePulse AI Coach");

        String requestJson = objectMapper.writeValueAsString(body);
        HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl, entity, JsonNode.class);

        return DirectMistralService.extractContent(response.getBody(), "OpenRouter (" + targetModel + ")");
    }
}