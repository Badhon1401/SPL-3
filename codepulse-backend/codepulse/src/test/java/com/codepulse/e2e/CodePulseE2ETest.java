package com.codepulse.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.web.client.ResourceAccessException;

import java.net.HttpRetryException;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CodePulse end-to-end API acceptance tests (TC-01 ... TC-17).
 *
 * - Every test is independent: it creates / reuses its own user, so you can run ONE test or ALL.
 * - Tests tagged "ai"   call the real AI providers (need a Groq / Mistral / OpenRouter key).
 * - Tests tagged "sync" call the real Codeforces API.
 *
 * Run one:   mvn -Dtest=CodePulseE2ETest#tc03_login_success test
 * Run all:   mvn -Dtest=CodePulseE2ETest test
 * Skip slow: mvn -Dtest=CodePulseE2ETest -DexcludedGroups=ai,sync test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.MethodName.class)
@ExtendWith(CodePulseE2ETest.ResultPrinter.class)
class CodePulseE2ETest {

    /** Prints one easy-to-copy line per test:  RESULT|test name|PASSED/FAILED/SKIPPED|reason */
    static class ResultPrinter implements TestWatcher {
        public void testSuccessful(ExtensionContext c) { System.out.println("RESULT|" + c.getDisplayName() + "|PASSED|"); }
        public void testFailed(ExtensionContext c, Throwable t) {
            String m = String.valueOf(t.getMessage()).replaceAll("\\s+", " ");
            System.out.println("RESULT|" + c.getDisplayName() + "|FAILED|" + (m.length() > 160 ? m.substring(0, 160) : m)); }
        public void testAborted(ExtensionContext c, Throwable t) { System.out.println("RESULT|" + c.getDisplayName() + "|SKIPPED|"); }
        public void testDisabled(ExtensionContext c, Optional<String> r) { System.out.println("RESULT|" + c.getDisplayName() + "|DISABLED|"); }
    }

    @Autowired private TestRestTemplate rest;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String PASSWORD = "S3cret!pass";
    private static String sharedEmail;
    private static String sharedToken;

    // ───────────────────────── helpers ─────────────────────────

    private record Res(int status, JsonNode json, String raw) {}

