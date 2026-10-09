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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Mistral - fully automatic. Only the API key is required.
 * Discovers the chat models available to the key and tries them best-first.
 * Stops early when the account itself is rate-limited (two models in a row return 429).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DirectMistralService {

    private static final String PROVIDER = "mistral";
    private static final List<String> FALLBACK_MODELS = List.of("mistral-small-latest", "open-mistral-nemo");

    private final ObjectMapper objectMapper;
    private final ModelHealthRegistry registry;
    private final RestTemplate http = AiJsonUtil.newRestTemplate(10_000, 60_000);

    @Value("${mistral.api-key:${spring.ai.mistralai.api-key:}}")
    private String apiKey;

    @Value("${mistral.base-url:https://api.mistral.ai}")
    private String baseUrl;

    /** Optional preferred model (tried first if the key can use it). */
    @Value("${mistral.model:mistral-small-latest}")
    private String preferredModel;

    @Value("${mistral.max-tokens:4096}")
    private int maxTokens;

    @Value("${mistral.temperature:0.3}")
    private double temperature;

    private volatile String lastSuccessfulModel;
    private volatile List<String> availableModels = List.of();
    private volatile long availableFetchedAt = 0;

    public boolean isConfigured() {
        return !AiJsonUtil.cleanKey(apiKey).isBlank();
    }

    public String getModel() {
        return lastSuccessfulModel != null ? lastSuccessfulModel : preferredModel;
    }

    public String chat(String systemPrompt, String userMessage) {
        return chat(systemPrompt, userMessage, s -> s != null && s.indexOf('{') >= 0);
    }

    public String chat(String systemPrompt, String userMessage, Predicate<String> validator) {
        if (!isConfigured()) {
            throw new RuntimeException("Mistral API key not configured (mistral.api-key)");
        }

        List<String> models = registry.order(resolveModels(), PROVIDER);
        log.info("Mistral will try models (best first): {}", models);

        Exception lastError = null;
        int rateLimited = 0;

        for (String model : models) {
            try {
                log.info("Attempting Mistral chat with model: {}", model);
                String content = callWithOneRetry(model, systemPrompt, userMessage);

                if (!validator.test(content)) {
                    log.warn("Mistral {} returned unusable output. First 150 chars: {}",
                            model, AiJsonUtil.truncate(content, 150));
                    registry.markFailure(PROVIDER, model, 10 * ModelHealthRegistry.MIN);
                    lastError = new RuntimeException("Unusable output from " + model);
                    continue;
                }

                lastSuccessfulModel = model;
                registry.markSuccess(PROVIDER, model);
                log.info("✅ Mistral success with model: {}", model);
                return content;

            } catch (HttpStatusCodeException e) {
                int code = e.getStatusCode().value();
                log.warn("Mistral model {} failed: HTTP {} {}", model, code,
                        AiJsonUtil.truncate(e.getResponseBodyAsString(), 200));
                lastError = e;
                if (code == 401) {
                    throw new RuntimeException("Mistral rejected the API key (HTTP 401)", e);
                }
                if (code == 429) {
                    registry.markFailure(PROVIDER, model, 3 * ModelHealthRegistry.MIN);
                    if (++rateLimited >= 2) {
                        log.warn("Mistral: account looks rate-limited, giving up for now");
                        break;
                    }
                } else if (code >= 500) {
                    registry.markFailure(PROVIDER, model, 2 * ModelHealthRegistry.MIN);
                } else {
                    registry.markFailure(PROVIDER, model, 60 * ModelHealthRegistry.MIN);
                    availableFetchedAt = 0;
                }
            } catch (Exception e) {
                log.warn("Mistral model {} failed: {}", model, e.getMessage());
                registry.markFailure(PROVIDER, model, 5 * ModelHealthRegistry.MIN);
                lastError = e;
            }
        }

        throw new RuntimeException("All Mistral attempts failed. Last: "
                + (lastError != null ? lastError.getMessage() : "unknown"), lastError);
    }

    // ─── discovery ───────────────────────────────────────────────────────────

    private List<String> fetchAvailableModels() {
        long now = System.currentTimeMillis();
        if (!availableModels.isEmpty() && now - availableFetchedAt < 3_600_000L) {
            return availableModels;
        }
        try {
            HttpHeaders h = new HttpHeaders();
            h.setBearerAuth(AiJsonUtil.cleanKey(apiKey));
            ResponseEntity<JsonNode> r = http.exchange(base() + "/v1/models", HttpMethod.GET,
                    new HttpEntity<>(h), JsonNode.class);

            Set<String> ids = new LinkedHashSet<>();
            JsonNode data = r.getBody() == null ? null : r.getBody().path("data");
            if (data != null) {
                for (JsonNode m : data) {
                    String id = m.path("id").asText("");
                    String l = id.toLowerCase();
                    if (id.isBlank()) continue;
                    if (l.contains("embed") || l.contains("ocr") || l.contains("moderation")
                            || l.contains("voxtral") || l.contains("transcri") || l.contains("pixtral")) continue;
                    JsonNode caps = m.path("capabilities");
                    if (caps.has("completion_chat") && !caps.path("completion_chat").asBoolean(true)) continue;
                    ids.add(id);
                }
            }
            if (!ids.isEmpty()) {
                availableModels = new ArrayList<>(ids);
                availableFetchedAt = now;
                log.info("Mistral models available to this key: {}", ids);
            }
            return availableModels;

        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 401) {
                throw new RuntimeException("Mistral rejected the API key (HTTP 401)", e);
            }
            log.warn("Mistral model list failed: HTTP {}", e.getStatusCode().value());
            return availableModels;
        } catch (Exception e) {
            log.warn("Mistral model list failed: {}", e.getMessage());
            return availableModels;
        }
    }

    private List<String> resolveModels() {
        List<String> available = fetchAvailableModels();
        if (available.isEmpty()) return FALLBACK_MODELS;

        List<String> sorted = new ArrayList<>(available);
        sorted.sort(Comparator.comparingInt(this::rank));
        return sorted.size() > 5 ? new ArrayList<>(sorted.subList(0, 5)) : sorted;
    }

    private int rank(String id) {
        String l = id.toLowerCase();
        if (id.equals(preferredModel))       return 0;
        if (l.contains("mistral-small"))     return 1;
        if (l.contains("nemo"))              return 2;
        if (l.contains("ministral"))         return 3;
        if (l.contains("mistral-medium"))    return 4;
        if (l.contains("mistral-large"))     return 5;
        return 9;
    }

    // ─── HTTP ────────────────────────────────────────────────────────────────

    /** One call, plus one short retry on 429 / 5xx. */
    private String callWithOneRetry(String model, String systemPrompt, String userMessage) {
        HttpEntity<String> entity = buildEntity(model, systemPrompt, userMessage);
        try {
            return post(entity);
        } catch (HttpStatusCodeException e) {
            int code = e.getStatusCode().value();
            if (code == 429 || code >= 500) {
                sleep(1500);
                return post(entity);
            }
            throw e;
        }
    }

    private String post(HttpEntity<String> entity) {
        ResponseEntity<JsonNode> response = http.postForEntity(base() + "/v1/chat/completions", entity, JsonNode.class);
        return AiJsonUtil.extractContent(response.getBody(), "Mistral");
    }

    private HttpEntity<String> buildEntity(String model, String systemPrompt, String userMessage) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", Math.max(maxTokens, 4096));
        body.put("temperature", temperature);
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
        headers.setBearerAuth(AiJsonUtil.cleanKey(apiKey));

        try {
            return new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        } catch (Exception e) {
            throw new RuntimeException("Could not build Mistral request: " + e.getMessage(), e);
        }
    }

    private String base() {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}