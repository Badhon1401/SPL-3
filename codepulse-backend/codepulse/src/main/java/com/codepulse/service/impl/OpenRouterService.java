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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenRouterService {

    private final ObjectMapper objectMapper;
    private final OpenRouterModelDiscovery discovery;
    private final ModelHealthRegistry registry;

    /** Free models can be slow; 45s per model, and an overall budget below. */
    private final RestTemplate http = AiJsonUtil.newRestTemplate(10_000, 45_000);

    @Value("${app.openrouter.api-key:}")
    private String apiKey;

    @Value("${app.openrouter.base-url:https://openrouter.ai/api/v1/chat/completions}")
    private String baseUrl;

    /** Optional override list. If empty -> dynamic discovery of free models. */
    @Value("${app.openrouter.models:}")
    private String modelsString;

    @Value("${app.openrouter.max-tokens:4096}")
    private int maxTokens;

    @Value("${app.openrouter.temperature:0.3}")
    private double temperature;

    /** Max models to try per request (stops the 20-model / 3-minute crawl). */
    @Value("${app.openrouter.max-attempts:10}")
    private int maxAttempts;

    /** Overall time budget for the whole OpenRouter stage. */
    @Value("${app.openrouter.total-timeout-seconds:90}")
    private int totalTimeoutSeconds;

    private volatile String lastSuccessfulModel;

    public boolean isConfigured() {
        return !AiJsonUtil.cleanKey(apiKey).isBlank();
    }

    public String getLastSuccessfulModel() {
        return lastSuccessfulModel != null ? lastSuccessfulModel : "unknown-fallback";
    }

    public String chat(String systemPrompt, String userMessage) {
        return chat(systemPrompt, userMessage, s -> s != null && s.indexOf('{') >= 0);
    }

    public String chat(String systemPrompt, String userMessage, Predicate<String> validator) {
        if (!isConfigured()) {
            throw new RuntimeException("OpenRouter API key not configured (app.openrouter.api-key)");
        }

        List<String> modelList = registry.order(resolveModels(), "openrouter");
        if (modelList.isEmpty()) {
            throw new RuntimeException("No OpenRouter models available (discovery returned empty)");
        }

        log.info("OpenRouter: {} candidate models, will try at most {} within {}s",
                modelList.size(), maxAttempts, totalTimeoutSeconds);

        long deadline = System.currentTimeMillis() + totalTimeoutSeconds * 1000L;
        int attempts = 0;
        Exception lastException = null;

        for (String currentModel : modelList) {
            if (attempts >= maxAttempts) {
                log.warn("OpenRouter: reached max attempts ({})", maxAttempts);
                break;
            }
            if (System.currentTimeMillis() > deadline) {
                log.warn("OpenRouter: total time budget of {}s used up", totalTimeoutSeconds);
                break;
            }
            attempts++;
            log.info("Attempting OpenRouter chat with model: {} ({}/{})", currentModel, attempts, maxAttempts);

            try {
                String response = executeChat(currentModel, systemPrompt, userMessage);

                if (!validator.test(response)) {
                    log.warn("Model {} returned unusable output (first 120 chars): {}. Trying next...",
                            currentModel, AiJsonUtil.truncate(response, 120));
                    registry.markFailure("openrouter", currentModel, 30 * ModelHealthRegistry.MIN);
                    lastException = new RuntimeException("Unusable output from " + currentModel);
                    continue;
                }

                this.lastSuccessfulModel = currentModel;
                registry.markSuccess("openrouter", currentModel);
                log.info("✅ OpenRouter success with model: {}", currentModel);
                return response;

            } catch (HttpStatusCodeException e) {
                int code = e.getStatusCode().value();
                log.warn("OpenRouter model {} failed: HTTP {} {}", currentModel, code,
                        AiJsonUtil.truncate(e.getResponseBodyAsString(), 160));
                lastException = e;
                registry.markFailure("openrouter", currentModel,
                        code == 429 ? 3 * ModelHealthRegistry.MIN : 60 * ModelHealthRegistry.MIN);
                if (code == 401) {
                    // Wrong key: every other model would fail the same way.
                    throw new RuntimeException("OpenRouter rejected the API key (HTTP 401)", e);
                }
                // 402 (needs credits), 403 (agentic-only), 404, 429 (rate limit), 5xx -> just try the next model
            } catch (Exception e) {
                log.warn("OpenRouter model {} failed: {}", currentModel, e.getMessage());
                registry.markFailure("openrouter", currentModel, 30 * ModelHealthRegistry.MIN);
                lastException = e;
            }
        }

        throw new RuntimeException("All OpenRouter attempts failed. Last error: "
                + (lastException != null ? lastException.getMessage() : "Unknown"), lastException);
    }

    /**
     * Model priority:
     *   1. Explicit override from properties (if non-empty)
     *   2. Known-good models (if currently available), then everything else discovered
     *      (discovery already sorts: non-reasoning, JSON-capable, large context first)
     *   3. openrouter/free auto-router as the very last resort
     */
    private List<String> resolveModels() {
        if (modelsString != null && !modelsString.isBlank()) {
            List<String> list = new ArrayList<>();
            Arrays.stream(modelsString.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(list::add);
            return list;
        }

        List<String> discovered = discovery.getFreeModels();

        List<String> preferred = List.of(
                "qwen/qwen3-coder:free",
                "meta-llama/llama-3.3-70b-instruct:free",
                "openai/gpt-oss-120b:free",
                "openai/gpt-oss-20b:free",
                "google/gemma-3-27b-it:free",
                "mistralai/mistral-small-3.2-24b-instruct:free",
                "deepseek/deepseek-chat-v3.1:free",
                "cohere/north-mini-code:free"
        );

        List<String> finalOrder = new ArrayList<>();
        for (String p : preferred) {
            if (discovered.contains(p)) finalOrder.add(p);
        }
        for (String m : discovered) {
            if (!finalOrder.contains(m)) finalOrder.add(m);
        }
        finalOrder.add("openrouter/free");
        return finalOrder;
    }

    private String executeChat(String targetModel, String systemPrompt, String userMessage) throws Exception {
        try {
            return doExecute(targetModel, systemPrompt, userMessage, true);
        } catch (HttpStatusCodeException e) {
            int code = e.getStatusCode().value();
            // 400 / 422 usually means "this model does not support response_format" -> retry in plain mode
            if (code == 400 || code == 422) {
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
        body.put("max_tokens", Math.max(maxTokens, 4096));
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
        headers.setBearerAuth(AiJsonUtil.cleanKey(apiKey));
        headers.set("HTTP-Referer", "https://codepulse.app");
        headers.set("X-Title", "CodePulse AI Coach");

        HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        ResponseEntity<JsonNode> response = http.postForEntity(baseUrl, entity, JsonNode.class);

        return AiJsonUtil.extractContent(response.getBody(), "OpenRouter (" + targetModel + ")");
    }
}