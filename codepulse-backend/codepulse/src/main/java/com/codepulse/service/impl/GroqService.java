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

/**
 * Groq — free, fast, reliable JSON-mode LLM.
 * API is OpenAI-compatible.
 * Get a key: https://console.groq.com/keys
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroqService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.groq.api-key:}")
    private String apiKey;

    @Value("${app.groq.base-url:https://api.groq.com/openai/v1/chat/completions}")
    private String baseUrl;

    @Value("${app.groq.max-tokens:4096}")
    private int maxTokens;

    @Value("${app.groq.temperature:0.3}")
    private double temperature;

    @Value("${app.groq.models:llama-3.3-70b-versatile,llama-3.1-8b-instant,mixtral-8x7b-32768}")
    private String modelsString;

    private String lastSuccessfulModel;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String getLastSuccessfulModel() {
        return lastSuccessfulModel != null ? lastSuccessfulModel : "groq-unknown";
    }

    public String chat(String systemPrompt, String userMessage) {
        if (!isConfigured()) {
            throw new RuntimeException("Groq API key not configured (app.groq.api-key)");
        }

        List<String> models = Arrays.stream(modelsString.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

        Exception lastError = null;
        for (String model : models) {
            try {
                log.info("Attempting Groq chat with model: {}", model);
                String response = execute(model, systemPrompt, userMessage);

                int open  = response.indexOf('{');
                int close = response.lastIndexOf('}');
                if (open < 0 || close <= open) {
                    log.warn("Groq {} returned non-JSON. First 120 chars: {}",
                            model, response.length() > 120 ? response.substring(0, 120) : response);
                    lastError = new RuntimeException("Non-JSON from " + model);
                    continue;
                }

                this.lastSuccessfulModel = model;
                log.info("✅ Groq success with model: {}", model);
                return response;

            } catch (Exception e) {
                log.warn("Groq model {} failed: {}", model, e.getMessage());
                lastError = e;
            }
        }

        throw new RuntimeException("All Groq models failed. Last: "
                + (lastError != null ? lastError.getMessage() : "unknown"), lastError);
    }

    private String execute(String model, String systemPrompt, String userMessage) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("temperature", temperature);
        body.putObject("response_format").put("type", "json_object");

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

        HttpEntity<String> entity = new HttpEntity<>(
                objectMapper.writeValueAsString(body), headers);

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl, entity, JsonNode.class);

        return DirectMistralService.extractContent(response.getBody(),
                "Groq (" + model + ")");
    }
}