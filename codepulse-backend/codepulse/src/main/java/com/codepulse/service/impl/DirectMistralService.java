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

/**
 * Calls the Mistral AI chat completions endpoint DIRECTLY via RestTemplate.
 *
 * WHY THIS EXISTS:
 *   Spring AI M4–M6's MistralAiApi creates its own private ObjectMapper instance
 *   and hardcodes it — no Spring bean, no RestClientCustomizer, no global Jackson
 *   property can override it.  When the Mistral API added "prompt_tokens_details"
 *   to its response, the internal mapper threw UnrecognizedPropertyException on
 *   every single call.
 *
 * THE FIX:
 *   We deserialise the response to JsonNode (Jackson's generic tree).
 *   JsonNode reads ANY JSON without a target class, so unknown fields like
 *   "prompt_tokens_details" are silently ignored.  We then walk the tree to
 *   extract only the content we actually need.
 */
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

    @Value("${mistral.model:mistral-large-latest}")
    private String model;

    @Value("${mistral.max-tokens:2048}")
    private int maxTokens;

    @Value("${mistral.temperature:0.7}")
    private double temperature;

    /**
     * Sends a system + user message pair to Mistral and returns the assistant content.
     *
     * @param systemPrompt instructions for the AI (may be null/blank to omit)
     * @param userMessage  the user's message
     * @return assistant reply text
     * @throws RuntimeException if the API call fails or returns an empty response
     */
    public String chat(String systemPrompt, String userMessage) {
        String endpoint = buildEndpoint();

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("temperature", temperature);

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

            /*
             * KEY: We deserialise to JsonNode, NOT to MistralAiApi$ChatCompletion.
             * JsonNode accepts any JSON tree regardless of unknown fields —
             * "prompt_tokens_details", "reasoning", whatever Mistral adds next,
             * will never cause an UnrecognizedPropertyException.
             */
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

    // ─── helpers ─────────────────────────────────────────────────────────────

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
        // Some reasoning models put the answer in "reasoning" instead
        if (content == null || content.isBlank()) {
            content = message.path("reasoning").asText(null);
        }
        if (content == null || content.isBlank()) {
            throw new RuntimeException(provider + " returned empty content: " + root);
        }
        return content.trim();
    }
}