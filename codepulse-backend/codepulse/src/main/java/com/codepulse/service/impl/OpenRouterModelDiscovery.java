package com.codepulse.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class OpenRouterModelDiscovery {

    private static final String MODELS_URL = "https://openrouter.ai/api/v1/models";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    private volatile List<String> cachedFreeModels = new ArrayList<>();

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
            JsonNode root = mapper.readTree(json);
            JsonNode data = root.get("data");

            List<String> free = new ArrayList<>();
            for (JsonNode model : data) {
                String id = model.get("id").asText();
                JsonNode pricing = model.get("pricing");
                if (pricing != null
                        && "0".equals(pricing.get("prompt").asText())
                        && "0".equals(pricing.get("completion").asText())) {
                    free.add(id);
                }
            }

            this.cachedFreeModels = free;
            log.info("✅ OpenRouter free models refreshed: {}", free.size());

        } catch (Exception e) {
            log.error("❌ OpenRouter model discovery failed: {}", e.getMessage());
        }
    }
}