package com.codepulse.controller;

import com.codepulse.dto.response.ApiResponse;
import com.codepulse.service.impl.DirectMistralService;
import com.codepulse.service.impl.GroqService;
import com.codepulse.service.impl.OpenRouterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Quick self-test for the AI providers (never returns your API keys).
 *
 *   GET /api/ai/health/groq
 *   GET /api/ai/health/mistral
 *   GET /api/ai/health/openrouter
 *
 * Needs the normal "Authorization: Bearer <token>" header.
 * Delete this file before final deployment if you do not want it exposed.
 */
@RestController
@RequestMapping("/api/ai/health")
@RequiredArgsConstructor
public class AiDiagnosticsController {

    private final GroqService groqService;
    private final DirectMistralService mistralService;
    private final OpenRouterService openRouterService;

    private static final String SYS = "Reply with ONLY this JSON object and nothing else: {\"ok\":true}";
    private static final String USER = "ping";
    private static final Predicate<String> OK = s -> s != null && s.contains("\"ok\"");

    @GetMapping("/{provider}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> check(@PathVariable String provider) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("provider", provider);
        long t0 = System.currentTimeMillis();

        try {
            switch (provider.toLowerCase()) {
                case "groq" -> {
                    out.put("configured", groqService.isConfigured());
                    if (groqService.isConfigured()) {
                        String r = groqService.chat(SYS, USER, OK);
                        out.put("model", groqService.getLastSuccessfulModel());
                        out.put("reply", r);
                        out.put("status", "OK");
                    } else out.put("status", "NOT_CONFIGURED");
                }
                case "mistral" -> {
                    out.put("configured", mistralService.isConfigured());
                    if (mistralService.isConfigured()) {
                        String r = mistralService.chat(SYS, USER, OK);
                        out.put("model", mistralService.getModel());
                        out.put("reply", r);
                        out.put("status", "OK");
                    } else out.put("status", "NOT_CONFIGURED");
                }
                case "openrouter" -> {
                    out.put("configured", openRouterService.isConfigured());
                    if (openRouterService.isConfigured()) {
                        String r = openRouterService.chat(SYS, USER, OK);
                        out.put("model", openRouterService.getLastSuccessfulModel());
                        out.put("reply", r);
                        out.put("status", "OK");
                    } else out.put("status", "NOT_CONFIGURED");
                }
                default -> out.put("status", "UNKNOWN_PROVIDER (use groq, mistral or openrouter)");
            }
        } catch (Exception e) {
            out.put("status", "FAILED");
            out.put("error", e.getMessage());
        }

        out.put("millis", System.currentTimeMillis() - t0);
        return ResponseEntity.ok(ApiResponse.success(out));
    }
}