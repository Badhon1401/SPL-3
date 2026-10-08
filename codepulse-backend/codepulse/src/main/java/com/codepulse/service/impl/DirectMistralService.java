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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class DirectMistralService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${mistral.api-key:${spring.ai.mistralai.api-key:}}")
    private String apiKey;

    @Value("${mistral.base-url:https://api.mistral.ai}")
    private String baseUrl;

    @Value("${mistral.model:mistral-small-latest}")
    private String model;

    @Value("${mistral.max-tokens:4096}")
    private int maxTokens;

    @Value("${mistral.temperature:0.3}")
    private double temperature;

    public String chat(String systemPrompt, String userMessage) {
        String endpoint = buildEndpoint();

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("temperature", temperature);

        // Force JSON output — Mistral supports this on all recent models
        body.putObject("response_format").put("type", "json_object");

        ArrayNode messages = objectMapper.createArrayNode();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            ObjectNode sys = objectMapper.createObjectNode();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
            messages.add(sys);
        }
        ObjectNode user = objectMapper.createObjectNode();
        user.put("role", "user");
        user.put("content", userMessage);
        messages.add(user);
        body.set("messages", messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        try {
            String requestJson = objectMapper.writeValueAsString(body);
            HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);

            ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                    endpoint, entity, JsonNode.class);

            return extractContent(response.getBody(), "Mistral");

        } catch (HttpClientErrorException e) {
            log.error("Mistral 4xx error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Mistral client error " + e.getStatusCode()
                    + ": " + e.getResponseBodyAsString(), e);
        } catch (HttpServerErrorException e) {
            log.error("Mistral 5xx error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Mistral server error " + e.getStatusCode(), e);
        } catch (Exception e) {
            log.error("Mistral direct call failed: {}", e.getMessage());
            throw new RuntimeException("Mistral API call failed: " + e.getMessage(), e);
        }
    }

    private String buildEndpoint() {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + "/v1/chat/completions";
    }

    static String extractContent(JsonNode root, String provider) {
        if (root == null || root.isMissingNode()) {
            throw new RuntimeException(provider + " returned null/empty body");
        }
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new RuntimeException(provider + " returned no choices: " + root);
        }
        JsonNode message = choices.get(0).path("message");
        String content = message.path("content").asText(null);
        if (content == null || content.isBlank()) {
            content = message.path("reasoning").asText(null);
        }
        if (content == null || content.isBlank()) {
            throw new RuntimeException(provider + " returned empty content: " + root);
        }
        return content.trim();
    }
}