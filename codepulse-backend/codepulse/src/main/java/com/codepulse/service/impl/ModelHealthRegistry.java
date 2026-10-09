package com.codepulse.service.impl;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory "memory" of which AI models / providers work.
 *
 *  - A model that fails (404, 403, 429, empty answer, bad JSON...) is put on a cooldown,
 *    so the next request skips it instead of wasting 30-60 seconds on it again.
 *  - The model / provider that last succeeded is tried FIRST next time.
 *  - Nothing is ever banned for good: when everything is cooling down, all models are tried anyway.
 */
@Component
public class ModelHealthRegistry {

    public static final long MIN = 60_000L;

    private final Map<String, Long> cooldownUntil = new ConcurrentHashMap<>();
    private final Map<String, String> lastGoodModel = new ConcurrentHashMap<>();
    private volatile String lastGoodProvider;

    private static String key(String provider, String model) {
        return provider + "|" + model;
    }

    // ── models ───────────────────────────────────────────────────────────────

    public boolean isCoolingDown(String provider, String model) {
        Long until = cooldownUntil.get(key(provider, model));
        return until != null && until > System.currentTimeMillis();
    }

    public void markFailure(String provider, String model, long cooldownMs) {
        cooldownUntil.put(key(provider, model), System.currentTimeMillis() + cooldownMs);
    }

    public void markSuccess(String provider, String model) {
        cooldownUntil.remove(key(provider, model));
        lastGoodModel.put(provider, model);
        lastGoodProvider = provider;
    }

    /** Healthy models first (last good one at the very front). If all are cooling down, returns them all. */
    public List<String> order(List<String> models, String provider) {
        List<String> ready = new ArrayList<>();
        for (String m : models) {
            if (!isCoolingDown(provider, m)) ready.add(m);
        }
        if (ready.isEmpty()) ready = new ArrayList<>(models);

        String good = lastGoodModel.get(provider);
        if (good != null && ready.remove(good)) {
            ready.add(0, good);
        }
        return ready;
    }

    // ── providers ────────────────────────────────────────────────────────────

    public boolean isProviderCoolingDown(String provider) {
        return isCoolingDown(provider, "*");
    }

    public void markProviderFailure(String provider, long cooldownMs) {
        markFailure(provider, "*", cooldownMs);
    }

    public void markProviderSuccess(String provider) {
        cooldownUntil.remove(key(provider, "*"));
        lastGoodProvider = provider;
    }

    public String lastGoodProvider() {
        return lastGoodProvider;
    }
}