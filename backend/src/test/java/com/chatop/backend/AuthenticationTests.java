package com.chatop.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import com.chatop.backend.entity.User;
import tools.jackson.databind.JsonNode;

class AuthenticationTests extends ApiTestSupport {
    @Autowired
    JwtEncoder encoder;

    @Test
    void registerPersistsHashAndIssuesUsableJwtWithoutExposingPassword() throws Exception {
        String email = email();
        var response = register(email);
        assertEquals(200, response.statusCode());
        String token = token(response);
        var user = users.findByEmail(email).orElseThrow();
        assertNotEquals(PASSWORD, user.getPassword());
        assertTrue(passwords.matches(PASSWORD, user.getPassword()));
        assertEquals(user.getId().toString(), decoder.decode(token).getSubject());
        var profile = get("/api/auth/me", token);
        assertEquals(200, profile.statusCode());
        JsonNode body = json.readTree(profile.body());
        assertEquals(email, body.get("email").asString());
        assertEquals("Test User", body.get("name").asString());
        assertTrue(body.has("created_at"));
        assertTrue(body.has("updated_at"));
        assertFalse(body.has("password"));
        assertFalse(response.body().contains(PASSWORD));
    }

    @Test
    void loginNormalizesEmailAndReturnsAUsableToken() throws Exception {
        String email = email();
        assertEquals(200, register(email).statusCode());
        var response = post("/api/auth/login", Map.of("email", " " + email.toUpperCase() + " ", "password", PASSWORD));
        assertEquals(200, response.statusCode());
        assertEquals(200, get("/api/auth/me", token(response)).statusCode());
    }

    @Test
    void duplicateEmailIsRejectedWithoutCreatingAnotherUser() throws Exception {
        String email = email();
        assertEquals(200, register(email).statusCode());
        long count = users.count();
        var duplicate = register(email.toUpperCase());
        assertEquals(409, duplicate.statusCode());
        assertEquals(count, users.count());
        assertFalse(duplicate.body().contains("password"));
    }

    @Test
    void simultaneousRegistrationsCannotCreateTwoAccountsWithTheSameEmail() throws Exception {
        String email = email();
        var body = Map.of("email", email, "name", "Concurrent User", "password", PASSWORD);
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/register"))
                .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        var first = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        var second = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        var statuses = java.util.List.of(first.get().statusCode(), second.get().statusCode())
                .stream().sorted().toList();
        assertEquals(java.util.List.of(200, 409), statuses);
        assertTrue(users.findByEmail(email).isPresent());
    }

    @Test
    void wrongPasswordAndUnknownUserReturnTheSameError() throws Exception {
        String email = email();
        assertEquals(200, register(email).statusCode());
        var wrong = post("/api/auth/login", Map.of("email", email, "password", "wrong-password"));
        var unknown = post("/api/auth/login", Map.of("email", email(), "password", "wrong-password"));
        assertEquals(401, wrong.statusCode());
        assertEquals(401, unknown.statusCode());
        assertEquals(wrong.body(), unknown.body());
    }

    @Test
    void invalidAndMissingFieldsAreRejectedWithoutPersisting() throws Exception {
        long count = users.count();
        for (var input : java.util.List.of(
                Map.of("email", "invalid", "name", "Test", "password", PASSWORD),
                Map.of("email", email(), "name", " ", "password", PASSWORD),
                Map.of("email", email(), "name", "Test", "password", "short"),
                Map.of("email", email(), "name", "Test", "password", "é".repeat(40)),
                Map.<String, String>of())) {
            assertEquals(400, post("/api/auth/register", input).statusCode());
        }
        assertEquals(400, post("/api/auth/login", Map.of()).statusCode());
        assertEquals(count, users.count());
    }

    @Test
    void missingAndMockTokensCannotAccessProtectedRoutes() throws Exception {
        for (String route : java.util.List.of("/api/auth/me", "/api/rentals", "/api/rentals/1", "/api/user/1", "/api/messages")) {
            assertEquals(401, get(route, null).statusCode());
            assertEquals(401, get(route, "jwt").statusCode());
        }
    }

    @Test
    void expiredWrongIssuerAndTamperedTokensAreRejected() throws Exception {
        Instant now = Instant.now();
        String expired = signedToken("chatop", now.minusSeconds(3600), now.minusSeconds(120));
        String wrongIssuer = signedToken("another-issuer", now, now.plusSeconds(3600));
        String valid = token(register(email()));
        String[] parts = valid.split("\\.");
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"999\",\"iss\":\"chatop\"}".getBytes(StandardCharsets.UTF_8));
        String tampered = parts[0] + "." + payload + "." + parts[2];
        for (String token : java.util.List.of(expired, wrongIssuer, tampered)) {
            assertEquals(401, get("/api/auth/me", token).statusCode());
        }
    }

    @Test
    void angularAndSwaggerPreflightAllowOnlyTheConfiguredOrigins() throws Exception {
        for (String origin : java.util.List.of("http://localhost:8081", "http://127.0.0.1:8081",
                "http://localhost:4200", "http://127.0.0.1:4200", "https://untrusted.example")) {
            var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/login"))
                    .header("Origin", origin).header("Access-Control-Request-Method", "POST")
                    .header("Access-Control-Request-Headers", "content-type")
                    .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(origin.equals("https://untrusted.example") ? 403 : 200, response.statusCode());
            if (response.statusCode() == 200) {
                assertEquals(origin, response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
            } else {
                assertTrue(response.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
            }
        }
    }

    @Test
    void registerAcceptsTheAngularOriginForwardedByTheProxy() throws Exception {
        var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/register"))
                .header("Origin", "http://localhost:4200").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
                        Map.of("email", email(), "name", "Proxy User", "password", PASSWORD))))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("http://localhost:4200", response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        assertEquals(200, get("/api/auth/me", token(response)).statusCode());
    }

    @Test
    void writeRoutesCannotBeUsedWithoutAuthentication() throws Exception {
        assertEquals(401, multipart("POST", "/api/rentals", rentalFields("House", "10", "20"), png(), "image.png", "image/png", null).statusCode());
        assertEquals(401, multipart("PUT", "/api/rentals/1", rentalFields("House", "10", "20"), null, null, null, null).statusCode());
        assertEquals(401, post("/api/messages", Map.of("rental_id", "1", "user_id", "1", "message", "Hello"), null).statusCode());
    }

    private String signedToken(String issuer, Instant issued, Instant expires) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer(issuer).subject("1").issuedAt(issued).expiresAt(expires).build()))
                .getTokenValue();
    }
}