    private Res call(HttpMethod method, String path, Object body, String token) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) h.setBearerAuth(token);
        try {
            ResponseEntity<String> r = rest.exchange(path, method, new HttpEntity<>(body, h), String.class);
            JsonNode json = null;
            try { if (r.getBody() != null && !r.getBody().isBlank()) json = mapper.readTree(r.getBody()); }
            catch (Exception ignored) { }
            // Some controllers wrap payloads in ApiResponse<T> {success, message, data, timestamp}: unwrap it.
            if (json != null && json.has("success") && json.has("data") && !json.get("data").isNull()) json = json.get("data");
            return new Res(r.getStatusCode().value(), json, r.getBody());
        } catch (ResourceAccessException e) {
            // JDK HttpURLConnection cannot retry a 401 with WWW-Authenticate in streaming mode,
            // so it throws HttpRetryException instead of surfacing the 401 the server sent.
            Throwable cause = e.getCause();
            String msg = String.valueOf(e.getMessage());
            if (cause instanceof HttpRetryException
                    || msg.contains("cannot retry due to server authentication")) {
                return new Res(401, null, msg);
            }
            throw e;
        }
    }

    private Res register(String email, String username) {
        return call(HttpMethod.POST, "/api/auth/register",
                Map.of("username", username, "email", email, "password", PASSWORD, "fullName", "E2E Tester"), null);
    }

    private static String unique(String prefix) { return prefix + System.nanoTime(); }

    /** Registers one shared user the first time it is needed and returns its JWT. */
    private synchronized String token() {
        if (sharedToken == null) {
            String u = unique("e2e_");
            sharedEmail = u + "@example.com";
            Res r = register(sharedEmail, u);
            assertTrue(r.status() == 200 || r.status() == 201, "register failed: " + r.raw());
            sharedToken = r.json().path("token").asText();
            assertFalse(sharedToken.isBlank(), "no token in register response");
        }
        return sharedToken;
    }

    private static boolean is2xx(int s) { return s >= 200 && s < 300; }
    private static boolean denied(int s) { return s == 401 || s == 403; }

    // ───────────────────────── TC-01 … TC-05 : authentication ─────────────────────────

    @Test @DisplayName("TC-01 register returns a token")
    void tc01_register_returns_token() {
        String u = unique("e2e_reg_");
        Res r = register(u + "@example.com", u);
        assertTrue(r.status() == 200 || r.status() == 201, r.raw());
        assertFalse(r.json().path("token").asText().isBlank());
    }

    @Test @DisplayName("TC-02 register with duplicate email is rejected")
    void tc02_register_duplicate_is_rejected() {
        String u = unique("e2e_dup_");
        register(u + "@example.com", u);
        Res again = register(u + "@example.com", u);
        assertTrue(again.status() >= 400 && again.status() < 500, "expected 4xx, got " + again.status());
    }

    @Test @DisplayName("TC-03 login with correct password succeeds")
    void tc03_login_success() {
        token();
        Res r = call(HttpMethod.POST, "/api/auth/login", Map.of("email", sharedEmail, "password", PASSWORD), null);
        assertEquals(200, r.status(), r.raw());
        assertFalse(r.json().path("token").asText().isBlank());
    }

    @Test @DisplayName("TC-04 login with wrong password is rejected")
    void tc04_login_wrong_password_is_rejected() {
        token();
        Res r = call(HttpMethod.POST, "/api/auth/login", Map.of("email", sharedEmail, "password", "wrong-password"), null);
        assertTrue(r.status() >= 400 && r.status() < 500, "expected 4xx, got " + r.status());
    }

    @Test @DisplayName("TC-05 protected endpoint without token is denied")
    void tc05_protected_endpoint_without_token_is_denied() {
        Res r = call(HttpMethod.GET, "/api/analytics/me", null, null);
        assertTrue(denied(r.status()), "expected 401/403, got " + r.status());
    }

    // ───────────────────────── TC-06 … TC-07 : profile ─────────────────────────

    @Test @DisplayName("TC-06 get own profile returns the caller's data")
    void tc06_get_own_profile() {
        Res r = call(HttpMethod.GET, "/api/users/me", null, token());
        assertEquals(200, r.status(), r.raw());
        assertEquals(sharedEmail, r.json().path("email").asText());
    }

    @Test @DisplayName("TC-07 update profile handles is accepted")
    void tc07_update_profile_handles() {
        Res r = call(HttpMethod.PUT, "/api/users/me",
                Map.of("fullName", "E2E Tester", "codeforcesHandle", "tourist"), token());
        assertTrue(is2xx(r.status()), r.status() + " " + r.raw());
    }

    // ───────────────────────── TC-08 … TC-13 : analytics, submissions, recommendations, admin ─────────────────────────

    @Test @DisplayName("TC-08 analytics for a new user does not fail")
    void tc08_analytics_for_new_user_does_not_fail() {
        Res r = call(HttpMethod.GET, "/api/analytics/me", null, token());
        assertEquals(200, r.status(), r.raw());          // also guards against LazyInitializationException (500)
        assertTrue(r.json().has("totalSubmissions"));
    }

    @Test @DisplayName("TC-09 recent submissions returns a list")
    void tc09_recent_submissions_is_a_list() {
        Res r = call(HttpMethod.GET, "/api/submissions/recent?limit=10", null, token());
        assertEquals(200, r.status(), r.raw());
        assertTrue(r.json().isArray());
    }

    @Test @DisplayName("TC-10 recommendations returns a list")
    void tc10_recommendations_list() {
        Res r = call(HttpMethod.GET, "/api/recommendations", null, token());
        assertEquals(200, r.status(), r.raw());
        assertTrue(r.json().isArray());
    }

    @Test @DisplayName("TC-11 generate recommendations for a user without data does not fail")
    void tc11_generate_recommendations_for_user_without_data() {
        Res r = call(HttpMethod.POST, "/api/recommendations/generate", null, token());
        assertTrue(is2xx(r.status()), r.status() + " " + r.raw()); // must never be a 500
    }

    @Test @DisplayName("TC-12 admin endpoint is forbidden for a normal user")
    void tc12_admin_endpoint_forbidden_for_normal_user() {
        Res r = call(HttpMethod.GET, "/api/admin/users", null, token());
        assertEquals(403, r.status());
    }

    // ───────────────────────── TC-13 … TC-16 : AI Coach ─────────────────────────

    @Test @DisplayName("TC-13 AI latest session never returns 500")
    void tc13_ai_latest_session_never_500() {
        Res r = call(HttpMethod.GET, "/api/ai/sessions/latest", null, token());
        assertTrue(r.status() < 500 && !denied(r.status()), r.status() + " " + r.raw());
    }

    @Test @Tag("ai") @DisplayName("TC-14 AI health endpoint requires a token and answers")
    void tc14_ai_diagnostics_requires_token_and_answers() {
        assertTrue(denied(call(HttpMethod.GET, "/api/ai/health/groq", null, null).status()));
        Res r = call(HttpMethod.GET, "/api/ai/health/groq", null, token());
        assertTrue(is2xx(r.status()), r.status() + " " + r.raw());
    }

    @Test @Tag("ai") @DisplayName("TC-15 AI recommend returns a session with insight and recommendations")
    void tc15_ai_recommend_returns_a_session() {
        Res r = call(HttpMethod.POST, "/api/ai/recommend",
                Map.of("prompt", "Give me easy dynamic programming problems", "count", 4), token());
        assertEquals(200, r.status(), r.raw());            // graceful response even if all providers fail
        assertNotNull(r.json());
        assertTrue(r.json().has("coachInsight") && r.json().has("recommendations"));
    }

    @Test @Tag("ai") @DisplayName("TC-16 AI recommend count is capped at 10")
    void tc16_ai_recommend_count_is_limited_to_10() {
        Res r = call(HttpMethod.POST, "/api/ai/recommend",
                Map.of("prompt", "Mixed practice problems", "count", 50), token());
        assertEquals(200, r.status(), r.raw());
        JsonNode list = r.json().path("recommendations");
        assertTrue(list.size() <= 10, "got " + list.size() + " items");
    }

    // ───────────────────────── TC-17 : synchronisation ─────────────────────────

    @Test @Tag("sync") @DisplayName("TC-17 analytics sync returns immediately")
    void tc17_sync_returns_immediately() {
        call(HttpMethod.PUT, "/api/users/me", Map.of("fullName", "E2E Tester", "codeforcesHandle", "tourist"), token());
        Res r = call(HttpMethod.POST, "/api/analytics/sync", null, token());
        assertTrue(is2xx(r.status()), r.status() + " " + r.raw());
    }
}