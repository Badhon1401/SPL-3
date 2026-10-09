package com.codepulse.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Discovers OpenRouter models that are REALLY free and sorts them so the ones
 * most likely to return clean JSON are tried first:
 *   1. only ids ending in ":free" (a price of 0 alone is not enough - some paid models showed up)
 *   2. text-output models only, context window >= 8k
 *   3. non-reasoning models first (reasoning models burn tokens thinking and often return no answer)
 *   4. models that advertise response_format / structured_outputs first
 *   5. larger context window first
 */
@Service
@Slf4j
public class OpenRouterModelDiscovery {

    private static final String MODELS_URL = "https://openrouter.ai/api/v1/models";

    private final RestTemplate restTemplate = AiJsonUtil.newRestTemplate(10_000, 20_000);
    private final ObjectMapper mapper = new ObjectMapper();

    private volatile List<String> cachedFreeModels = List.of();

    private record Candidate(String id, boolean json, boolean reasoning, int ctx) {}

    /** Returns currently available free models. Cached; refreshed every 6 hours. */
    public List<String> getFreeModels() {
        if (cachedFreeModels.isEmpty()) {
            refresh();
        }
        return cachedFreeModels;
    }

    @Scheduled(fixedRate = 6 * 60 * 60 * 1000)
    public void refresh() {
        try {
            String json = restTemplate.getForObject(MODELS_URL, String.class);
            JsonNode data = mapper.readTree(json).path("data");

            List<Candidate> found = new ArrayList<>();
            for (JsonNode model : data) {
                String id = model.path("id").asText("");
                if (!id.endsWith(":free")) continue;

                String lower = id.toLowerCase();
                if (lower.contains("embed") || lower.contains("guard") || lower.contains("moderation")) continue;

                JsonNode pricing = model.path("pricing");
                if (!isZero(pricing.path("prompt")) || !isZero(pricing.path("completion"))) continue;

                JsonNode outMods = model.path("architecture").path("output_modalities");
                if (outMods.isArray() && outMods.size() > 0) {
                    boolean text = false;
                    for (JsonNode m : outMods) if ("text".equals(m.asText())) text = true;
                    if (!text) continue;
                }

                int ctx = model.path("context_length").asInt(0);
                if (ctx > 0 && ctx < 8000) continue;

                Set<String> params = new HashSet<>();
                model.path("supported_parameters").forEach(p -> params.add(p.asText()));

                boolean jsonCapable = params.contains("response_format") || params.contains("structured_outputs");
                boolean reasoning = params.contains("reasoning") || params.contains("include_reasoning");

                found.add(new Candidate(id, jsonCapable, reasoning, ctx));
            }

            found.sort(Comparator.comparing((Candidate c) -> c.reasoning())          // false first
                    .thenComparing(c -> !c.json())                                    // JSON-capable first
                    .thenComparing(Comparator.<Candidate>comparingInt(Candidate::ctx).reversed()));

            List<String> ids = found.stream().map(Candidate::id).toList();
            if (!ids.isEmpty()) {
                this.cachedFreeModels = ids;
            }
            log.info("✅ OpenRouter free models refreshed: {} usable (first: {})",
                    ids.size(), ids.stream().limit(5).toList());

        } catch (Exception e) {
            log.error("❌ OpenRouter model discovery failed: {}", e.getMessage());
        }
    }

    private static boolean isZero(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return false;
        try {
            return Double.parseDouble(n.asText()) == 0.0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}