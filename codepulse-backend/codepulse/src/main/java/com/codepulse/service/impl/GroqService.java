package com.codepulse.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Groq - fully automatic.
 *
 * You only provide the API key (app.groq.api-key). The service:
 *   1. asks Groq which chat models THIS key can use,
 *   2. tries them best-first (remembering which worked / failed),
 *   3. skips models that are retired, rate-limited or return junk.
 * No model names need to be configured.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroqService {

    private static final String PROVIDER = "groq";
    private static final List<String> FALLBACK_MODELS =
            List.of("openai/gpt-oss-20b", "llama-3.3-70b-versatile", "llama-3.1-8b-instant");

    private final ObjectMapper objectMapper;
    private final ModelHealthRegistry registry;
    private final RestTemplate http = AiJsonUtil.newRestTemplate(10_000, 45_000);

    @Value("${app.groq.api-key:}")
    private String apiKey;

    @Value("${app.groq.base-url:https://api.groq.com/openai/v1/chat/completions}")
    private String baseUrl;

    @Value("${app.groq.max-tokens:4096}")
    private int maxTokens;

    @Value("${app.groq.temperature:0.3}")
    private double temperature;

    private volatile String lastSuccessfulModel;
    private volatile List<String> availableModels = List.of();
    private volatile long availableFetchedAt = 0;

    public boolean isConfigured() {
        return !AiJsonUtil.cleanKey(apiKey).isBlank();
    }

    public String getLastSuccessfulModel() {
        return lastSuccessfulModel != null ? lastSuccessfulModel : "groq-unknown";
    }

    public String chat(String systemPrompt, String userMessage) {
        return chat(systemPrompt, userMessage, s -> s != null && s.indexOf('{') >= 0);
    }

    public String chat(String systemPrompt, String userMessage, Predicate<String> validator) {
        if (!isConfigured()) {
            throw new RuntimeException("Groq API key not configured (app.groq.api-key)");
        }

        List<String> models = registry.order(resolveModels(), PROVIDER);
        log.info("Groq will try models (best first): {}", models);

        Exception lastError = null;
        for (String model : models) {
            try {
                log.info("Attempting Groq chat with model: {}", model);
                String response = executeWithJsonFallback(model, systemPrompt, userMessage);

                if (!validator.test(response)) {
                    log.warn("Groq {} returned unusable output. First 150 chars: {}",
                            model, AiJsonUtil.truncate(response, 150));
                    registry.markFailure(PROVIDER, model, 10 * ModelHealthRegistry.MIN);
                    lastError = new RuntimeException("Unusable output from " + model);
                    continue;
                }

                this.lastSuccessfulModel = model;
                registry.markSuccess(PROVIDER, model);
                log.info("✅ Groq success with model: {}", model);
                return response;

            } catch (HttpStatusCodeException e) {
                int code = e.getStatusCode().value();
                log.warn("Groq model {} failed: HTTP {} {}", model, code,
                        AiJsonUtil.truncate(e.getResponseBodyAsString(), 200));
                lastError = e;
                if (code == 401) {
                    throw new RuntimeException("Groq rejected the API key (HTTP 401). "
                            + "Create a new key at https://console.groq.com/keys", e);
                }
                if (code == 429)      registry.markFailure(PROVIDER, model, 3 * ModelHealthRegistry.MIN);
                else if (code >= 500) registry.markFailure(PROVIDER, model, 2 * ModelHealthRegistry.MIN);
                else                  registry.markFailure(PROVIDER, model, 60 * ModelHealthRegistry.MIN);
                if (code == 404 || code == 400) availableFetchedAt = 0; // re-read the model list next time
            } catch (Exception e) {
                log.warn("Groq model {} failed: {}", model, e.getMessage());
                registry.markFailure(PROVIDER, model, 5 * ModelHealthRegistry.MIN);
                lastError = e;
            }
        }

        throw new RuntimeException("All Groq models failed. Last: "
                + (lastError != null ? lastError.getMessage() : "unknown"), lastError);
    }

    // ─── model discovery ─────────────────────────────────────────────────────

    private List<String> fetchAvailableModels() {
        long now = System.currentTimeMillis();
        if (!availableModels.isEmpty() && now - availableFetchedAt < 3_600_000L) {
            return availableModels;
        }
        try {
            HttpHeaders h = new HttpHeaders();
            h.setBearerAuth(AiJsonUtil.cleanKey(apiKey));
            String url = baseUrl.replace("/chat/completions", "/models");

            ResponseEntity<JsonNode> r = http.exchange(url, HttpMethod.GET, new HttpEntity<>(h), JsonNode.class);
            List<String> ids = new ArrayList<>();
            JsonNode data = r.getBody() == null ? null : r.getBody().path("data");
            if (data != null) {
                for (JsonNode m : data) {
                    String id = m.path("id").asText("");
                    String l = id.toLowerCase();
                    if (id.isBlank() || !m.path("active").asBoolean(true)) continue;
                    if (l.contains("whisper") || l.contains("guard") || l.contains("tts")
                            || l.contains("playai") || l.contains("orpheus") || l.contains("compound")
                            || l.contains("embed") || l.contains("safeguard")) continue;
                    ids.add(id);
                }
            }
            if (!ids.isEmpty()) {
                availableModels = ids;
                availableFetchedAt = now;
                log.info("Groq models available to this key: {}", ids);
            }
            return ids.isEmpty() ? availableModels : ids;

        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 401) {
                throw new RuntimeException("Groq rejected the API key (HTTP 401). "
                        + "Create a new key at https://console.groq.com/keys", e);
            }
            log.warn("Groq model list failed: HTTP {}", e.getStatusCode().value());
            return availableModels;
        } catch (Exception e) {
            log.warn("Groq model list failed: {}", e.getMessage());
            return availableModels;
        }
    }

    private List<String> resolveModels() {
        List<String> available = fetchAvailableModels();
        if (available.isEmpty()) return FALLBACK_MODELS;

        List<String> sorted = new ArrayList<>(available);
        sorted.sort(Comparator.comparingInt(GroqService::rank));
        return sorted.size() > 6 ? new ArrayList<>(sorted.subList(0, 6)) : sorted;
    }

    private static int rank(String id) {
        String l = id.toLowerCase();
        if (l.contains("gpt-oss-120b")) return 0;
        if (l.contains("70b"))          return 1;
        if (l.contains("gpt-oss-20b"))  return 2;
        if (l.contains("llama-4"))      return 3;
        if (l.contains("qwen"))         return 4;
        if (l.contains("kimi"))         return 5;
        if (l.contains("8b"))           return 6;
        return 9;
    }

    // ─── HTTP ────────────────────────────────────────────────────────────────

    /** Tries JSON mode first; if the model rejects response_format (HTTP 400), retries in plain mode. */
    private String executeWithJsonFallback(String model, String systemPrompt, String userMessage) throws Exception {
        try {
            return execute(model, systemPrompt, userMessage, true);
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 400) {
                log.warn("Groq {} rejected JSON mode, retrying in plain mode", model);
                return execute(model, systemPrompt, userMessage, false);
            }
            throw e;
        }
    }

    private String execute(String model, String systemPrompt, String userMessage, boolean jsonMode) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", Math.max(maxTokens, 4096));
        body.put("temperature", temperature);
        if (jsonMode) body.putObject("response_format").put("type", "json_object");

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
        headers.setBearerAuth(AiJsonUtil.cleanKey(apiKey));

        HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        ResponseEntity<JsonNode> response = http.postForEntity(baseUrl, entity, JsonNode.class);

        return AiJsonUtil.extractContent(response.getBody(), "Groq (" + model + ")");
    }
}