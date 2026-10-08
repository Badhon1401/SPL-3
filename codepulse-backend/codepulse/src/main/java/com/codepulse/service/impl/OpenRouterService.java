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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenRouterService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final OpenRouterModelDiscovery discovery;

    @Value("${app.openrouter.api-key:}")
    private String apiKey;

    @Value("${app.openrouter.base-url:https://openrouter.ai/api/v1/chat/completions}")
    private String baseUrl;

    /** Optional override list. If empty → dynamic discovery of free models. */
    @Value("${app.openrouter.models:}")
    private String modelsString;

    @Value("${app.openrouter.max-tokens:4096}")
    private int maxTokens;

    @Value("${app.openrouter.temperature:0.3}")
    private double temperature;

    private String lastSuccessfulModel;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String getLastSuccessfulModel() {
        return lastSuccessfulModel != null ? lastSuccessfulModel : "unknown-fallback";
    }

    public String chat(String systemPrompt, String userMessage) {
        if (!isConfigured()) {
            throw new RuntimeException("OpenRouter API key not configured (app.openrouter.api-key)");
        }

        List<String> modelList = resolveModels();
        if (modelList.isEmpty()) {
            throw new RuntimeException("No OpenRouter models available (discovery returned empty)");
        }

        log.info("OpenRouter will try {} models in order", modelList.size());

        Exception lastException = null;
        for (String currentModel : modelList) {
            log.info("Attempting OpenRouter chat with model: {}", currentModel);
            try {
                String response = executeChat(currentModel, systemPrompt, userMessage);

                // ⚠ Validate: response must contain a JSON object
                if (!looksLikeJson(response)) {
                    log.warn("Model {} returned non-JSON response (first 120 chars): {}. Trying next...",
                            currentModel,
                            response.length() > 120 ? response.substring(0, 120) + "..." : response);
                    lastException = new RuntimeException("Non-JSON response from " + currentModel);
                    continue;
                }

                this.lastSuccessfulModel = currentModel;
                log.info("✅ OpenRouter success with model: {}", currentModel);
                return response;

            } catch (Exception e) {
                log.warn("OpenRouter model failed: {}. Error: {}. Moving to next...",
                        currentModel, e.getMessage());
                lastException = e;
            }
        }

        log.error("❌ All OpenRouter free models failed!");
        throw new RuntimeException("All OpenRouter models failed. Last error: "
                + (lastException != null ? lastException.getMessage() : "Unknown"), lastException);
    }

    /**
     * A response "looks like JSON" if it contains at least one '{' and one '}'.
     * This filters out refusals ("User Safety: safe"), errors, and empty text.
     */
    private boolean looksLikeJson(String s) {
        if (s == null || s.isBlank()) return false;
        int firstBrace = s.indexOf('{');
        int lastBrace  = s.lastIndexOf('}');
        return firstBrace >= 0 && lastBrace > firstBrace;
    }

    /**
     * Model priority:
     *   1. Explicit override from properties (if non-empty)
     *   2. Dynamically-discovered free models — PREFER larger/known models first
     *
     * We deliberately do NOT use "openrouter/free" first — its auto-routing
     * picks low-quality free models that frequently return non-JSON text
     * ("User Safety: safe", etc.).
     */
    private List<String> resolveModels() {
        // 1. Explicit override
        if (modelsString != null && !modelsString.isBlank()) {
            List<String> list = new ArrayList<>();
            Arrays.stream(modelsString.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(list::add);
            return list;
        }

        // 2. Discovered free models, sorted by preference
        List<String> discovered = discovery.getFreeModels();
        List<String> ordered = new ArrayList<>(discovered);

        // Prefer strong, instruction-following models first
        List<String> preferred = List.of(
                "qwen/qwen3-235b-a22b:free",
                "qwen/qwen3-coder:free",
                "qwen/qwen3-32b:free",
                "qwen/qwen-2.5-72b-instruct:free",
                "deepseek/deepseek-r1:free",
                "deepseek/deepseek-chat-v3.1:free",
                "deepseek/deepseek-chat:free",
                "meta-llama/llama-3.3-70b-instruct:free",
                "meta-llama/llama-4-maverick:free",
                "google/gemma-3-27b-it:free",
                "mistralai/mistral-small-3.2-24b-instruct:free",
                "microsoft/phi-4-reasoning:free",
                "openai/gpt-oss-120b:free",
                "z-ai/glm-4.5-air:free",
                "minimax/minimax-m2:free"
        );

        // Move preferred ones to the front, in that order
        List<String> finalOrder = new ArrayList<>();
        for (String p : preferred) {
            if (ordered.contains(p)) {
                finalOrder.add(p);
            }
        }
        // Then add anything else that was discovered
        for (String m : ordered) {
            if (!finalOrder.contains(m)) finalOrder.add(m);
        }

        // Last resort: auto-router (only if nothing else worked)
        finalOrder.add("openrouter/free");

        return finalOrder;
    }

    private String executeChat(String targetModel, String systemPrompt, String userMessage) throws Exception {
        try {
            return doExecute(targetModel, systemPrompt, userMessage, true);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("response_format") || msg.contains("json_object")
                    || msg.contains("not supported") || msg.contains("unknown")
                    || msg.contains("invalid") || msg.contains("unavailable")) {
                log.warn("Retrying {} without response_format", targetModel);
                return doExecute(targetModel, systemPrompt, userMessage, false);
            }
            throw e;
        }
    }

    private String doExecute(String targetModel, String systemPrompt, String userMessage,
                             boolean useJsonMode) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", targetModel);
        body.put("max_tokens", maxTokens);
        body.put("temperature", temperature);

        if (useJsonMode) {
            body.putObject("response_format").put("type", "json_object");
        }

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

        return DirectMistralService.extractContent(
                response.getBody(), "OpenRouter (" + targetModel + ")");
    }
}