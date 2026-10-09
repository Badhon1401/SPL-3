package com.codepulse.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Shared helpers for every LLM provider (Groq / Mistral / OpenRouter).
 *
 * Key idea: a provider response only counts as a SUCCESS when it contains a
 * JSON object with a non-empty "recommendations" array. Anything else
 * (reasoning text, refusals, truncated JSON) is a failure and the next
 * model / provider is tried.
 */
public final class AiJsonUtil {

    private AiJsonUtil() {}

    private static final Pattern THINK_BLOCK =
            Pattern.compile("(?is)<think(?:ing)?>.*?</think(?:ing)?>");

    /** RestTemplate with real timeouts so one slow model can't hang the request. */
    public static RestTemplate newRestTemplate(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(connectTimeoutMs);
        f.setReadTimeout(readTimeoutMs);
        return new RestTemplate(f);
    }

    /** Removes stray spaces / quotes that commonly sneak into API keys in .properties files. */
    public static String cleanKey(String key) {
        if (key == null) return "";
        String k = key.trim();
        if (k.length() >= 2
                && ((k.startsWith("\"") && k.endsWith("\"")) || (k.startsWith("'") && k.endsWith("'")))) {
            k = k.substring(1, k.length() - 1).trim();
        }
        return k;
    }

    public static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    /** Strips <think> blocks and markdown code fences. */
    public static String stripNoise(String raw) {
        if (raw == null) return "";
        String s = THINK_BLOCK.matcher(raw).replaceAll("");
        s = s.replace("```json", "").replace("```JSON", "").replace("```", "");
        return s.trim();
    }

    /**
     * Finds the first balanced {...} block in the text that parses as JSON AND
     * contains a non-empty "recommendations" array with at least one titled item.
     */
    public static Optional<String> extractRecommendationJson(ObjectMapper mapper, String raw) {
        String s = stripNoise(raw);
        int from = 0;
        while (from < s.length()) {
            int start = s.indexOf('{', from);
            if (start < 0) break;
            int end = findMatchingBrace(s, start);
            if (end > start) {
                String candidate = s.substring(start, end + 1);
                if (isValidRecommendationPayload(mapper, candidate)) {
                    return Optional.of(candidate);
                }
            }
            from = start + 1;
        }
        return Optional.empty();
    }

    public static boolean isValidRecommendationPayload(ObjectMapper mapper, String json) {
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode recs = root.path("recommendations");
            if (!recs.isArray() || recs.size() == 0) return false;
            for (JsonNode r : recs) {
                if (!r.path("title").asText("").isBlank()) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /** Brace matcher that ignores braces inside JSON strings. Returns -1 if unbalanced (truncated). */
    private static int findMatchingBrace(String s, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
            } else {
                if (c == '"') inString = true;
                else if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    /**
     * Reads choices[0].message.content from an OpenAI-compatible response.
     * IMPORTANT: it does NOT fall back to "reasoning" - reasoning text is thinking,
     * not the answer, and was the cause of the "No valid JSON object" failures.
     */
    public static String extractContent(JsonNode root, String provider) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            throw new RuntimeException(provider + " returned null/empty body");
        }
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.size() == 0) {
            throw new RuntimeException(provider + " returned no choices: " + truncate(root.toString(), 300));
        }
        JsonNode first = choices.get(0);
        JsonNode c = first.path("message").path("content");

        String content = null;
        if (c.isTextual()) {
            content = c.asText();
        } else if (c.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : c) {
                if (part.isTextual()) sb.append(part.asText());
                else if (part.has("text")) sb.append(part.path("text").asText());
            }
            content = sb.toString();
        }

        if (content == null || content.isBlank()) {
            String finish = first.path("finish_reason").asText("");
            throw new RuntimeException(provider + " returned empty content (finish_reason=" + finish
                    + "; probably a reasoning model that used all tokens thinking)");
        }
        return content.trim();
    }
}